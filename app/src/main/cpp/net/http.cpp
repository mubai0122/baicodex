#include "http.h"

#include <arpa/inet.h>
#include <dirent.h>
#include <fcntl.h>
#include <netdb.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <poll.h>
#include <sys/socket.h>
#include <unistd.h>

#include <cctype>
#include <cerrno>
#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <mutex>
#include <string>

#include <openssl/err.h>
#include <openssl/pem.h>
#include <openssl/ssl.h>
#include <openssl/x509.h>

namespace net {
namespace {

constexpr size_t kMaxBody = 4u * 1024 * 1024; // 4MB 上限：图标再大也不该走这条路

struct Url {
    bool tls = false;
    std::string host;
    std::string port;
    std::string path;
};

std::string lower(std::string s) {
    for (auto& c : s) c = static_cast<char>(tolower(static_cast<unsigned char>(c)));
    return s;
}

bool parseUrl(const std::string& in, Url& u) {
    auto sp = in.find("://");
    if (sp == std::string::npos) return false;
    auto scheme = lower(in.substr(0, sp));
    u.tls = scheme == "https";
    if (!u.tls && scheme != "http") return false;
    std::string rest = in.substr(sp + 3);
    auto slash = rest.find('/');
    std::string hostport = slash == std::string::npos ? rest : rest.substr(0, slash);
    u.path = slash == std::string::npos ? "/" : rest.substr(slash);
    auto colon = hostport.rfind(':');
    if (colon != std::string::npos && hostport.find(']') == std::string::npos) {
        u.host = hostport.substr(0, colon);
        u.port = hostport.substr(colon + 1);
    } else {
        u.host = hostport;
        u.port = u.tls ? "443" : "80";
    }
    return !u.host.empty();
} //拆出 scheme/host/port/path

int connectTo(const std::string& host, const std::string& port, int timeoutMs) {
    addrinfo hints{};
    hints.ai_family = AF_UNSPEC;
    hints.ai_socktype = SOCK_STREAM;
    addrinfo* res = nullptr;
    if (getaddrinfo(host.c_str(), port.c_str(), &hints, &res) != 0 || res == nullptr) return -1;
    int fd = -1;
    for (addrinfo* p = res; p != nullptr; p = p->ai_next) {
        int s = ::socket(p->ai_family, p->ai_socktype, p->ai_protocol);
        if (s < 0) continue;
        int fl = fcntl(s, F_GETFL, 0);
        fcntl(s, F_SETFL, fl | O_NONBLOCK);
        int rc = ::connect(s, p->ai_addr, p->ai_addrlen);
        if (rc != 0 && errno != EINPROGRESS) {
            ::close(s);
            continue;
        }
        if (rc != 0) {
            pollfd pw{s, POLLOUT, 0};
            if (::poll(&pw, 1, timeoutMs) <= 0) {
                ::close(s);
                continue;
            }
            int soErr = 0;
            socklen_t len = sizeof(soErr);
            if (getsockopt(s, SOL_SOCKET, SO_ERROR, &soErr, &len) != 0 || soErr != 0) {
                ::close(s);
                continue;
            }
        }
        fcntl(s, F_SETFL, fl);
        int one = 1;
        setsockopt(s, IPPROTO_TCP, TCP_NODELAY, &one, sizeof(one));
        fd = s;
        break;
    }
    freeaddrinfo(res);
    return fd;
} //带超时的 TCP 连接

/** 逐文件灌 CA：hash 目录模式在静态 OpenSSL3 里不生效（返回成功但库里 0 张），只能自己读 PEM */
int loadCaDir(SSL_CTX* ctx, const char* dir) {
    DIR* d = opendir(dir);
    if (d == nullptr) return 0;
    X509_STORE* st = SSL_CTX_get_cert_store(ctx);
    int n = 0;
    while (dirent* e = readdir(d)) {
        if (e->d_name[0] == '.') continue;
        std::string p = std::string(dir) + "/" + e->d_name;
        FILE* f = fopen(p.c_str(), "r");
        if (f == nullptr) continue;
        X509* x = PEM_read_X509(f, nullptr, nullptr, nullptr);
        fclose(f);
        if (x == nullptr) continue;
        //两个目录内容基本相同，重复 CA 会被拒，这里不当作错误
        X509_STORE_add_cert(st, x);
        X509_free(x);
        ++n;
    }
    closedir(d);
    return n;
} //灌一批系统 CA

SSL_CTX* sslCtx() {
    static SSL_CTX* ctx = nullptr;
    static std::once_flag once;
    std::call_once(once, [] {
        SSL_library_init();
        ctx = SSL_CTX_new(TLS_client_method());
        if (ctx == nullptr) return;
        SSL_CTX_set_min_proto_version(ctx, TLS1_2_VERSION);
        SSL_CTX_set_mode(ctx, SSL_MODE_AUTO_RETRY);
        //Android 的系统 CA 就是 PEM 文件目录，14 起搬到 apex
        int n = loadCaDir(ctx, "/apex/com.android.conscrypt/cacerts");
        if (n == 0) n = loadCaDir(ctx, "/system/etc/security/cacerts");
        if (n == 0) SSL_CTX_set_default_verify_paths(ctx);
    });
    return ctx;
} //进程内共享一个 SSL 上下文

struct Conn {
    int fd = -1;
    SSL* ssl = nullptr;

    int writeAll(const char* d, size_t n) {
        size_t off = 0;
        while (off < n) {
            int w = ssl ? SSL_write(ssl, d + off, static_cast<int>(n - off))
                        : static_cast<int>(::send(fd, d + off, n - off, 0));
            if (w <= 0) return -1;
            off += static_cast<size_t>(w);
        }
        return 0;
    }

    int readSome(char* d, size_t n) {
        if (ssl) return SSL_read(ssl, d, static_cast<int>(n));
        return static_cast<int>(::recv(fd, d, n, 0));
    }

    void shutdown() {
        if (ssl != nullptr) {
            SSL_shutdown(ssl);
            SSL_free(ssl);
            ssl = nullptr;
        }
        if (fd >= 0) {
            ::close(fd);
            fd = -1;
        }
    }
}; //TCP 或 TLS 连接包装

std::string headerOf(const std::string& head, const std::string& name) {
    auto low = lower(head);
    auto key = "\r\n" + name + ":";
    auto i = low.find(key);
    if (i == std::string::npos) return "";
    i += key.size();
    while (i < head.size() && (head[i] == ' ' || head[i] == '\t')) ++i;
    auto j = head.find("\r\n", i);
    return head.substr(i, j == std::string::npos ? std::string::npos : j - i);
} //取某个响应头

std::vector<uint8_t> dechunk(const std::string& s) {
    std::vector<uint8_t> out;
    size_t i = 0;
    while (i < s.size()) {
        auto eol = s.find("\r\n", i);
        if (eol == std::string::npos) break;
        long len = strtol(s.substr(i, eol - i).c_str(), nullptr, 16);
        if (len <= 0) break;
        i = eol + 2;
        if (i + static_cast<size_t>(len) > s.size()) len = static_cast<long>(s.size() - i);
        out.insert(out.end(), s.begin() + i, s.begin() + i + len);
        i += static_cast<size_t>(len) + 2;
    }
    return out;
} //解 chunked

Resp oneShot(const Url& u, const std::string& method, const std::string& extraHeaders,
             const std::string& reqBody, int timeoutMs, size_t maxBody, std::string& location) {
    Resp r;
    int fd = connectTo(u.host, u.port, timeoutMs);
    if (fd < 0) {
        r.err = "connect failed";
        return r;
    }
    Conn c;
    c.fd = fd;
    if (u.tls) {
        SSL_CTX* ctx = sslCtx();
        if (ctx == nullptr) {
            c.shutdown();
            r.err = "ssl ctx failed";
            return r;
        }
        c.ssl = SSL_new(ctx);
        if (c.ssl == nullptr) {
            c.shutdown();
            r.err = "ssl new failed";
            return r;
        }
        SSL_set_fd(c.ssl, fd);
        SSL_set_tlsext_host_name(c.ssl, u.host.c_str()); //SNI：多域名服务器靠它选证书
        SSL_set1_host(c.ssl, u.host.c_str());            //证书域名校验
        SSL_set_verify(c.ssl, SSL_VERIFY_PEER, nullptr);
        if (SSL_connect(c.ssl) != 1) {
            r.err = "tls handshake failed";
            c.shutdown();
            return r;
        }
    }
    std::string req = method + " " + u.path + " HTTP/1.1\r\nHost: " + u.host +
                      "\r\nUser-Agent: Mozilla/5.0 (Android) BaiAgent\r\nAccept: */*\r\n"
                      "Accept-Encoding: identity\r\nConnection: close\r\n";
    if (!extraHeaders.empty()) {
        req += extraHeaders;
        if (req.compare(req.size() - 2, 2, "\r\n") != 0) req += "\r\n";
    }
    req += "Content-Length: " + std::to_string(reqBody.size()) + "\r\n\r\n";
    req += reqBody;
    if (c.writeAll(req.data(), req.size()) != 0) {
        r.err = "write failed";
        c.shutdown();
        return r;
    }
    std::string raw;
    char buf[8192];
    while (raw.size() < maxBody) {
        pollfd p{fd, POLLIN, 0};
        if (::poll(&p, 1, timeoutMs) <= 0) break;
        int n = c.readSome(buf, sizeof(buf));
        if (n <= 0) break;
        raw.append(buf, static_cast<size_t>(n));
        //头部到齐且 body 够长就提前收工，不等服务端关连接
        auto sp = raw.find("\r\n\r\n");
        if (sp != std::string::npos) {
            auto cl = headerOf(raw.substr(0, sp), "content-length");
            if (!cl.empty() && raw.size() - (sp + 4) >= static_cast<size_t>(strtol(cl.c_str(), nullptr, 10))) break;
        }
    }
    c.shutdown();
    auto sp = raw.find("\r\n\r\n");
    if (sp == std::string::npos) {
        r.err = "no header";
        return r;
    }
    std::string head = raw.substr(0, sp);
    r.head = head;
    std::string body = raw.substr(sp + 4);
    auto sp1 = head.find(' ');
    if (sp1 == std::string::npos) {
        r.err = "bad status line";
        return r;
    }
    r.code = atoi(head.c_str() + sp1 + 1);
    location = headerOf(head, "location");
    if (lower(headerOf(head, "transfer-encoding")).find("chunked") != std::string::npos) {
        r.body = dechunk(body);
    } else {
        r.body.assign(body.begin(), body.end());
    }
    return r;
} //发一次请求并收完整响应

std::string resolve(const Url& base, const std::string& loc) {
    if (loc.rfind("http", 0) == 0) return loc;
    std::string origin = std::string(base.tls ? "https://" : "http://") + base.host;
    if (loc.rfind("//", 0) == 0) return std::string(base.tls ? "https:" : "http:") + loc;
    if (!loc.empty() && loc[0] == '/') return origin + loc;
    auto slash = base.path.rfind('/');
    return origin + (slash == std::string::npos ? "/" : base.path.substr(0, slash + 1)) + loc;
} //相对 Location 补成绝对地址

} //namespace

Resp get(const std::string& url, int timeoutMs, int maxRedirect) {
    Resp r;
    if (timeoutMs <= 0) timeoutMs = 6000;
    Url u{};
    if (!parseUrl(url, u)) {
        r.err = "bad url";
        return r;
    }
    std::string cur = url;
    for (int hop = 0; hop <= maxRedirect; ++hop) {
        std::string loc;
        r = oneShot(u, "GET", "", "", timeoutMs, kMaxBody, loc);
        if (r.code < 300 || r.code >= 400 || r.code == 304 || loc.empty()) return r;
        cur = resolve(u, loc);
        if (!parseUrl(cur, u)) {
            r.err = "bad redirect";
            return r;
        }
    }
    return r;
} //带重定向的 GET


namespace {

bool isBlockTag(const std::string& s, size_t i) {
    static const char* names[] = {"br", "p", "div", "li", "tr", "td", "th", "h1", "h2",
                                  "h3", "h4", "h5", "h6", "section", "article", "header",
                                  "footer", "nav", "blockquote", "pre", "dt", "dd"};
    size_t j = i + 1;
    if (j < s.size() && s[j] == '/') ++j;
    for (const char* n : names) {
        size_t k = 0;
        while (n[k] != '\0' && j + k < s.size()) {
            if (tolower(static_cast<unsigned char>(s[j + k])) != n[k]) break;
            ++k;
        }
        if (n[k] != '\0') continue;
        char after = j + k < s.size() ? s[j + k] : ' ';
        if (after == '>' || after == ' ' || after == '/' || after == '\t' || after == '\n' ||
            after == '\r')
            return true;
    }
    return false;
} //是不是块级标签（要换行）

size_t findNoCase(const std::string& s, const char* needle, size_t from) {
    size_t n = strlen(needle);
    if (n == 0) return std::string::npos;
    for (size_t i = from; i + n <= s.size(); ++i) {
        size_t k = 0;
        while (k < n && tolower(static_cast<unsigned char>(s[i + k])) ==
                           tolower(static_cast<unsigned char>(needle[k])))
            ++k;
        if (k == n) return i;
    }
    return std::string::npos;
} //不区分大小写找子串

void stripBlock(std::string& s, const char* openTag, const char* closeTag) {
    size_t from = 0;
    for (;;) {
        auto a = findNoCase(s, openTag, from);
        if (a == std::string::npos) return;
        auto b = findNoCase(s, closeTag, a);
        if (b == std::string::npos) {
            s.erase(a);
            return;
        }
        s.erase(a, b + strlen(closeTag) - a);
        from = a;
    }
} //整块删掉（script / style / 注释）

void appendUtf8(std::string& o, int cp) {
    if (cp < 0x80) {
        o.push_back(static_cast<char>(cp));
    } else if (cp < 0x800) {
        o.push_back(static_cast<char>(0xC0 | (cp >> 6)));
        o.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
    } else if (cp < 0x10000) {
        o.push_back(static_cast<char>(0xE0 | (cp >> 12)));
        o.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
        o.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
    } else {
        o.push_back(static_cast<char>(0xF0 | (cp >> 18)));
        o.push_back(static_cast<char>(0x80 | ((cp >> 12) & 0x3F)));
        o.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
        o.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
    }
} //码点写成 UTF-8

void unescape(std::string& s) {
    struct Ent {
        const char* k;
        const char* v;
    };
    static const Ent es[] = {
        {"&" "nbsp;", " "},      {"&" "amp;", "&"},      {"&" "lt;", "<"},      {"&" "gt;", ">"},
        {"&" "quot;", "\""},     {"&" "apos;", "'"},     {"&" "copy;", "(c)"},  {"&" "reg;", "(R)"},
        {"&" "mdash;", "-"},     {"&" "ndash;", "-"},    {"&" "hellip;", "..."},{"&" "middot;", "-"},
        {"&" "bull;", "-"},      {"&" "times;", "x"},    {"&" "deg;", "度"},     {"&" "ldquo;", "\""},
        {"&" "rdquo;", "\""},    {"&" "lsquo;", "'"},    {"&" "rsquo;", "'"},   {"&" "laquo;", "<<"},
        {"&" "raquo;", ">>"}};
    std::string out;
    out.reserve(s.size());
    size_t i = 0;
    while (i < s.size()) {
        if (s[i] != '&') {
            out.push_back(s[i++]);
            continue;
        }
        size_t semi = s.find(';', i + 1);
        if (semi == std::string::npos || semi - i > 10) {
            out.push_back(s[i++]);
            continue;
        }
        std::string ent = s.substr(i, semi - i + 1);
        bool hit = false;
        //数字实体：&#123; 与 &#x1F600;，先判断十六进制
        if (ent.size() > 4 && ent[1] == '#' && (ent[2] == 'x' || ent[2] == 'X')) {
            int cp = static_cast<int>(strtol(ent.c_str() + 3, nullptr, 16));
            if (cp > 0) {
                appendUtf8(out, cp);
                hit = true;
            }
        } else if (ent.size() > 3 && ent[1] == '#' && isdigit(static_cast<unsigned char>(ent[2]))) {
            int cp = static_cast<int>(strtol(ent.c_str() + 2, nullptr, 10));
            if (cp > 0) {
                appendUtf8(out, cp);
                hit = true;
            }
        } else {
            for (const auto& e : es) {
                if (ent == e.k) {
                    out += e.v;
                    hit = true;
                    break;
                }
            }
        }
        if (hit) {
            i = semi + 1;
        } else {
            out.push_back(s[i++]);
        }
    }
    s.swap(out);
} //解实体：单遍扫描，命名与数字都不会二次解码

} //namespace

Resp curl(const std::string& method, const std::string& url, const std::string& headers,
          const std::string& body, int timeoutMs, size_t maxBody, int maxRedirect) {
    Resp r;
    if (timeoutMs <= 0) timeoutMs = 15000;
    if (maxBody == 0) maxBody = 1024u * 1024u;
    Url u{};
    if (!parseUrl(url, u)) {
        r.err = "bad url";
        return r;
    }
    std::string m = method;
    for (auto& c : m) c = static_cast<char>(toupper(static_cast<unsigned char>(c)));
    if (m.empty()) m = "GET";
    std::string cur = url;
    std::string payload = body;
    for (int hop = 0; hop <= maxRedirect; ++hop) {
        std::string loc;
        r = oneShot(u, m, headers, payload, timeoutMs, maxBody, loc);
        if (r.code < 300 || r.code >= 400 || r.code == 304 || loc.empty()) return r;
        cur = resolve(u, loc);
        if (!parseUrl(cur, u)) {
            r.err = "bad redirect";
            return r;
        }
        //跟 curl -L 一样：跳转后丢掉请求体，降成 GET
        m = "GET";
        payload.clear();
    }
    return r;
} //任意方法发一次请求（默认跟跳转）

Ping ping(const std::string& host, int port, int count, int timeoutMs) {
    Ping p;
    if (count < 1) count = 1;
    if (count > 10) count = 10;
    if (timeoutMs <= 0) timeoutMs = 3000;
    std::string ps = std::to_string(port > 0 ? port : 443);
    addrinfo hints{};
    hints.ai_family = AF_UNSPEC;
    hints.ai_socktype = SOCK_STREAM;
    addrinfo* res = nullptr;
    if (getaddrinfo(host.c_str(), ps.c_str(), &hints, &res) != 0 || res == nullptr) {
        p.err = "resolve failed";
        return p;
    }
    char ip[INET6_ADDRSTRLEN] = {0};
    void* sa = res->ai_family == AF_INET
                   ? static_cast<void*>(&reinterpret_cast<sockaddr_in*>(res->ai_addr)->sin_addr)
                   : static_cast<void*>(&reinterpret_cast<sockaddr_in6*>(res->ai_addr)->sin6_addr);
    inet_ntop(res->ai_family, sa, ip, sizeof(ip));
    p.ip = ip;
    freeaddrinfo(res);
    for (int i = 0; i < count; ++i) {
        auto t0 = std::chrono::steady_clock::now();
        int fd = connectTo(host, ps, timeoutMs);
        auto t1 = std::chrono::steady_clock::now();
        ++p.sent;
        if (fd >= 0) {
            ::close(fd);
            p.rtt.push_back(static_cast<int>(
                std::chrono::duration_cast<std::chrono::milliseconds>(t1 - t0).count()));
        }
        if (i + 1 < count) usleep(120 * 1000);
    }
    if (p.rtt.empty()) p.err = "connect failed";
    return p;
} //TCP 建连计时

std::string text(const std::string& html) {
    std::string s = html;
    stripBlock(s, "<script", "</script>");
    stripBlock(s, "<style", "</style>");
    stripBlock(s, "<!--", "-->");
    std::string out;
    out.reserve(s.size() / 2);
    bool inTag = false;
    for (size_t i = 0; i < s.size(); ++i) {
        char c = s[i];
        if (!inTag && c == '<') {
            if (isBlockTag(s, i)) out.push_back('\n');
            inTag = true;
            continue;
        }
        if (inTag) {
            if (c == '>') inTag = false;
            continue;
        }
        out.push_back(c);
    }
    unescape(out);
    std::string fin;
    fin.reserve(out.size());
    int nl = 0;
    bool sp = false;
    for (char c : out) {
        if (c == '\r') continue;
        if (c == '\n') {
            if (nl < 2) fin.push_back('\n');
            ++nl;
            sp = false;
            continue;
        }
        if (c == ' ' || c == '\t' || c == '\f' || c == '\v') {
            if (!sp && !fin.empty() && fin.back() != '\n') {
                fin.push_back(' ');
                sp = true;
            }
            continue;
        }
        nl = 0;
        sp = false;
        fin.push_back(c);
    }
    auto a = fin.find_first_not_of(" \n");
    if (a == std::string::npos) return "";
    auto b = fin.find_last_not_of(" \n");
    return fin.substr(a, b - a + 1);
} //HTML 取正文

} //namespace net

namespace secret {

const char *piece1() {
    return "d14b8e6027af35c9";
} //第二段

} //命名空间 secret
#include "http.h"

#include <arpa/inet.h>
#include <fcntl.h>
#include <netdb.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <poll.h>
#include <sys/socket.h>
#include <unistd.h>

#include <cctype>
#include <cerrno>
#include <cstdio>
#include <cstring>
#include <mutex>
#include <string>

#include <openssl/err.h>
#include <openssl/ssl.h>

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

SSL_CTX* sslCtx() {
    static SSL_CTX* ctx = nullptr;
    static std::once_flag once;
    std::call_once(once, [] {
        SSL_library_init();
        ctx = SSL_CTX_new(TLS_client_method());
        if (ctx == nullptr) return;
        SSL_CTX_set_min_proto_version(ctx, TLS1_2_VERSION);
        SSL_CTX_set_mode(ctx, SSL_MODE_AUTO_RETRY);
        //Android 的系统 CA 就是 OpenSSL 哈希目录格式，直接按目录加载（14 起搬到 apex）
        if (SSL_CTX_load_verify_locations(ctx, nullptr, "/apex/com.android.conscrypt/cacerts") != 1) {
            SSL_CTX_load_verify_locations(ctx, nullptr, "/system/etc/security/cacerts");
        }
        SSL_CTX_set_default_verify_paths(ctx);
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

Resp oneShot(const Url& u, int timeoutMs, std::string& location) {
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
    std::string req = "GET " + u.path + " HTTP/1.1\r\nHost: " + u.host +
                      "\r\nUser-Agent: Mozilla/5.0 (Android) BaiAgent\r\nAccept: image/*,*/*\r\n"
                      "Accept-Encoding: identity\r\nConnection: close\r\n\r\n";
    if (c.writeAll(req.data(), req.size()) != 0) {
        r.err = "write failed";
        c.shutdown();
        return r;
    }
    std::string raw;
    char buf[8192];
    while (raw.size() < kMaxBody) {
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
        r = oneShot(u, timeoutMs, loc);
        if (r.code < 300 || r.code >= 400 || r.code == 304 || loc.empty()) return r;
        cur = resolve(u, loc);
        if (!parseUrl(cur, u)) {
            r.err = "bad redirect";
            return r;
        }
    }
    return r;
} //带重定向的 GET

} //namespace net
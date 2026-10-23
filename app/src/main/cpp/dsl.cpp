#include "dsl.h"

#include <cstdio>
#include <regex>

namespace dsl {
namespace {

/**参数名 / secret 名：字母或下划线开头，后续允许数字 */
bool identChar(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
}

bool ident(const std::string &s) {
    if (s.empty()) return false;
    if (s[0] >= '0' && s[0] <= '9') return false;
    for (char c : s) if (!identChar(c)) return false;
    return true;
} //参数名合法性

/**带行列的错误前缀，模型和人看都能直接定位 */
std::string pos(size_t line, size_t col) {
    char buf[64];
    snprintf(buf, sizeof(buf), "第%zu行第%zu列: ", line, col);
    return buf;
} //错误位置

/**值里带换行会裂成第二条命令 / 第二个 header，一律拒 */
bool hasBreak(const std::string &v) {
    return v.find('\n') != std::string::npos || v.find('\r') != std::string::npos;
} //值是否含换行

/**shell 单引号包裹：内部的 ' 变成 '\''，插值后整体是一个安全的单参数 */
std::string quote(const std::string &v) {
    std::string o;
    o.reserve(v.size() + 2);
    o += '\'';
    for (char c : v) {
        if (c == '\'') o += "'\\''";
        else o += c;
    }
    o += '\'';
    return o;
} //shell 安全引用

std::string trim(const std::string &s) {
    size_t a = 0, b = s.size();
    while (a < b && (s[a] == ' ' || s[a] == '\t' || s[a] == '\r' || s[a] == '\n')) a++;
    while (b > a && (s[b - 1] == ' ' || s[b - 1] == '\t' || s[b - 1] == '\r' || s[b - 1] == '\n')) b--;
    return s.substr(a, b - a);
} //去掉两端空白

const char *KINDS[] = {"cmd", "http"};

bool kindOk(const std::string &k) {
    for (const char *s : KINDS) if (k == s) return true;
    return false;
} //kind 白名单

/**
 * 模板扫描器：一次走完全文，负责括号配对、占位符识别、参数与 secret 检查、插值。
 * args 为 nullptr 时只校验不输出（lint）。
 */
struct Scan {
    const std::string &tpl;
    const std::string &kind;
    const Table &params;
    const Table *secrets;
    const Table *args;
    std::string out;
    std::string err;

    explicit Scan(const std::string &t, const std::string &k, const Table &p, const Table *s,
                  const Table *a) : tpl(t), kind(k), params(p), secrets(s), args(a) {}

    void run() {
        const std::string head = "$secret.";
        const size_t n = tpl.size();
        size_t i = 0, line = 1, col = 1;
        while (i < n && err.empty()) {
            const char c = tpl[i];
            if (c == '{') {
                //{{ 是字面量 {
                if (i + 1 < n && tpl[i + 1] == '{') {
                    out += '{';
                    i += 2;
                    col += 2;
                    continue;
                }
                const size_t j = tpl.find('}', i + 1);
                if (j == std::string::npos) {
                    err = pos(line, col) + "花括号不配对，字面量请写 {{ }}";
                    return;
                }
                const std::string name = tpl.substr(i + 1, j - i - 1);
                if (!ident(name)) {
                    err = pos(line, col) + "非法参数名 {" + name + "}";
                    return;
                }
                if (params.find(name) == params.end()) {
                    err = pos(line, col) + "未知参数 {" + name + "}，skill.json 未声明";
                    return;
                }
                if (args != nullptr) {
                    auto it = args->find(name);
                    if (it == args->end()) {
                        err = pos(line, col) + "缺少参数 " + name;
                        return;
                    }
                    if (hasBreak(it->second)) {
                        err = pos(line, col) + "参数值不能包含换行: " + name;
                        return;
                    }
                    out += (kind == "cmd") ? quote(it->second) : it->second;
                } else {
                    //lint 模式保留占位符原文，否则 http 首行会被误判成缺 URL
                    out += tpl.substr(i, j - i + 1);
                }
                col += j - i + 1;
                i = j + 1;
                continue;
            }
            if (c == '}') {
                if (i + 1 < n && tpl[i + 1] == '}') {
                    out += '}';
                    i += 2;
                    col += 2;
                    continue;
                }
                err = pos(line, col) + "花括号不配对，字面量请写 {{ }}";
                return;
            }
            if (c == '$') {
                if (tpl.compare(i, head.size(), head) == 0) {
                    size_t j = i + head.size();
                    while (j < n && identChar(tpl[j])) j++;
                    const std::string name = tpl.substr(i + head.size(), j - i - head.size());
                    if (!ident(name)) {
                        err = pos(line, col) + "非法 secret 名 $secret." + name;
                        return;
                    }
                    if (secrets != nullptr) {
                        auto it = secrets->find(name);
                        if (it == secrets->end() || it->second.empty()) {
                            err = pos(line, col) + "$secret." + name + " 未配置";
                            return;
                        }
                        if (hasBreak(it->second)) {
                            err = pos(line, col) + "secret 值不能包含换行: " + name;
                            return;
                        }
                        out += (kind == "cmd") ? quote(it->second) : it->second;
                    } else {
                        out += tpl.substr(i, j - i);
                    }
                    col += j - i;
                    i = j;
                    continue;
                }
                out += '$';
                i++;
                col++;
                continue;
            }
            out += c;
            if (c == '\n') {
                line++;
                col = 1;
            } else {
                col++;
            }
            i++;
        }
    } //扫描整份模板
};

/**http 模板：首行 METHOD URL，随后 Header: value，第一个空行之后是 body */
bool checkHttp(const std::string &text, std::string &err) {
    const size_t nl = text.find('\n');
    const std::string first = trim(nl == std::string::npos ? text : text.substr(0, nl));
    const size_t sp = first.find(' ');
    if (sp == std::string::npos) {
        err = "第1行: http 首行必须是 METHOD URL";
        return false;
    }
    const std::string method = first.substr(0, sp);
    static const char *OK[] = {"GET", "POST", "PUT", "PATCH", "DELETE", "HEAD"};
    bool m = false;
    for (const char *s : OK) if (method == s) m = true;
    if (!m) {
        err = "第1行: 方法只支持 GET/POST/PUT/PATCH/DELETE/HEAD，收到 " + method;
        return false;
    }
    const std::string url = trim(first.substr(sp + 1));
    if (url.compare(0, 7, "http://") != 0 && url.compare(0, 8, "https://") != 0) {
        err = "第1行: URL 必须以 http:// 或 https:// 开头";
        return false;
    }
    if (url.find(' ') != std::string::npos) {
        err = "第1行: URL 里不能有空格";
        return false;
    }
    //逐行检查 header 区，遇到空行即进入 body，不再校验
    size_t i = (nl == std::string::npos) ? text.size() : nl + 1;
    size_t line = 2;
    while (i < text.size()) {
        const size_t e = text.find('\n', i);
        const std::string row = trim(text.substr(i, (e == std::string::npos ? text.size() : e) - i));
        if (row.empty()) return true;
        if (row.find(':') == std::string::npos) {
            err = "第" + std::to_string(line) + "行: header 必须写成 Name: value";
            return false;
        }
        if (e == std::string::npos) return true;
        i = e + 1;
        line++;
    }
    return true;
} //http 结构校验

/**参数值按 skill.json 声明的正则校验，写错正则也算包作者的错误 */
bool checkValues(const Table &params, const Table &args, std::string &err) {
    for (const auto &kv : args) {
        auto it = params.find(kv.first);
        if (it == params.end()) {
            err = "未知参数 " + kv.first + "，skill.json 未声明";
            return false;
        }
        if (it->second.empty()) continue;
        try {
            const std::regex re(it->second);
            if (!std::regex_search(kv.second, re)) {
                err = "参数 " + kv.first + " 的值不符合约定格式";
                return false;
            }
        } catch (const std::regex_error &) {
            err = "参数 " + kv.first + " 的正则写错了";
            return false;
        }
    }
    return true;
} //参数值校验

} //匿名命名空间

Result lint(const std::string &tpl, const std::string &kind,
            const Table &params, const Table *secrets) {
    Result r;
    if (!kindOk(kind)) {
        r.ok = false;
        r.text = "未知 kind: " + kind + "（只支持 cmd / http）";
        return r;
    }
    if (trim(tpl).empty()) {
        r.ok = false;
        r.text = "模板体为空";
        return r;
    }
    //声明的正则先自己过一遍，包作者写错不必等到用户点击才发现
    for (const auto &kv : params) {
        if (kv.second.empty()) continue;
        try {
            std::regex re(kv.second);
            (void) re;
        } catch (const std::regex_error &) {
            r.ok = false;
            r.text = "参数 " + kv.first + " 的正则写错了";
            return r;
        }
    }
    Scan s(tpl, kind, params, secrets, nullptr);
    s.run();
    if (!s.err.empty()) {
        r.ok = false;
        r.text = s.err;
        return r;
    }
    if (kind == "http") {
        std::string e;
        if (!checkHttp(s.out, e)) {
            r.ok = false;
            r.text = e;
        }
    }
    return r;
} //加载期校验

Result render(const std::string &tpl, const std::string &kind,
              const Table &params, const Table &secrets, const Table &args) {
    Result r;
    if (!kindOk(kind)) {
        r.ok = false;
        r.text = "未知 kind: " + kind + "（只支持 cmd / http）";
        return r;
    }
    std::string ve;
    //换行是最危险的一类值（能裂出第二条命令），先单独拦掉再谈正则
    for (const auto &kv : args) {
        if (hasBreak(kv.second)) {
            r.ok = false;
            r.text = "参数 " + kv.first + " 的值不能包含换行";
            return r;
        }
    }
    if (!checkValues(params, args, ve)) {
        r.ok = false;
        r.text = ve;
        return r;
    }
    Scan s(tpl, kind, params, &secrets, &args);
    s.run();
    if (!s.err.empty()) {
        r.ok = false;
        r.text = s.err;
        return r;
    }
    if (trim(s.out).empty()) {
        r.ok = false;
        r.text = "插值后内容为空";
        return r;
    }
    if (kind == "http") {
        std::string e;
        if (!checkHttp(s.out, e)) {
            r.ok = false;
            r.text = e;
            return r;
        }
    }
    r.text = s.out;
    return r;
} //校验并生成

} //dsl 命名空间
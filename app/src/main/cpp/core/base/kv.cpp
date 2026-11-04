#include "core/base/kv.h"
#include "core/base/bytes.h"

namespace bai::base {

std::string trim(const std::string &s) {
    size_t a = 0;
    size_t b = s.size();
    while (a < b && (s[a] == ' ' || s[a] == '\t' || s[a] == '\r' || s[a] == '\n')) ++a;
    while (b > a && (s[b - 1] == ' ' || s[b - 1] == '\t' || s[b - 1] == '\r' || s[b - 1] == '\n')) --b;
    return s.substr(a, b - a);
} //去空白

std::vector<std::pair<std::string, std::string>> pairs(const std::string &text) {
    std::vector<std::pair<std::string, std::string>> out;
    size_t at = 0;
    while (at <= text.size()) {
        size_t nl = text.find('\n', at);
        if (nl == std::string::npos) nl = text.size();
        const std::string line = trim(text.substr(at, nl - at));
        at = nl + 1;
        if (line.empty() || line[0] == '#') continue;
        const size_t eq = line.find('=');
        if (eq == std::string::npos) continue;
        std::string key = trim(line.substr(0, eq));
        for (auto &c : key) c = static_cast<char>(lower(static_cast<unsigned char>(c)));
        if (key.empty()) continue;
        out.push_back({key, trim(line.substr(eq + 1))});
    }
    return out;
} //解析成键值对

std::vector<std::string> csv(const std::string &v) {
    std::vector<std::string> out;
    size_t at = 0;
    while (at <= v.size() && out.size() < kMaxItems) {
        size_t comma = v.find(',', at);
        if (comma == std::string::npos) comma = v.size();
        const std::string one = trim(v.substr(at, comma - at));
        at = comma + 1;
        if (one.empty() || one.size() > kMaxItemLen) continue;
        out.push_back(one);
    }
    return out;
} //切分列表

size_t usizeIn(const std::string &v, size_t def, size_t lo, size_t hi) {
    const std::string t = trim(v);
    if (t.empty()) return def;
    size_t n = 0;
    for (char c : t) {
        if (c < '0' || c > '9') return def;
        n = n * 10 + static_cast<size_t>(c - '0');
        if (n > hi) return hi;
    }
    if (n < lo) return lo;
    return n > hi ? hi : n;
} //读整数

} //KV 文本
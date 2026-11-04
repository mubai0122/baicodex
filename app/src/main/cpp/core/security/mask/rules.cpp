#include "core/security/mask/rules.h"
#include "core/base/bytes.h"
#include "core/base/kv.h"
#include "core/base/marks.h"
#include <algorithm>

namespace bai::security::mask {

namespace {

// 长项在前，同长按字典序，保证匹配结果稳定
void rank(std::vector<std::string> &items) {
    std::sort(items.begin(), items.end(), [](const std::string &a, const std::string &b) {
        if (a.size() != b.size()) return a.size() > b.size();
        return a < b;
    });
} //排成确定顺序

std::vector<std::string> toTable(const std::vector<std::string> &items) {
    std::vector<std::string> out = items;
    rank(out);
    return out;
} //整理成表

} //匿名命名空间

const std::string *matchTable(const std::vector<std::string> &table, const std::string &src, size_t at) {
    for (const auto &one : table) {
        if (bai::base::startsCi(src, at, one)) return &one;
    }
    return nullptr;
} //命中查找

Rules builtin() {
    Rules r;
    r.keys = toTable({"refresh_token", "access_token", "id_token", "client_secret", "private_key",
                      "authorization", "x-api-key", "api_key", "api-key", "apikey", "access_key",
                      "secret_key", "secretkey", "password_hash", "password", "passwd", "passphrase",
                      "token", "secret", "cookie", "set-cookie", "sessionid", "session_id", "sessid",
                      "credential", "credentials", "bearer", "pwd", "pass"});
    r.prefixes = toTable({"sk_live_", "sk_test_", "sk-", "rk_live_", "github_pat_", "ghp_", "gho_",
                          "ghu_", "ghs_", "xoxb-", "xoxp-", "xoxa-", "xoxr-", "AKIA", "ASIA", "AIza",
                          "ya29."});
    r.auth = toTable({"Bearer ", "Basic ", "Token "});
    r.minLen = 16;
    r.alnumLen = 24;
    r.minVal = 4;
    r.mark = "******";
    return r;
} //内置兜底

Rules parse(const std::string &text) {
    Rules r = builtin();
    for (const auto &kv : bai::base::pairs(text)) {
        const std::string &key = kv.first;
        const std::string &val = kv.second;
        if (val.empty()) continue;
        if (key == "keys") {
            const auto v = bai::base::csv(val);
            if (!v.empty()) r.keys = toTable(v);
        } else if (key == "prefixes") {
            const auto v = bai::base::csv(val);
            if (!v.empty()) r.prefixes = toTable(v);
        } else if (key == "auth") {
            const auto v = bai::base::csv(val);
            if (!v.empty()) r.auth = toTable(v);
        } else if (key == "minlen") {
            r.minLen = bai::base::usizeIn(val, r.minLen, 4, 512);
        } else if (key == "alnum") {
            r.alnumLen = bai::base::usizeIn(val, r.alnumLen, 8, 4096);
        } else if (key == "minval") {
            r.minVal = bai::base::usizeIn(val, r.minVal, 1, 64);
        } else if (key == "mask") {
            r.mark = bai::base::normalizeMark(val);
        }
    }
    return r;
} //解析规则

} //脱敏规则
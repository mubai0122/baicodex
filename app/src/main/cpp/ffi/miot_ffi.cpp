#include "core/feature/miot/miot.h"

#include <cctype>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

//给 JNI 与 Rust 用的 C ABI：
//  - bai_miot_discover / call / alive 是真做事的（socket 只在 C++ 这边）；
//  - 报文头、探测包、JSON 取数由 Rust 侧提供，没有 Rust 的 ABI 这里给同名兜底实现。
//-fvisibility=hidden 下必须显式标 default，否则这些符号不进 dynsym。
#define BAI_EXPORT extern "C" __attribute__((visibility("default")))

#ifndef BAI_RUST
//纯字节运算的兜底实现：与 Rust 侧口径逐位一致，JNI 层不需要区分后端
BAI_EXPORT void bai_miot_probe(uint64_t virtualDid, uint8_t *out) {
    if (out == nullptr) return;
    std::memset(out, 0, bai::feature::miot::HEAD);
    out[0] = 0x21;
    out[1] = 0x31;
    out[3] = static_cast<uint8_t>(bai::feature::miot::HEAD);
    std::memset(out + 4, 0xFF, 12);
    std::memcpy(out + 16, "MDID", 4);
    for (int i = 0; i < 8; i++) out[20 + i] = static_cast<uint8_t>((virtualDid >> (56 - 8 * i)) & 0xFF);
}

BAI_EXPORT int64_t bai_miot_head_did(const uint8_t *p, size_t n) {
    if (p == nullptr || n < 12) return -1;
    uint64_t v = 0;
    for (size_t i = 4; i < 12; i++) v = (v << 8) | p[i];
    return static_cast<int64_t>(v);
}

BAI_EXPORT uint32_t bai_miot_head_stamp(const uint8_t *p, size_t n) {
    if (p == nullptr || n < 16) return 0;
    return (static_cast<uint32_t>(p[12]) << 24) | (static_cast<uint32_t>(p[13]) << 16) |
           (static_cast<uint32_t>(p[14]) << 8) | static_cast<uint32_t>(p[15]);
}

BAI_EXPORT int64_t bai_miot_json_int(const uint8_t *p, size_t n, const char *key, size_t klen) {
    if (p == nullptr || key == nullptr || klen == 0 || n < klen + 3) return INT64_MIN;
    const std::string text(reinterpret_cast<const char *>(p), n);
    const std::string k(key, klen);
    size_t i = 0;
    while (i + klen + 1 < text.size()) {
        const bool isKey = text[i] == '"' && text.compare(i + 1, klen, k) == 0 && text[i + 1 + klen] == '"';
        if (isKey) {
            size_t j = i + 2 + klen;
            while (j < text.size() && std::isspace(static_cast<unsigned char>(text[j]))) j++;
            if (j < text.size() && text[j] == ':') {
                j++;
                while (j < text.size() && std::isspace(static_cast<unsigned char>(text[j]))) j++;
                const size_t start = j;
                if (j < text.size() && text[j] == '-') j++;
                const size_t digitAt = j;
                while (j < text.size() && text[j] >= '0' && text[j] <= '9') j++;
                if (j > digitAt) {
                    const long long v = std::strtoll(text.substr(start, j - start).c_str(), nullptr, 10);
                    return static_cast<int64_t>(v);
                }
            }
        }
        i++;
    }
    return INT64_MIN;
}
#endif

BAI_EXPORT int bai_miot_discover(int timeoutMs, unsigned char *out, int cap) {
    if (out == nullptr || cap <= 0) return -1;
    const std::vector<bai::feature::miot::Found> found = bai::feature::miot::discover(timeoutMs);
    std::string lines;
    for (const auto &f : found) {
        lines += std::to_string(f.did) + "|" + f.ip + "|" + std::to_string(f.stamp) + "\n";
    }
    if (static_cast<int>(lines.size()) > cap) return -2;
    if (!lines.empty()) std::memcpy(out, lines.data(), lines.size());
    return static_cast<int>(lines.size());
} //广播探测：每行 did|ip|stamp

BAI_EXPORT int bai_miot_call(const char *ip, long long did, const char *tokenHex, unsigned int stamp,
                             const char *json, int timeoutMs, unsigned char *out, int cap,
                             unsigned int *stampOut) {
    if (ip == nullptr || tokenHex == nullptr || json == nullptr || out == nullptr || cap <= 0) return -1;
    const std::vector<uint8_t> token = bai::feature::miot::tokenOf(tokenHex);
    if (token.size() != 16) return -3;
    uint32_t s = 0;
    const std::string text =
            bai::feature::miot::call(ip, did, token, stamp, json, timeoutMs, &s);
    if (text.empty()) return 0;
    if (static_cast<int>(text.size()) > cap) return -2;
    std::memcpy(out, text.data(), text.size());
    if (stampOut != nullptr) *stampOut = s;
    return static_cast<int>(text.size());
} //单播一次请求-应答，返回明文 JSON 字节数

BAI_EXPORT int bai_miot_alive(const char *ip, long long did, int timeoutMs, unsigned int *stampOut) {
    if (ip == nullptr) return 0;
    uint32_t s = 0;
    const bool ok = bai::feature::miot::alive(ip, did, timeoutMs, &s);
    if (ok && stampOut != nullptr) *stampOut = s;
    return ok ? 1 : 0;
} //单台探活
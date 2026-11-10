#include "core/feature/miot/miot.h"

#include <cstring>

#include "core/security/crypto/crypto.h"

namespace bai::feature::miot {

namespace {
/** 单个十六进制字符 → 0..15，非十六进制返回 -1 */
int digit(char c) {
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    return -1;
}
} //命名空间

std::vector<uint8_t> tokenOf(const std::string &hex) {
    std::string s;
    s.reserve(hex.size());
    for (char c : hex) {
        if (c == ':' || c == ' ') continue;
        s.push_back(c);
    }
    if (s.size() != 32) return {};
    std::vector<uint8_t> out(16);
    for (size_t i = 0; i < 16; i++) {
        const auto hi = digit(s[i * 2]);
        const auto lo = digit(s[i * 2 + 1]);
        if (hi < 0 || lo < 0) return {};
        out[i] = static_cast<uint8_t>((hi << 4) | lo);
    }
    return out;
} //token 转字节

std::vector<uint8_t> pack(const std::vector<uint8_t> &token, int64_t did, uint32_t stamp, const std::string &json) {
    if (token.size() != 16) return {};
    std::string enc;
    if (!security::crypto::aes128CbcEncrypt(token.data(), token.data(),
                                           reinterpret_cast<const uint8_t *>(json.data()), json.size(), enc)) {
        return {};
    }
    const size_t total = HEAD + enc.size();
    if (total > 0xFFFF) return {};
    std::vector<uint8_t> out(total, 0);
    out[0] = 0x21;
    out[1] = 0x31;
    out[2] = static_cast<uint8_t>((total >> 8) & 0xFF);
    out[3] = static_cast<uint8_t>(total & 0xFF);
    const uint64_t v = static_cast<uint64_t>(did);
    for (int i = 0; i < 8; i++) out[4 + i] = static_cast<uint8_t>((v >> (56 - 8 * i)) & 0xFF);
    out[12] = static_cast<uint8_t>((stamp >> 24) & 0xFF);
    out[13] = static_cast<uint8_t>((stamp >> 16) & 0xFF);
    out[14] = static_cast<uint8_t>((stamp >> 8) & 0xFF);
    out[15] = static_cast<uint8_t>(stamp & 0xFF);
    std::memcpy(out.data() + 16, token.data(), 16);
    std::memcpy(out.data() + HEAD, enc.data(), enc.size());
    //先塞 token 再算 MD5：设备侧的口径是「校验位之外整包（含 token）」，算完把 token 段覆写成校验位
    uint8_t sum[16];
    if (!security::crypto::md5(out.data(), total, sum)) return {};
    std::memcpy(out.data() + 16, sum, 16);
    return out;
} //打一包

std::string unpack(const std::vector<uint8_t> &token, const uint8_t *raw, size_t n) {
    if (token.size() != 16 || raw == nullptr || n <= HEAD) return {};
    if (raw[0] != 0x21 || raw[1] != 0x31) return {};
    const size_t declared = (static_cast<size_t>(raw[2]) << 8) | static_cast<size_t>(raw[3]);
    //设备有时把总长写小或写超，按实收裁，不信头里的长度
    const size_t len = (declared >= HEAD && declared <= n) ? declared : n;
    std::vector<uint8_t> body(raw, raw + len);
    uint8_t want[16];
    std::memcpy(want, body.data() + 16, 16);
    std::memcpy(body.data() + 16, token.data(), 16);
    uint8_t got[16];
    if (!security::crypto::md5(body.data(), body.size(), got)) return {};
    if (std::memcmp(want, got, 16) != 0) return {};
    std::string clear;
    if (!security::crypto::aes128CbcDecrypt(token.data(), token.data(), raw + HEAD, len - HEAD, clear)) return {};
    //个别设备在 JSON 尾巴补 \0，去掉再交给上层
    while (!clear.empty() && clear.back() == '\0') clear.pop_back();
    return clear;
} //解一包

} //namespace bai::feature::miot
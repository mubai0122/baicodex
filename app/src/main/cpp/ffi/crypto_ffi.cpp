#include "core/security/crypto/crypto.h"

#include <cstring>

//给 Rust 侧用的 C ABI：Rust 只做声明（extern "C"），实现全在这里，避免两套算法各算一份。
//-fvisibility=hidden 下必须显式标 default，否则这些符号不进 dynsym。
#define BAI_EXPORT extern "C" __attribute__((visibility("default")))

BAI_EXPORT size_t bai_aes128_cbc_enc_len(size_t inLen) {
    return bai::security::crypto::encLen(inLen);
} //补齐后密文长度

BAI_EXPORT int bai_aes128_cbc_encrypt(const unsigned char *key, const unsigned char *iv,
                                      const unsigned char *in, size_t inLen,
                                      unsigned char *out, size_t outCap, size_t *outLen) {
    if (key == nullptr || iv == nullptr || out == nullptr || outLen == nullptr) return -1;
    if (in == nullptr && inLen > 0) return -1;
    std::string ct;
    if (!bai::security::crypto::aes128CbcEncrypt(key, iv, in, inLen, ct)) return -2;
    if (ct.size() > outCap) return -3;
    std::memcpy(out, ct.data(), ct.size());
    *outLen = ct.size();
    return 0;
} //AES-128-CBC 加密，返回 0 成功

BAI_EXPORT int bai_aes128_cbc_decrypt(const unsigned char *key, const unsigned char *iv,
                                      const unsigned char *in, size_t inLen,
                                      unsigned char *out, size_t outCap, size_t *outLen) {
    if (key == nullptr || iv == nullptr || in == nullptr || out == nullptr || outLen == nullptr) return -1;
    std::string pt;
    if (!bai::security::crypto::aes128CbcDecrypt(key, iv, in, inLen, pt)) return -2;
    if (pt.size() > outCap) return -3;
    if (!pt.empty()) std::memcpy(out, pt.data(), pt.size());
    *outLen = pt.size();
    return 0;
} //AES-128-CBC 解密，补齐不合法返回 -2

BAI_EXPORT int bai_md5(const unsigned char *in, size_t inLen, unsigned char *out16) {
    if (out16 == nullptr || (in == nullptr && inLen > 0)) return -1;
    return bai::security::crypto::md5(in, inLen, out16) ? 0 : -2;
} //MD5 原始 16 字节

BAI_EXPORT int bai_sha1(const unsigned char *in, size_t inLen, unsigned char *out20) {
    if (out20 == nullptr || (in == nullptr && inLen > 0)) return -1;
    return bai::security::crypto::sha1(in, inLen, out20) ? 0 : -2;
} //SHA-1 原始 20 字节

BAI_EXPORT int bai_md5_hex(const unsigned char *in, size_t inLen, char *out33) {
    if (out33 == nullptr) return -1;
    const std::string s = bai::security::crypto::md5Hex(in, inLen);
    if (s.size() != 32) return -2;
    std::memcpy(out33, s.data(), 32);
    out33[32] = '\0';
    return 0;
} //MD5 小写十六进制，调用方给 33 字节

BAI_EXPORT int bai_sha1_hex(const unsigned char *in, size_t inLen, char *out41) {
    if (out41 == nullptr) return -1;
    const std::string s = bai::security::crypto::sha1Hex(in, inLen);
    if (s.size() != 40) return -2;
    std::memcpy(out41, s.data(), 40);
    out41[40] = '\0';
    return 0;
} //SHA-1 小写十六进制，调用方给 41 字节

BAI_EXPORT int bai_crypto_selftest() {
    return bai::security::crypto::selfTest() ? 1 : 0;
} //启动自检：1 正常
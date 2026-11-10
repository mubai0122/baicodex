#include "crypto.h"

#include <openssl/evp.h>

#include <cstring>
#include <vector>

namespace bai::security::crypto {
namespace {

constexpr int BLOCK = 16;
const char *HEXD = "0123456789abcdef";

bool digest(const EVP_MD *md, const void *in, size_t inLen, uint8_t *out, unsigned int outLen) {
    unsigned int n = 0;
    if (EVP_Digest(in, inLen, out, &n, md, nullptr) != 1) return false;
    return n == outLen;
} //EVP_Digest 统一入口

bool cbc(bool encrypt, const uint8_t key[16], const uint8_t iv[16],
         const uint8_t *in, size_t inLen, std::string &out) {
    if (key == nullptr || iv == nullptr || (in == nullptr && inLen > 0)) return false;
    //密文/明文最长只可能比输入多一个补齐块
    std::vector<uint8_t> buf(inLen + BLOCK);
    EVP_CIPHER_CTX *c = EVP_CIPHER_CTX_new();
    if (c == nullptr) return false;
    int len = 0;
    int done = 0;
    bool ok = EVP_CipherInit_ex(c, EVP_aes_128_cbc(), nullptr, key, iv, encrypt ? 1 : 0) == 1 &&
              EVP_CipherUpdate(c, buf.data(), &len, in, static_cast<int>(inLen)) == 1 &&
              EVP_CipherFinal_ex(c, buf.data() + len, &done) == 1;
    EVP_CIPHER_CTX_free(c);
    //解密时 EVP_CipherFinal_ex 不合法补齐会返回 0，正好挡掉错密钥与坏报文
    if (!ok) return false;
    out.assign(reinterpret_cast<const char *>(buf.data()), static_cast<size_t>(len + done));
    return true;
} //CBC 加解密本体
} //命名空间

size_t encLen(size_t inLen) {
    return (inLen / BLOCK + 1) * BLOCK;
} //补齐后长度

bool aes128CbcEncrypt(const uint8_t key[16], const uint8_t iv[16], const uint8_t *in, size_t inLen, std::string &out) {
    return cbc(true, key, iv, in, inLen, out);
} //AES-128-CBC 加密

bool aes128CbcDecrypt(const uint8_t key[16], const uint8_t iv[16], const uint8_t *in, size_t inLen, std::string &out) {
    if (inLen == 0 || inLen % BLOCK != 0) return false;
    return cbc(false, key, iv, in, inLen, out);
} //AES-128-CBC 解密

bool md5(const void *in, size_t inLen, uint8_t out[16]) {
    return digest(EVP_md5(), in, inLen, out, 16);
} //MD5

bool sha1(const void *in, size_t inLen, uint8_t out[20]) {
    return digest(EVP_sha1(), in, inLen, out, 20);
} //SHA-1

std::string hex(const uint8_t *p, size_t n) {
    std::string s;
    s.resize(n * 2);
    for (size_t i = 0; i < n; i++) {
        s[i * 2] = HEXD[(p[i] >> 4) & 0xF];
        s[i * 2 + 1] = HEXD[p[i] & 0xF];
    }
    return s;
} //小写十六进制

std::string md5Hex(const void *in, size_t inLen) {
    uint8_t d[16];
    if (!md5(in, inLen, d)) return "";
    return hex(d, sizeof(d));
} //MD5 十六进制

std::string sha1Hex(const void *in, size_t inLen) {
    uint8_t d[20];
    if (!sha1(in, inLen, d)) return "";
    return hex(d, sizeof(d));
} //SHA-1 十六进制

bool selfTest() {
    //已知答案：空串与 "abc" 的 MD5 / SHA-1 是标准向量，算错就说明摘要层有问题
    if (md5Hex("", 0) != "d41d8cd98f00b204e9800998ecf8427e") return false;
    if (md5Hex("abc", 3) != "900150983cd24fb0d6963f7d28e17f72") return false;
    if (sha1Hex("abc", 3) != "a9993e364706816aba3e25717850c26c9cd0d89d") return false;
    uint8_t key[16];
    uint8_t iv[16];
    for (int i = 0; i < 16; i++) {
        key[i] = static_cast<uint8_t>(i);
        iv[i] = static_cast<uint8_t>(0xF0 - i);
    }
    const char *msg = "miot payload / 米家报文";
    const size_t n = std::strlen(msg);
    std::string ct;
    if (!aes128CbcEncrypt(key, iv, reinterpret_cast<const uint8_t *>(msg), n, ct)) return false;
    if (ct.size() != encLen(n) || ct.size() % BLOCK != 0) return false;
    std::string pt;
    if (!aes128CbcDecrypt(key, iv, reinterpret_cast<const uint8_t *>(ct.data()), ct.size(), pt)) return false;
    if (pt.size() != n || std::memcmp(pt.data(), msg, n) != 0) return false;
    //满块输入也必须补一整块，否则对接方解出来是半截
    std::string exact(16, 'a');
    std::string ct2;
    if (!aes128CbcEncrypt(key, iv, reinterpret_cast<const uint8_t *>(exact.data()), exact.size(), ct2)) return false;
    if (ct2.size() != 32) return false;
    //错密钥必须解不开：解出乱码比报错更危险，宁可让上层判定失败
    //少部分错密钥碰巧通过补齐校验（概率约 1/256），所以判据是「要么解不开，要么解出来不是原文」
    uint8_t bad[16];
    std::memcpy(bad, key, 16);
    bad[0] ^= 0xFF;
    std::string junk;
    if (aes128CbcDecrypt(bad, iv, reinterpret_cast<const uint8_t *>(ct.data()), ct.size(), junk) &&
        junk.size() == n && std::memcmp(junk.data(), msg, n) == 0) {
        return false;
    }
    return true;
} //自检
} //命名空间 bai::security::crypto
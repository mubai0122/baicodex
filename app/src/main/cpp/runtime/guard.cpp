#include "guard.h"

#include <openssl/crypto.h>
#include <openssl/evp.h>
#include <openssl/kdf.h>
#include <openssl/params.h>
#include <openssl/rand.h>

#include <cstring>
#include <mutex>

#include "secret.h"

#ifdef BAI_RUST
extern "C" {
void bai_xor(unsigned char *dst, const unsigned char *a, const unsigned char *b, size_t len);
}
#endif

namespace guard {

namespace {

std::mutex LK;
std::vector<unsigned char> K2; //组合半密钥，unlock 后立刻销毁
std::vector<unsigned char> KD; //数据密钥，解锁后常驻（AES 每写一次都要用）

void wipe(std::vector<unsigned char> &v) {
    if (!v.empty()) OPENSSL_cleanse(v.data(), v.size());
    v.clear();
} //抹掉密钥

int hexVal(char c) {
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    return -1;
} //十六进制字符

std::vector<unsigned char> unhex(const std::string &s) {
    if (s.size() < 2 || s.size() % 2 != 0) return {};
    std::vector<unsigned char> out(s.size() / 2);
    for (size_t i = 0; i < out.size(); ++i) {
        const int a = hexVal(s[i * 2]);
        const int b = hexVal(s[i * 2 + 1]);
        if (a < 0 || b < 0) return {};
        out[i] = static_cast<unsigned char>((a << 4) | b);
    }
    return out;
} //十六进制转字节

std::string tohex(const unsigned char *p, size_t n) {
    static const char HEX[] = "0123456789abcdef";
    std::string s;
    s.reserve(n * 2);
    for (size_t i = 0; i < n; ++i) {
        s.push_back(HEX[(p[i] >> 4) & 0xF]);
        s.push_back(HEX[p[i] & 0xF]);
    }
    return s;
} //字节转十六进制

//HKDF-SHA256：第二种子当 ikm，设备因子当 salt，输出 32 字节
bool hkdf32(const std::vector<unsigned char> &ikm, const std::string &salt,
            std::vector<unsigned char> &out) {
    if (ikm.empty()) return false;
    EVP_KDF *f = EVP_KDF_fetch(nullptr, "HKDF", nullptr);
    if (f == nullptr) return false;
    EVP_KDF_CTX *c = EVP_KDF_CTX_new(f);
    EVP_KDF_free(f);
    if (c == nullptr) return false;
    char md[] = "SHA256";
    char info[] = "bai.guard.v1";
    std::string s = salt.empty() ? std::string("bai.guard.salt") : salt;
    OSSL_PARAM p[5];
    p[0] = OSSL_PARAM_construct_utf8_string("digest", md, 0);
    p[1] = OSSL_PARAM_construct_octet_string("key", const_cast<unsigned char *>(ikm.data()), ikm.size());
    p[2] = OSSL_PARAM_construct_octet_string("salt", const_cast<char *>(s.data()), s.size());
    p[3] = OSSL_PARAM_construct_octet_string("info", info, sizeof(info) - 1);
    p[4] = OSSL_PARAM_construct_end();
    out.assign(32, 0);
    const int ok = EVP_KDF_derive(c, out.data(), out.size(), p);
    EVP_KDF_CTX_free(c);
    if (ok != 1) {
        wipe(out);
        return false;
    }
    return true;
} //HKDF 派生

} //匿名命名空间

bool start(const std::string &deviceFactor) {
    std::vector<unsigned char> seed = secret::seedCopy();
    if (seed.size() < 16) {
        wipe(seed);
        return false;
    }
    //第二种子 = HKDF(主种子, "bai.guard.seed2")：跟落盘密钥的种子分开派生，任何一半都还原不出数据密钥
    std::vector<unsigned char> s2;
    const bool ok1 = hkdf32(seed, std::string("bai.guard.seed2"), s2);
    wipe(seed);
    if (!ok1) return false;
    std::vector<unsigned char> k2;
    const bool ok2 = hkdf32(s2, deviceFactor, k2);
    wipe(s2);
    if (!ok2) return false;
    std::lock_guard<std::mutex> g(LK);
    wipe(K2);
    K2 = std::move(k2);
    return true;
} //派生组合半密钥

bool unlock(const std::string &r64hex) {
    std::vector<unsigned char> r = unhex(r64hex);
    if (r.size() != 32) {
        wipe(r);
        return false;
    }
    std::lock_guard<std::mutex> g(LK);
    if (K2.size() != 32) {
        wipe(r);
        return false;
    }
    wipe(KD);
    KD.assign(32, 0);
#ifdef BAI_RUST
    bai_xor(KD.data(), r.data(), K2.data(), 32);
#else
    for (size_t i = 0; i < 32; ++i) KD[i] = static_cast<unsigned char>(r[i] ^ K2[i]);
#endif
    wipe(r);
    wipe(K2); //异或完就销毁半密钥，内存里只剩合成后的数据密钥
    return true;
} //异或组合

void lock() {
    std::lock_guard<std::mutex> g(LK);
    wipe(K2);
    wipe(KD);
} //清密钥

bool ready() {
    std::lock_guard<std::mutex> g(LK);
    return KD.size() == 32;
} //是否已解锁

std::string randomHex() {
    unsigned char r[32];
    if (RAND_bytes(r, 32) != 1) return "";
    std::string s = tohex(r, 32);
    OPENSSL_cleanse(r, sizeof(r));
    return s;
} //随机半密钥

std::vector<unsigned char> key() {
    std::lock_guard<std::mutex> g(LK);
    if (KD.size() != 32) return {};
    return std::vector<unsigned char>(KD);
} //取数据密钥副本

} //命名空间 guard
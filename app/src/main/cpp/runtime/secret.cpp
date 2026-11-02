#include "secret.h"

#include <openssl/evp.h>
#include <openssl/rand.h>

#include <cstdint>
#include <cstdio>
#include <cstring>
#include <vector>

namespace secret {

//四段片段分别由这四个函数提供，散在不同编译单元
const char *piece0();
const char *piece1();
const char *piece2();
const char *piece3();

namespace {

//密钥四段分散在四个源文件里，任何单独一段都还原不出密钥；
//构建时用 -DBAI_KEY_HEX=... 覆盖（由 local.properties 注入，不进版本库）
const char MAGIC[] = "baicodexencryption";
const size_t MAGIC_LEN = sizeof(MAGIC) - 1;
const size_t NONCE_LEN = 12;
const size_t TAG_LEN = 16;
const size_t HEAD_LEN = MAGIC_LEN + 1 + 4;

#ifdef BAI_KEY_BYTES
const unsigned char SEED[] = { BAI_KEY_BYTES };
#endif

std::vector<unsigned char> derive() {
    std::string s = std::string(piece0()) + piece1() + piece2() + piece3();
#ifdef BAI_KEY_BYTES
    //构建期注入的种子不是整串字符串，而是拆好的字节数组，二进制里扫不到连读的十六进制
    s.append(reinterpret_cast<const char *>(SEED), sizeof(SEED));
#endif
    std::vector<unsigned char> out(32);
    unsigned int n = 0;
    if (EVP_Digest(s.data(), s.size(), out.data(), &n, EVP_sha256(), nullptr) != 1 || n != 32) {
        out.assign(32, 0);
    }
    return out;
} //拼出 32 字节密钥

std::vector<unsigned char> headOf(size_t n) {
    std::vector<unsigned char> h(HEAD_LEN);
    memcpy(h.data(), MAGIC, MAGIC_LEN);
    h[MAGIC_LEN] = 1;
    h[MAGIC_LEN + 1] = static_cast<unsigned char>((n >> 24) & 0xFF);
    h[MAGIC_LEN + 2] = static_cast<unsigned char>((n >> 16) & 0xFF);
    h[MAGIC_LEN + 3] = static_cast<unsigned char>((n >> 8) & 0xFF);
    h[MAGIC_LEN + 4] = static_cast<unsigned char>(n & 0xFF);
    return h;
} //文件头（也当 AAD 用）

} //匿名命名空间

std::string seal(const std::string &plain) {
    std::vector<unsigned char> key = derive();
    unsigned char nonce[NONCE_LEN];
    if (RAND_bytes(nonce, static_cast<int>(NONCE_LEN)) != 1) return "";
    std::vector<unsigned char> head = headOf(plain.size());
    std::vector<unsigned char> ct(plain.size() + TAG_LEN);
    unsigned char tag[TAG_LEN];
    EVP_CIPHER_CTX *c = EVP_CIPHER_CTX_new();
    if (c == nullptr) return "";
    int len = 0;
    int total = 0;
    bool ok = EVP_EncryptInit_ex(c, EVP_aes_256_gcm(), nullptr, nullptr, nullptr) == 1 &&
              EVP_CIPHER_CTX_ctrl(c, EVP_CTRL_GCM_SET_IVLEN, static_cast<int>(NONCE_LEN), nullptr) == 1 &&
              EVP_EncryptInit_ex(c, nullptr, nullptr, key.data(), nonce) == 1 &&
              EVP_EncryptUpdate(c, nullptr, &len, head.data(), static_cast<int>(head.size())) == 1;
    if (ok && !plain.empty()) {
        ok = EVP_EncryptUpdate(c, ct.data(), &len,
                               reinterpret_cast<const unsigned char *>(plain.data()),
                               static_cast<int>(plain.size())) == 1;
        total = len;
    }
    if (ok) ok = EVP_EncryptFinal_ex(c, ct.data() + total, &len) == 1 && len == 0;
    if (ok) ok = EVP_CIPHER_CTX_ctrl(c, EVP_CTRL_GCM_GET_TAG, static_cast<int>(TAG_LEN), tag) == 1;
    EVP_CIPHER_CTX_free(c);
    if (!ok) return "";
    std::string out(reinterpret_cast<const char *>(head.data()), head.size());
    out.append(reinterpret_cast<const char *>(nonce), NONCE_LEN);
    out.append(reinterpret_cast<const char *>(ct.data()), static_cast<size_t>(total));
    out.append(reinterpret_cast<const char *>(tag), TAG_LEN);
    return out;
} //加密并拼成完整文件内容

std::string open(const std::string &blob) {
    const size_t need = HEAD_LEN + NONCE_LEN + TAG_LEN;
    if (blob.size() < need) return "";
    if (blob.compare(0, MAGIC_LEN, MAGIC) != 0) return "";
    const unsigned char *raw = reinterpret_cast<const unsigned char *>(blob.data());
    if (raw[MAGIC_LEN] != 1) return "";
    const size_t n = (static_cast<size_t>(raw[MAGIC_LEN + 1]) << 24) |
                     (static_cast<size_t>(raw[MAGIC_LEN + 2]) << 16) |
                     (static_cast<size_t>(raw[MAGIC_LEN + 3]) << 8) |
                     static_cast<size_t>(raw[MAGIC_LEN + 4]);
    //长度对不上就是被截断或拼接过，直接拒，别把垃圾喂给解析器
    if (blob.size() != need + n) return "";
    std::vector<unsigned char> head(raw, raw + HEAD_LEN);
    const unsigned char *nonce = raw + HEAD_LEN;
    const unsigned char *ct = nonce + NONCE_LEN;
    unsigned char tag[TAG_LEN];
    memcpy(tag, raw + HEAD_LEN + NONCE_LEN + n, TAG_LEN);
    std::vector<unsigned char> key = derive();
    std::vector<unsigned char> pt(n > 0 ? n : 1);
    EVP_CIPHER_CTX *c = EVP_CIPHER_CTX_new();
    if (c == nullptr) return "";
    int len = 0;
    int total = 0;
    bool ok = EVP_DecryptInit_ex(c, EVP_aes_256_gcm(), nullptr, nullptr, nullptr) == 1 &&
              EVP_CIPHER_CTX_ctrl(c, EVP_CTRL_GCM_SET_IVLEN, static_cast<int>(NONCE_LEN), nullptr) == 1 &&
              EVP_DecryptInit_ex(c, nullptr, nullptr, key.data(), nonce) == 1 &&
              EVP_DecryptUpdate(c, nullptr, &len, head.data(), static_cast<int>(head.size())) == 1;
    if (ok && n > 0) {
        ok = EVP_DecryptUpdate(c, pt.data(), &len, ct, static_cast<int>(n)) == 1;
        total = len;
    }
    if (ok) ok = EVP_CIPHER_CTX_ctrl(c, EVP_CTRL_GCM_SET_TAG, static_cast<int>(TAG_LEN), tag) == 1;
    int fin = ok ? EVP_DecryptFinal_ex(c, pt.data() + total, &len) : 0;
    EVP_CIPHER_CTX_free(c);
    if (!ok || fin != 1) return "";
    return std::string(reinterpret_cast<const char *>(pt.data()), n);
} //校验头与 GCM tag 后解密

bool save(const std::string &path, const std::string &plain) {
    const std::string blob = seal(plain);
    if (blob.empty()) return false;
    const std::string tmp = path + ".tmp";
    FILE *f = fopen(tmp.c_str(), "wb");
    if (f == nullptr) return false;
    const size_t w = fwrite(blob.data(), 1, blob.size(), f);
    const int flushed = fflush(f);
    fclose(f);
    if (w != blob.size() || flushed != 0) {
        remove(tmp.c_str());
        return false;
    }
    if (rename(tmp.c_str(), path.c_str()) != 0) {
        remove(path.c_str());
        if (rename(tmp.c_str(), path.c_str()) != 0) {
            remove(tmp.c_str());
            return false;
        }
    }
    return true;
} //原子落盘

std::string load(const std::string &path) {
    FILE *f = fopen(path.c_str(), "rb");
    if (f == nullptr) return "";
    std::string blob;
    char buf[4096];
    size_t n = 0;
    while ((n = fread(buf, 1, sizeof(buf), f)) > 0) blob.append(buf, n);
    fclose(f);
    return open(blob);
} //整份读入再解密

std::string fingerprint() {
    const std::vector<unsigned char> key = derive();
    static const char HEX[] = "0123456789abcdef";
    std::string out;
    for (int i = 0; i < 8; ++i) {
        out.push_back(HEX[(key[i] >> 4) & 0xF]);
        out.push_back(HEX[key[i] & 0xF]);
    }
    return out;
} //密钥指纹

const char *piece3() {
    return "3e6f08d5b94a17c2";
} //第四段

} //命名空间 secret
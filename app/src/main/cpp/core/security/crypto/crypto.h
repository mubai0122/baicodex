#pragma once
#include <cstddef>
#include <cstdint>
#include <string>

//米家报文要的只有三样：AES-128-CBC（token 当密钥和 IV）、MD5（校验位）、SHA-1（state 派生）。
//算法全部走 android_openssl，这里只做长度计算、PKCS7 补齐与十六进制出参，不手搓任何分组运算。
namespace bai::security::crypto {
/** PKCS7 补齐后的密文长度：恒为 16 的倍数，且一定大于明文（满块也要补一整块） */
size_t encLen(size_t inLen);

/** AES-128-CBC 加密，PKCS7 补齐；key / iv 各 16 字节，明文长度不限 */
bool aes128CbcEncrypt(const uint8_t key[16], const uint8_t iv[16], const uint8_t *in, size_t inLen, std::string &out);

/** AES-128-CBC 解密，解开后校验并去掉 PKCS7 补齐；补齐不合法直接返回 false，绝不返回半截明文 */
bool aes128CbcDecrypt(const uint8_t key[16], const uint8_t iv[16], const uint8_t *in, size_t inLen, std::string &out);

bool md5(const void *in, size_t inLen, uint8_t out[16]);
bool sha1(const void *in, size_t inLen, uint8_t out[20]);

std::string hex(const uint8_t *p, size_t n);
std::string md5Hex(const void *in, size_t inLen);
std::string sha1Hex(const void *in, size_t inLen);

/** 固定向量自检：MD5 / SHA-1 已知答案 + AES 往返 + 错密钥必须解不开 */
bool selfTest();
} //命名空间 bai::security::crypto

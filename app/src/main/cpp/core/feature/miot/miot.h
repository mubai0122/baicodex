#pragma once
#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

//米家局域网链路：报文编解码（走 crypto）+ UDP 探测与单播对话。
//报文头 / 探测包 / JSON 取数这三样由 Rust 侧实现（同名 C ABI），没 Rust 的 ABI 用 ffi/miot_ffi.cpp 的兜底。
extern "C" {
void bai_miot_probe(uint64_t virtualDid, uint8_t *out);
int64_t bai_miot_head_did(const uint8_t *p, size_t n);
uint32_t bai_miot_head_stamp(const uint8_t *p, size_t n);
int64_t bai_miot_json_int(const uint8_t *p, size_t n, const char *key, size_t klen);
//下面三个是 JNI 直接调的入口，实现在 ffi/miot_ffi.cpp（socket 只在 C++ 这边）
int bai_miot_discover(int timeoutMs, unsigned char *out, int cap);
int bai_miot_call(const char *ip, long long did, const char *tokenHex, unsigned int stamp, const char *json,
                  int timeoutMs, unsigned char *out, int cap, unsigned int *stampOut);
int bai_miot_alive(const char *ip, long long did, int timeoutMs, unsigned int *stampOut);
}

namespace bai::feature::miot {
constexpr size_t HEAD = 32;   //报文头长度：magic2 + 总长2 + did8 + 时间戳4 + 校验16
constexpr int PORT = 54321;   //局域网端口

struct Found {                //局域网里探到的一台设备
    int64_t did = 0;
    std::string ip;
    uint32_t stamp = 0;       //设备侧时间戳，调用方据此算时钟差
};

/** 32 位十六进制 → 16 字节 token；格式不对返回空 */
std::vector<uint8_t> tokenOf(const std::string &hex);

/** 打一包：明文 JSON → AES 加密 → 填头 → 补 MD5，token 同时当密钥与 IV */
std::vector<uint8_t> pack(const std::vector<uint8_t> &token, int64_t did, uint32_t stamp, const std::string &json);

/** 解一包：先还原 token 验 MD5，再解密；任何一步不对都返回空串 */
std::string unpack(const std::vector<uint8_t> &token, const uint8_t *raw, size_t n);

/** 广播探测：全局广播 + 每张网卡子网广播，收满 timeoutMs 的所有应答 */
std::vector<Found> discover(int timeoutMs);

/** 单播一次请求-应答：拿不到回包 / 校验失败 / id 不匹配都返回空串 */
std::string call(const std::string &ip, int64_t did, const std::vector<uint8_t> &token, uint32_t stamp,
                 const std::string &json, int timeoutMs, uint32_t *stampOut);

/** 探活：32 字节探测包有应答就算在线 */
bool alive(const std::string &ip, int64_t did, int timeoutMs, uint32_t *stampOut);
} //namespace bai::feature::miot
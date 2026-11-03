#pragma once

#include <string>
#include <vector>

/**
 * 数据保障（三层密钥）
 * 1 设备密钥层：随机半密钥 R 用 Android Keystore（TEE/StrongBox，不可导出）包裹后存盘
 * 2 组合密钥层：K2 = HKDF-SHA256(第二种子, 设备因子)，数据密钥 K = R ⊕ K2
 * 3 内容加密层：AES-256-GCM 用 K 加密整份文件
 * 只要 R 或 K2 有一个拿不到，文件就是一堆噪音；换设备两样同时没有。
 */
namespace guard {

/** 用第二种子 + 设备因子派生 K2，只留在 native 内存；第二种子由 secret 层内部再派生，不过 Java 层 */
bool start(const std::string &deviceFactor);

/** 拿设备密钥解出的 R 与 K2 异或出数据密钥 K，随后清掉 K2 与 R 副本 */
bool unlock(const std::string &r64hex);

/** 清掉 native 侧全部密钥材料 */
void lock();

/** 是否已解锁 */
bool ready();

/** 生成随机半密钥 R（hex，64 字符） */
std::string randomHex();

/** 当前数据密钥的副本（未解锁返回空），调用方用完自己清 */
std::vector<unsigned char> key();

} //命名空间 guard
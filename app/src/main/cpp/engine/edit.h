#pragma once

#include <string>

namespace edit {

/**
 * 按行定位 old 在 src 里的唯一位置。
 * 返回 "OK\t起始行(0基)\t行数\t原文首行缩进\told首行缩进" 或 "ERR\t原因"。
 * 只做匹配不做替换：替换要拼真实 Unicode 文本，交给 Kotlin 侧做，避免过 JNI 时 4 字节字符被踩坏。
 */
std::string find(const std::string &src, const std::string &oldBlock);

} //命名空间 edit

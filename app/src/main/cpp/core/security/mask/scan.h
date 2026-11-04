#pragma once
// 扫描主循环：PEM → 键值对 → 已知前缀 → 认证头 → JWT → 高熵串 → 原样
#include <string>
#include "core/security/mask/rules.h"

namespace bai::security::mask {

// 按规则扫描整段文本，返回打码后的字符串
std::string run(const std::string &src, const Rules &r);

// 高熵判定：长度够、大小写与数字三类齐全、分隔符占比不高
bool entropy(const std::string &tok, const Rules &r);

} //脱敏扫描
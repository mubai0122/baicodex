#pragma once
// 数据输出脱敏：对外入口。arm64 走 Rust 层，其余 ABI 走本目录的 C++ 实现，行为一致
#include <string>

namespace bai::security::mask {

// 载入规则文本（keys=a,b / prefixes=c,d / auth=Bearer ,Basic  / minlen=N / alnum=N / minval=N / mask=***）
// 成功返回 1；空文本返回 0，保留上一版规则
int rules(const std::string &kv);

// 按当前规则打码；没载入过就走内置兜底
std::string text(const std::string &s);

// 当前规则规模：高 8 位键名数，低 8 位前缀数
int shape();

} //脱敏入口
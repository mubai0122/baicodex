#pragma once
// 扫描游走：跳空白、读出值区、走完整 token，全部按字节推进
#include <cstddef>
#include <string>

namespace bai::base {

// 空白与换行
constexpr const char *kBlank = " \t\r\n";
// 各种引号：JSON / 单引号 / 反引号
constexpr const char *kQuotes = "\"'`";
// 值里允许出现的字符：密文里的符号留住，& ? # 与逗号分号当分隔符
constexpr const char *kVal = "-._:+/=@!$%*()[]{}~^";
// token 里允许出现的字符
constexpr const char *kTok = "-._+/=";

// 从 at 起跳过 set 里的字节
size_t skipSet(const std::string &src, size_t at, const char *set);

// 值区终点：带引号就读到配对引号（不含），否则按 kVal 读到第一个分隔符
size_t valueEnd(const std::string &src, size_t at);

// token 终点：从 at 起连续满足标识符或 kTok 的字节
size_t tokenEnd(const std::string &src, size_t at);

// 是不是 base64url 字符
bool b64url(unsigned char b);

} //扫描游走
/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:46:58
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

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
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
// 掩码写入：标记数量跟着原值长度走但封顶，保证输出永不长于输入
#include <cstddef>
#include <string>

namespace bai::base {

// 一次打码最多写几个标记
constexpr size_t kMaxMarks = 6;
// 掩码串长度上限
constexpr size_t kMaxMarkLen = 16;

// 规范化掩码串：只留可打印 ASCII，长度夹到 1..=16，不合格退回单个星号
std::string normalizeMark(const std::string &mark);

// 把 len 字节的原值替换成掩码推进 out
void pushMask(std::string &out, const std::string &mark, size_t len);

} //掩码写入
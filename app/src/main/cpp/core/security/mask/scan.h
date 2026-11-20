/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:00
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

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
/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:03
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

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

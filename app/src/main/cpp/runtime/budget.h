/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:02
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

#pragma once

#include <cstddef>
#include <string>

namespace baicore {

/** 工具结果治理：超预算时直接截止（只保留头部），不再保留尾部。
 *  未超限时 out=text 且返回 true；超限时 out=硬截断文本且返回 false。 */
bool govern(const std::string &text, size_t maxChars, std::string &out);

} //命名空间 baicore
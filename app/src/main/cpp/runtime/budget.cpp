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

#include "budget.h"

namespace baicore {

bool govern(const std::string &text, size_t maxChars, std::string &out) {
    if (text.size() <= maxChars) {
        out = text;
        return true;
    }
    //预算不够就直接截止：只取头部，不做 head+tail，避免尾部内容干扰模型判断
    out = text.substr(0, maxChars);
    return false;
} //超出预算硬截止

} //命名空间 baicore
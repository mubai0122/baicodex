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

#include "core/base/bytes.h"

namespace bai::base {

bool startsCi(const std::string &src, size_t at, const std::string &pat) {
    if (pat.empty() || at >= src.size() || pat.size() > src.size() - at) return false;
    for (size_t k = 0; k < pat.size(); ++k) {
        if (lower(static_cast<unsigned char>(src[at + k])) != lower(static_cast<unsigned char>(pat[k]))) return false;
    }
    return true;
} //忽略大小写前缀匹配

size_t findSub(const std::string &src, size_t at, const char *sub) {
    if (sub == nullptr || sub[0] == '\0' || at >= src.size()) return std::string::npos;
    return src.find(sub, at);
} //找子串

} //字节工具

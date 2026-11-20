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

#include "core/base/text.h"
#include "core/base/bytes.h"

namespace bai::base {

size_t skipSet(const std::string &src, size_t at, const char *set) {
    while (at < src.size() && inSet(static_cast<unsigned char>(src[at]), set)) ++at;
    return at;
} //跳字符

size_t valueEnd(const std::string &src, size_t at) {
    if (at >= src.size()) return at;
    const char q = src[at];
    if (inSet(static_cast<unsigned char>(q), kQuotes)) {
        size_t j = at + 1;
        while (j < src.size()) {
            if (src[j] == '\\') {
                j += 2; //跳过转义，避免 \" 提前收尾
                continue;
            }
            if (src[j] == q) return j;
            ++j;
        }
        return src.size();
    }
    size_t j = at;
    while (j < src.size()) {
        const unsigned char b = static_cast<unsigned char>(src[j]);
        if (!ident(b) && !inSet(b, kVal)) break;
        ++j;
    }
    return j;
} //读值

size_t tokenEnd(const std::string &src, size_t at) {
    size_t j = at;
    while (j < src.size()) {
        const unsigned char b = static_cast<unsigned char>(src[j]);
        if (!ident(b) && !inSet(b, kTok)) break;
        ++j;
    }
    return j;
} //走 token

bool b64url(unsigned char b) {
    return ident(b) || b == '-' || b == '+' || b == '/' || b == '=';
}

} //扫描游走
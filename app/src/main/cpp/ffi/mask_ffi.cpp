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

#include "core/security/mask/mask.h"
#include <cstring>
#include <string>

#ifndef BAI_RUST
//没有 Rust 的 ABI（armv7 / x86）：导出与 Rust 同名的 C 符号，JNI 层两条路径行为一致
//-fvisibility=hidden 下必须显式标明 default，否则这些符号不进 dynsym
#define BAI_EXPORT extern "C" __attribute__((visibility("default")))

BAI_EXPORT int bai_mask_rules(const unsigned char *p, size_t n) {
    if (p == nullptr || n == 0) return 0;
    return bai::security::mask::rules(std::string(reinterpret_cast<const char *>(p), n));
}

BAI_EXPORT long bai_mask(const unsigned char *s, size_t sl, unsigned char *d, size_t dl) {
    if (s == nullptr || d == nullptr) return -1;
    const std::string out = bai::security::mask::text(std::string(reinterpret_cast<const char *>(s), sl));
    if (out.size() > dl) return -1;
    std::memcpy(d, out.data(), out.size());
    return static_cast<long>(out.size());
}

BAI_EXPORT int bai_mask_shape() {
    return bai::security::mask::shape();
}
#endif
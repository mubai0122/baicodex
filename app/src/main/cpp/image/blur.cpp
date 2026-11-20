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

#include "blur.h"

#include <cstddef>

namespace img {

namespace {

inline int at(int v, int hi) {
    return v < 0 ? 0 : (v > hi ? hi : v);
} //边界夹取

} //匿名命名空间

std::vector<unsigned int> scratch(int w, int h) {
    if (w <= 0 || h <= 0) return {};
    return std::vector<unsigned int>(static_cast<size_t>(w) * h);
} //临时缓冲

void pass(unsigned int *p, unsigned int *tmp, int w, int h, int r) {
    const int div = r * 2 + 1;
    for (int y = 0; y < h; ++y) {
        unsigned int *row = p + static_cast<long>(y) * w;
        unsigned int *out = tmp + static_cast<long>(y) * w;
        int sa = 0, sr = 0, sg = 0, sb = 0;
        for (int i = -r; i <= r; ++i) {
            const unsigned int c = row[at(i, w - 1)];
            sa += static_cast<int>(c >> 24);
            sr += static_cast<int>((c >> 16) & 0xFF);
            sg += static_cast<int>((c >> 8) & 0xFF);
            sb += static_cast<int>(c & 0xFF);
        }
        for (int x = 0; x < w; ++x) {
            out[x] = (static_cast<unsigned int>(sa / div) << 24) |
                     (static_cast<unsigned int>(sr / div) << 16) |
                     (static_cast<unsigned int>(sg / div) << 8) |
                     static_cast<unsigned int>(sb / div);
            const unsigned int add = row[at(x + r + 1, w - 1)];
            const unsigned int sub = row[at(x - r, w - 1)];
            sa += static_cast<int>(add >> 24) - static_cast<int>(sub >> 24);
            sr += static_cast<int>((add >> 16) & 0xFF) - static_cast<int>((sub >> 16) & 0xFF);
            sg += static_cast<int>((add >> 8) & 0xFF) - static_cast<int>((sub >> 8) & 0xFF);
            sb += static_cast<int>(add & 0xFF) - static_cast<int>(sub & 0xFF);
        }
    }
    for (int x = 0; x < w; ++x) {
        int sa = 0, sr = 0, sg = 0, sb = 0;
        for (int i = -r; i <= r; ++i) {
            const unsigned int c = tmp[static_cast<long>(at(i, h - 1)) * w + x];
            sa += static_cast<int>(c >> 24);
            sr += static_cast<int>((c >> 16) & 0xFF);
            sg += static_cast<int>((c >> 8) & 0xFF);
            sb += static_cast<int>(c & 0xFF);
        }
        for (int y = 0; y < h; ++y) {
            p[static_cast<long>(y) * w + x] = (static_cast<unsigned int>(sa / div) << 24) |
                                              (static_cast<unsigned int>(sr / div) << 16) |
                                              (static_cast<unsigned int>(sg / div) << 8) |
                                              static_cast<unsigned int>(sb / div);
            const unsigned int add = tmp[static_cast<long>(at(y + r + 1, h - 1)) * w + x];
            const unsigned int sub = tmp[static_cast<long>(at(y - r, h - 1)) * w + x];
            sa += static_cast<int>(add >> 24) - static_cast<int>(sub >> 24);
            sr += static_cast<int>((add >> 16) & 0xFF) - static_cast<int>((sub >> 16) & 0xFF);
            sg += static_cast<int>((add >> 8) & 0xFF) - static_cast<int>((sub >> 8) & 0xFF);
            sb += static_cast<int>(add & 0xFF) - static_cast<int>(sub & 0xFF);
        }
    }
} //一趟盒式模糊

bool box(unsigned int *p, unsigned int *tmp, int w, int h, int r) {
    if (p == nullptr || tmp == nullptr || w < 2 || h < 2 || r <= 0) return false;
    pass(p, tmp, w, h, r);
    pass(p, tmp, w, h, r);
    return true;
} //两趟模糊

} //命名空间 img

namespace secret {

const char *piece2() {
    return "7a05c93fe1b62d84";
} //第三段

} //命名空间 secret
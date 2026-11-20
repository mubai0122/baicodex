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

#include <vector>

//图像算法：只吃像素指针，不认识 JNI，也不认识 Android 类型
namespace img {

/** 临时缓冲：必须在进入 JNI 临界区之前调用，避免在临界区里申请堆 */
std::vector<unsigned int> scratch(int w, int h);

/** 一趟盒式模糊：横向滑窗写 tmp，纵向滑窗写回 pix；边界夹取，与 Kotlin 版逐像素等价 */
void pass(unsigned int *pix, unsigned int *tmp, int w, int h, int r);

/** 原地两趟盒式模糊近似高斯；参数不合法返回 false */
bool box(unsigned int *pix, unsigned int *tmp, int w, int h, int r);

} //命名空间 img
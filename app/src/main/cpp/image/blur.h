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
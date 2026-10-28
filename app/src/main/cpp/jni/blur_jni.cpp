#include <jni.h>
#include <vector>

namespace {

inline int at(int v, int hi) {
    return v < 0 ? 0 : (v > hi ? hi : v);
} //边界夹取

/** 一趟盒式模糊：横向滑窗写 tmp，纵向滑窗写回 p；边界取夹取，与 Kotlin 版逐像素等价 */
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

} //匿名命名空间

extern "C" {

/** 原地两趟盒式模糊近似高斯：arr 是 ARGB_8888 的像素数组，走临界区不复制；失败返回 false 由 Kotlin 兜底 */
JNIEXPORT jboolean JNICALL
Java_b_ai_core_Blur_nativeBox(JNIEnv *env, jobject, jintArray arr, jint w, jint h, jint r) {
    if (arr == nullptr || w < 2 || h < 2 || r <= 0) return JNI_FALSE;
    const jsize len = env->GetArrayLength(arr);
    if (len < static_cast<jsize>(w) * h) return JNI_FALSE;
    //临时缓冲在临界区外分配：进去之后不碰 JNI、不申请堆，GC 阻塞时间就是纯计算时间
    std::vector<unsigned int> tmp(static_cast<size_t>(w) * h);
    unsigned int *p = static_cast<unsigned int *>(env->GetPrimitiveArrayCritical(arr, nullptr));
    if (p == nullptr) return JNI_FALSE;
    pass(p, tmp.data(), w, h, r);
    pass(p, tmp.data(), w, h, r);
    env->ReleasePrimitiveArrayCritical(arr, p, 0);
    return JNI_TRUE;
} //native 两趟模糊

} //extern "C"

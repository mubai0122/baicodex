#include <jni.h>

#include <vector>

#include "blur.h"

extern "C" {

/** 原地两趟盒式模糊近似高斯：arr 是 ARGB_8888 的像素数组，走临界区不复制；失败返回 false 由 Kotlin 兜底 */
JNIEXPORT jboolean JNICALL
Java_b_ai_core_Blur_nativeBox(JNIEnv *env, jobject, jintArray arr, jint w, jint h, jint r) {
    if (arr == nullptr || w < 2 || h < 2 || r <= 0) return JNI_FALSE;
    const jsize len = env->GetArrayLength(arr);
    if (len < static_cast<jsize>(w) * h) return JNI_FALSE;
    //临时缓冲在临界区外分配：进去之后不碰 JNI、不申请堆，GC 阻塞时间就是纯计算时间
    std::vector<unsigned int> tmp = img::scratch(w, h);
    unsigned int *p = static_cast<unsigned int *>(env->GetPrimitiveArrayCritical(arr, nullptr));
    if (p == nullptr) return JNI_FALSE;
    const bool ok = img::box(p, tmp.data(), w, h, r);
    env->ReleasePrimitiveArrayCritical(arr, p, 0);
    return ok ? JNI_TRUE : JNI_FALSE;
} //native 两趟模糊

} //extern "C"
#include <jni.h>

#include <string>

#include "jni_util.h"

//计算类 ABI（内核的 ffi::memory + ffi::compute）的 JNI 薄壳：
//记忆 / 滑杆几何 / 重试策略全部在 Rust 侧实现，这里只做 jstring 翻译。
//与 kernel_jni.cpp 同一套约定：Rust 缺位时（v7a）返回零值，Kotlin 侧有极小兜底。

using jni::back;
using jni::jstr;

#define BX_CODE_UNAVAILABLE 11

#ifdef BAI_RUST

extern "C" {
char *bx_mem_normalize(const char *raw);
char *bx_mem_dedup(const char *raw);
char *bx_mem_rank(const char *items, const char *query, long long now_ms, int limit);
char *bx_mem_compose(const char *items, const char *mode, int limit, long long now_ms, const char *query);
char *bx_mem_stats(const char *items, long long now_ms);
int bx_mem_kind_long(void);
int bx_mem_kind_normal(void);
float bx_slider_frac(float value, float min, float max);
float bx_slider_x(float frac, float track_w, float thumb_w);
float bx_slider_value(float x, float track_w, float thumb_w, float min, float max, float step);
int bx_retry_classify(int http_code, const char *msg);
int bx_retry_allow(int attempt, int kind);
long long bx_retry_delay(int kind, int attempt);
int bx_retry_max(void);
void bx_free(char *p);
}

namespace {

//Rust 给的 char* 转 jstring 后立刻释放：所有权只跨一次边界
jstring take(JNIEnv *env, char *p) {
    if (p == nullptr) return nullptr;
    jstring out = back(env, std::string(p));
    bx_free(p);
    return out;
} //接走字符串并释放

} //命名空间

extern "C" {

JNIEXPORT jboolean JNICALL
Java_b_ai_native_calc_Calc_nativeAvailable(JNIEnv *, jobject) {
    return JNI_TRUE;
} //计算层是否可用

JNIEXPORT jstring JNICALL
Java_b_ai_native_calc_Calc_nativeNormalize(JNIEnv *env, jobject, jstring raw) {
    return take(env, bx_mem_normalize(jstr(env, raw).c_str()));
} //规范化

JNIEXPORT jstring JNICALL
Java_b_ai_native_calc_Calc_nativeDedup(JNIEnv *env, jobject, jstring raw) {
    return take(env, bx_mem_dedup(jstr(env, raw).c_str()));
} //去重指纹

JNIEXPORT jstring JNICALL
Java_b_ai_native_calc_Calc_nativeRank(JNIEnv *env, jobject, jstring items, jstring query,
                                      jlong nowMs, jint limit) {
    return take(env, bx_mem_rank(jstr(env, items).c_str(), jstr(env, query).c_str(),
                                 static_cast<long long>(nowMs), static_cast<int>(limit)));
} //关键词召回

JNIEXPORT jstring JNICALL
Java_b_ai_native_calc_Calc_nativeCompose(JNIEnv *env, jobject, jstring items, jstring mode,
                                         jint limit, jlong nowMs, jstring query) {
    return take(env, bx_mem_compose(jstr(env, items).c_str(), jstr(env, mode).c_str(),
                                    static_cast<int>(limit), static_cast<long long>(nowMs),
                                    jstr(env, query).c_str()));
} //拼装注入文本

JNIEXPORT jstring JNICALL
Java_b_ai_native_calc_Calc_nativeStats(JNIEnv *env, jobject, jstring items, jlong nowMs) {
    return take(env, bx_mem_stats(jstr(env, items).c_str(), static_cast<long long>(nowMs)));
} //统计

JNIEXPORT jint JNICALL
Java_b_ai_native_calc_Calc_nativeKindLong(JNIEnv *, jobject) {
    return bx_mem_kind_long();
} //长期类型

JNIEXPORT jint JNICALL
Java_b_ai_native_calc_Calc_nativeKindNormal(JNIEnv *, jobject) {
    return bx_mem_kind_normal();
} //普通类型

JNIEXPORT jfloat JNICALL
Java_b_ai_native_calc_Calc_nativeSliderFrac(JNIEnv *, jobject, jfloat value, jfloat min, jfloat max) {
    return bx_slider_frac(value, min, max);
} //值→比例

JNIEXPORT jfloat JNICALL
Java_b_ai_native_calc_Calc_nativeSliderX(JNIEnv *, jobject, jfloat frac, jfloat trackW, jfloat thumbW) {
    return bx_slider_x(frac, trackW, thumbW);
} //比例→中心 x

JNIEXPORT jfloat JNICALL
Java_b_ai_native_calc_Calc_nativeSliderValue(JNIEnv *, jobject, jfloat x, jfloat trackW, jfloat thumbW,
                                             jfloat min, jfloat max, jfloat step) {
    return bx_slider_value(x, trackW, thumbW, min, max, step);
} //手指 x→值

JNIEXPORT jint JNICALL
Java_b_ai_native_calc_Calc_nativeRetryClassify(JNIEnv *env, jobject, jint code, jstring msg) {
    return bx_retry_classify(static_cast<int>(code), jstr(env, msg).c_str());
} //失败分类

JNIEXPORT jint JNICALL
Java_b_ai_native_calc_Calc_nativeRetryAllow(JNIEnv *, jobject, jint attempt, jint kind) {
    return bx_retry_allow(static_cast<int>(attempt), static_cast<int>(kind));
} //是否允许重试

JNIEXPORT jlong JNICALL
Java_b_ai_native_calc_Calc_nativeRetryDelay(JNIEnv *, jobject, jint kind, jint attempt) {
    return static_cast<jlong>(bx_retry_delay(static_cast<int>(kind), static_cast<int>(attempt)));
} //退避时长

JNIEXPORT jint JNICALL
Java_b_ai_native_calc_Calc_nativeRetryMax(JNIEnv *, jobject) {
    return bx_retry_max();
} //重试上限

} //extern "C"

#else //BAI_RUST

//Rust 层没编进来（v7a）：一律返回零值，由 Kotlin 侧兜底实现接管
extern "C" {

JNIEXPORT jboolean JNICALL Java_b_ai_native_calc_Calc_nativeAvailable(JNIEnv *, jobject) { return JNI_FALSE; }
JNIEXPORT jstring JNICALL Java_b_ai_native_calc_Calc_nativeNormalize(JNIEnv *, jobject, jstring) { return nullptr; }
JNIEXPORT jstring JNICALL Java_b_ai_native_calc_Calc_nativeDedup(JNIEnv *, jobject, jstring) { return nullptr; }
JNIEXPORT jstring JNICALL Java_b_ai_native_calc_Calc_nativeRank(JNIEnv *, jobject, jstring, jstring, jlong, jint) { return nullptr; }
JNIEXPORT jstring JNICALL Java_b_ai_native_calc_Calc_nativeCompose(JNIEnv *, jobject, jstring, jstring, jint, jlong, jstring) { return nullptr; }
JNIEXPORT jstring JNICALL Java_b_ai_native_calc_Calc_nativeStats(JNIEnv *, jobject, jstring, jlong) { return nullptr; }
JNIEXPORT jint JNICALL Java_b_ai_native_calc_Calc_nativeKindLong(JNIEnv *, jobject) { return 0; }
JNIEXPORT jint JNICALL Java_b_ai_native_calc_Calc_nativeKindNormal(JNIEnv *, jobject) { return 1; }
JNIEXPORT jfloat JNICALL Java_b_ai_native_calc_Calc_nativeSliderFrac(JNIEnv *, jobject, jfloat, jfloat, jfloat) { return 0.f; }
JNIEXPORT jfloat JNICALL Java_b_ai_native_calc_Calc_nativeSliderX(JNIEnv *, jobject, jfloat, jfloat, jfloat) { return 0.f; }
JNIEXPORT jfloat JNICALL Java_b_ai_native_calc_Calc_nativeSliderValue(JNIEnv *, jobject, jfloat, jfloat, jfloat, jfloat, jfloat, jfloat) { return 0.f; }
JNIEXPORT jint JNICALL Java_b_ai_native_calc_Calc_nativeRetryClassify(JNIEnv *, jobject, jint, jstring) { return 0; }
JNIEXPORT jint JNICALL Java_b_ai_native_calc_Calc_nativeRetryAllow(JNIEnv *, jobject, jint, jint) { return 0; }
JNIEXPORT jlong JNICALL Java_b_ai_native_calc_Calc_nativeRetryDelay(JNIEnv *, jobject, jint, jint) { return 0; }
JNIEXPORT jint JNICALL Java_b_ai_native_calc_Calc_nativeRetryMax(JNIEnv *, jobject) { return 0; }

} //extern "C"

#endif //BAI_RUST
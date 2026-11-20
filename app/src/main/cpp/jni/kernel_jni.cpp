/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:01
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

#include <jni.h>

#include <string>

#include "jni_util.h"

//Rust 内核（内核的 ffi::store）的 JNI 薄壳：
//只做 jstring ↔ const char* 与 jlong ↔ 句柄指针的翻译，业务判断一律在 Rust 侧。
//Rust 那层自己管错误（bx_last_error / bx_last_code），这里不做任何兜底语义。

using jni::back;
using jni::jstr;

#ifdef BAI_RUST

extern "C" {
//不透明句柄：真正定义在 Rust 侧，这里只拿指针
struct bx_handle;
struct bx_handle *bx_open_default(const char *root);
void bx_close(struct bx_handle *h);
char *bx_get(struct bx_handle *h, const char *bucket, const char *name);
int bx_put(struct bx_handle *h, const char *bucket, const char *name, const char *value);
int bx_del(struct bx_handle *h, const char *bucket, const char *name);
char *bx_snapshot(struct bx_handle *h, const char *bucket);
char *bx_buckets(struct bx_handle *h);
char *bx_prefs_get(struct bx_handle *h, const char *page, const char *key);
int bx_prefs_set(struct bx_handle *h, const char *page, const char *key, const char *json);
char *bx_prefs_page(struct bx_handle *h, const char *page);
char *bx_selfcheck(const char *root);
const char *bx_version(void);
const char *bx_last_error(void);
int bx_last_code(void);
void bx_free(char *p);
}

//错误码：11 = 这个 ABI 没把 Rust 编进来（与 Kotlin 侧 Code.UNAVAILABLE 对齐）
#define BX_CODE_UNAVAILABLE 11

namespace {

inline struct bx_handle *H(jlong h) {
    return reinterpret_cast<struct bx_handle *>(static_cast<intptr_t>(h));
} //jlong → 句柄

//Rust 给的 char* 转成 jstring 后立刻交还给 bx_free：所有权只跨一次边界
jstring take(JNIEnv *env, char *p) {
    if (p == nullptr) return nullptr;
    jstring out = back(env, std::string(p));
    bx_free(p);
    return out;
} //接走字符串并释放

} //命名空间

extern "C" {

JNIEXPORT jboolean JNICALL
Java_b_ai_native_security_BxKernel_nativeAvailable(JNIEnv *, jobject) {
    return JNI_TRUE;
} //内核是否可用

JNIEXPORT jlong JNICALL
Java_b_ai_native_security_BxKernel_nativeOpenDefault(JNIEnv *env, jobject, jstring root) {
    return static_cast<jlong>(reinterpret_cast<intptr_t>(bx_open_default(jstr(env, root).c_str())));
} //打开（内核自管主密钥）

JNIEXPORT void JNICALL
Java_b_ai_native_security_BxKernel_nativeClose(JNIEnv *, jobject, jlong h) {
    if (h != 0) bx_close(H(h));
} //关闭

JNIEXPORT jstring JNICALL
Java_b_ai_native_security_BxKernel_nativeGet(JNIEnv *env, jobject, jlong h, jstring bucket, jstring name) {
    if (h == 0) return nullptr;
    return take(env, bx_get(H(h), jstr(env, bucket).c_str(), jstr(env, name).c_str()));
} //读凭据

JNIEXPORT jint JNICALL
Java_b_ai_native_security_BxKernel_nativePut(JNIEnv *env, jobject, jlong h, jstring bucket,
                                             jstring name, jstring value) {
    if (h == 0) return BX_CODE_UNAVAILABLE;
    return bx_put(H(h), jstr(env, bucket).c_str(), jstr(env, name).c_str(), jstr(env, value).c_str());
} //写凭据

JNIEXPORT jint JNICALL
Java_b_ai_native_security_BxKernel_nativeDel(JNIEnv *env, jobject, jlong h, jstring bucket,
                                             jstring name) {
    if (h == 0) return BX_CODE_UNAVAILABLE;
    return bx_del(H(h), jstr(env, bucket).c_str(), jstr(env, name).c_str());
} //删凭据

JNIEXPORT jstring JNICALL
Java_b_ai_native_security_BxKernel_nativeSnapshot(JNIEnv *env, jobject, jlong h, jstring bucket) {
    if (h == 0) return nullptr;
    return take(env, bx_snapshot(H(h), jstr(env, bucket).c_str()));
} //桶快照

JNIEXPORT jstring JNICALL
Java_b_ai_native_security_BxKernel_nativeBuckets(JNIEnv *env, jobject, jlong h) {
    if (h == 0) return nullptr;
    return take(env, bx_buckets(H(h)));
} //桶列表

JNIEXPORT jstring JNICALL
Java_b_ai_native_security_BxKernel_nativePrefsGet(JNIEnv *env, jobject, jlong h, jstring page,
                                                  jstring key) {
    if (h == 0) return nullptr;
    return take(env, bx_prefs_get(H(h), jstr(env, page).c_str(), jstr(env, key).c_str()));
} //读配置

JNIEXPORT jint JNICALL
Java_b_ai_native_security_BxKernel_nativePrefsSet(JNIEnv *env, jobject, jlong h, jstring page,
                                                  jstring key, jstring json) {
    if (h == 0) return BX_CODE_UNAVAILABLE;
    return bx_prefs_set(H(h), jstr(env, page).c_str(), jstr(env, key).c_str(), jstr(env, json).c_str());
} //写配置

JNIEXPORT jstring JNICALL
Java_b_ai_native_security_BxKernel_nativePrefsPage(JNIEnv *env, jobject, jlong h, jstring page) {
    if (h == 0) return nullptr;
    return take(env, bx_prefs_page(H(h), jstr(env, page).c_str()));
} //整页配置

JNIEXPORT jstring JNICALL
Java_b_ai_native_security_BxKernel_nativeSelfcheck(JNIEnv *env, jobject, jstring root) {
    return take(env, bx_selfcheck(jstr(env, root).c_str()));
} //自检

JNIEXPORT jstring JNICALL
Java_b_ai_native_security_BxKernel_nativeVersion(JNIEnv *env, jobject) {
    const char *v = bx_version();
    return v == nullptr ? nullptr : back(env, v);
} //内核版本

JNIEXPORT jstring JNICALL
Java_b_ai_native_security_BxKernel_nativeLastError(JNIEnv *env, jobject) {
    const char *e = bx_last_error();
    return e == nullptr ? back(env, "") : back(env, e);
} //最近错误文本

JNIEXPORT jint JNICALL
Java_b_ai_native_security_BxKernel_nativeLastCode(JNIEnv *, jobject) {
    return bx_last_code();
} //最近错误码

} //extern "C"

#else //BAI_RUST

//非 arm64 或构建时没找到 cargo：Rust 层没编进来，所有入口一律返回「不可用」。
//不静默降级去写另一份数据 —— 那会造出第三个真相来源。

extern "C" {

JNIEXPORT jboolean JNICALL
Java_b_ai_native_security_BxKernel_nativeAvailable(JNIEnv *, jobject) {
    return JNI_FALSE;
} //内核是否可用

JNIEXPORT jlong JNICALL
Java_b_ai_native_security_BxKernel_nativeOpenDefault(JNIEnv *, jobject, jstring) {
    return 0;
} //打开（不可用）

JNIEXPORT void JNICALL
Java_b_ai_native_security_BxKernel_nativeClose(JNIEnv *, jobject, jlong) { } //关闭（空实现）

JNIEXPORT jstring JNICALL
Java_b_ai_native_security_BxKernel_nativeGet(JNIEnv *, jobject, jlong, jstring, jstring) {
    return nullptr;
} //读凭据（不可用）

JNIEXPORT jint JNICALL
Java_b_ai_native_security_BxKernel_nativePut(JNIEnv *, jobject, jlong, jstring, jstring, jstring) {
    return 11;
} //写凭据（不可用）

JNIEXPORT jint JNICALL
Java_b_ai_native_security_BxKernel_nativeDel(JNIEnv *, jobject, jlong, jstring, jstring) {
    return 11;
} //删凭据（不可用）

JNIEXPORT jstring JNICALL
Java_b_ai_native_security_BxKernel_nativeSnapshot(JNIEnv *, jobject, jlong, jstring) {
    return nullptr;
} //桶快照（不可用）

JNIEXPORT jstring JNICALL
Java_b_ai_native_security_BxKernel_nativeBuckets(JNIEnv *, jobject, jlong) {
    return nullptr;
} //桶列表（不可用）

JNIEXPORT jstring JNICALL
Java_b_ai_native_security_BxKernel_nativePrefsGet(JNIEnv *, jobject, jlong, jstring, jstring) {
    return nullptr;
} //读配置（不可用）

JNIEXPORT jint JNICALL
Java_b_ai_native_security_BxKernel_nativePrefsSet(JNIEnv *, jobject, jlong, jstring, jstring,
                                                  jstring) {
    return 11;
} //写配置（不可用）

JNIEXPORT jstring JNICALL
Java_b_ai_native_security_BxKernel_nativePrefsPage(JNIEnv *, jobject, jlong, jstring) {
    return nullptr;
} //整页配置（不可用）

JNIEXPORT jstring JNICALL
Java_b_ai_native_security_BxKernel_nativeSelfcheck(JNIEnv *, jobject, jstring) {
    return nullptr;
} //自检（不可用）

JNIEXPORT jstring JNICALL
Java_b_ai_native_security_BxKernel_nativeVersion(JNIEnv *, jobject) {
    return nullptr;
} //内核版本（不可用）

JNIEXPORT jstring JNICALL
Java_b_ai_native_security_BxKernel_nativeLastError(JNIEnv *env, jobject) {
    return back(env, "这个 ABI 没把 Rust 内核编进来（只编 arm64-v8a）");
} //最近错误文本

JNIEXPORT jint JNICALL
Java_b_ai_native_security_BxKernel_nativeLastCode(JNIEnv *, jobject) {
    return 11;
} //最近错误码

} //extern "C"

#endif //BAI_RUST
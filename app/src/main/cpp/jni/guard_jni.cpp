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

#include "guard.h"
#include "jni_util.h"

using jni::back;
using jni::jstr;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_b_ai_native_security_Guard_nativeStart(JNIEnv *env, jobject, jstring dev) {
    return guard::start(jni::jstr(env, dev)) ? JNI_TRUE : JNI_FALSE;
} //派生组合半密钥

JNIEXPORT jboolean JNICALL
Java_b_ai_native_security_Guard_nativeUnlock(JNIEnv *env, jobject, jstring r64) {
    return guard::unlock(jstr(env, r64)) ? JNI_TRUE : JNI_FALSE;
} //异或组合成数据密钥

JNIEXPORT void JNICALL
Java_b_ai_native_security_Guard_nativeLock(JNIEnv *, jobject) {
    guard::lock();
} //清掉 native 侧密钥

JNIEXPORT jboolean JNICALL
Java_b_ai_native_security_Guard_nativeReady(JNIEnv *, jobject) {
    return guard::ready() ? JNI_TRUE : JNI_FALSE;
} //是否已解锁

JNIEXPORT jstring JNICALL
Java_b_ai_native_security_Guard_nativeRandom(JNIEnv *env, jobject) {
    return back(env, guard::randomHex());
} //随机半密钥

} //extern "C"
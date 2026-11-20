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

#include <jni.h>
#include "jni/jni_util.h"
#include "core/security/mask/mask.h"

using jni::back;
using jni::jstr;

extern "C" {
JNIEXPORT jboolean JNICALL
Java_b_ai_native_security_Mask_nativeRules(JNIEnv *env, jobject, jstring kv) {
    return bai::security::mask::rules(jstr(env, kv)) == 1 ? JNI_TRUE : JNI_FALSE;
} //载入脱敏规则

JNIEXPORT jstring JNICALL
Java_b_ai_native_security_Mask_nativeMask(JNIEnv *env, jobject, jstring s) {
    return back(env, bai::security::mask::text(jstr(env, s)));
} //文本脱敏

JNIEXPORT jint JNICALL
Java_b_ai_native_security_Mask_nativeShape(JNIEnv *, jobject) {
    return bai::security::mask::shape();
} //规则规模

} //extern "C"
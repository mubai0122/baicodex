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
#include "secret.h"

using jni::back;
using jni::jstr;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_b_ai_native_security_Vault_nativeSave(JNIEnv *env, jobject, jstring path, jstring text) {
    return secret::save(jstr(env, path), jstr(env, text)) ? JNI_TRUE : JNI_FALSE;
} //加密后原子落盘

JNIEXPORT jstring JNICALL
Java_b_ai_native_security_Vault_nativeLoad(JNIEnv *env, jobject, jstring path) {
    return back(env, secret::load(jstr(env, path)));
} //读盘并解密，失败返回空串

JNIEXPORT jstring JNICALL
Java_b_ai_native_security_Vault_nativeFingerprint(JNIEnv *env, jobject) {
    return back(env, secret::fingerprint());
} //当前密钥指纹

} //extern "C"
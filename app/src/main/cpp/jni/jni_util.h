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

#pragma once

#include <jni.h>

#include <string>

//JNI 层唯一允许存在的「公共胶水」：字符串进出，不含任何业务算法
namespace jni {

inline std::string jstr(JNIEnv *env, jstring js) {
    if (js == nullptr) return "";
    const char *p = env->GetStringUTFChars(js, nullptr);
    std::string out = p == nullptr ? "" : p;
    if (p != nullptr) env->ReleaseStringUTFChars(js, p);
    return out;
} //jstring → std::string

inline jstring back(JNIEnv *env, const std::string &s) {
    return env->NewStringUTF(s.c_str());
} //std::string → jstring

} //命名空间 jni

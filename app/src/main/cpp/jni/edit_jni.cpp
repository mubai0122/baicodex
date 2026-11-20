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

#include "edit.h"
#include "jni_util.h"

using jni::back;
using jni::jstr;

extern "C" {

JNIEXPORT jstring JNICALL
Java_b_ai_native_text_TextEdit_nativeFind(JNIEnv *env, jobject, jstring src, jstring oldBlock) {
    return back(env, edit::find(jstr(env, src), jstr(env, oldBlock)));
} //按行定位，只回行号与缩进，不回文本

} //extern "C"
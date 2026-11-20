/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 22:51:00
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
#include <vector>

#include "core/feature/archive/tarbz2.h"
#include "jni_util.h"

using jni::jstr;

extern "C" {

//Kotlin: b.ai.native.archive.Bz2tar.nativeExtract(src, dstDir, names)
//只做参数翻译，解压逻辑全在 core/feature/archive/tarbz2.cpp
JNIEXPORT jint JNICALL
Java_b_ai_native_archive_Bz2tar_nativeExtract(JNIEnv *env, jobject, jstring src, jstring dstDir, jobjectArray names) {
    std::string s = jstr(env, src);
    std::string d = jstr(env, dstDir);
    std::vector<std::string> list;
    jsize n = names == nullptr ? 0 : env->GetArrayLength(names);
    list.reserve(static_cast<size_t>(n < 0 ? 0 : n));
    for (jsize i = 0; i < n; i++) {
        auto item = static_cast<jstring>(env->GetObjectArrayElement(names, i));
        list.push_back(jstr(env, item));
        if (item != nullptr) env->DeleteLocalRef(item);
    } //逐个取文件名
    std::vector<const char *> raw;
    raw.reserve(list.size());
    for (const auto &x : list) raw.push_back(x.c_str());
    return bai_tar_bz2_extract(s.c_str(), d.c_str(), raw.data(), static_cast<int>(raw.size()));
} //解压 tar.bz2（按名字挑文件）

} //extern "C"
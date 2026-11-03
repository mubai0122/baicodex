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

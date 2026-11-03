#include <jni.h>

#include <string>

#include "edit.h"

namespace {

std::string jstr(JNIEnv *env, jstring js) {
    if (js == nullptr) return "";
    const char *p = env->GetStringUTFChars(js, nullptr);
    std::string out = p == nullptr ? "" : p;
    if (p != nullptr) env->ReleaseStringUTFChars(js, p);
    return out;
} //jstring → std::string（只用来比较，不往外回写）

jstring back(JNIEnv *env, const std::string &s) {
    return env->NewStringUTF(s.c_str());
} //std::string → jstring

} //匿名命名空间

extern "C" {

JNIEXPORT jstring JNICALL
Java_b_ai_core_TextEdit_nativeFind(JNIEnv *env, jobject, jstring src, jstring oldBlock) {
    return back(env, edit::find(jstr(env, src), jstr(env, oldBlock)));
} //按行定位，只回行号与缩进，不回文本

} //extern "C"
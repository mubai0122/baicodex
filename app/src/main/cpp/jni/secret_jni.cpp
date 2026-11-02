#include <jni.h>

#include <string>

#include "secret.h"

namespace {

std::string jstr(JNIEnv *env, jstring js) {
    if (js == nullptr) return "";
    const char *p = env->GetStringUTFChars(js, nullptr);
    std::string out = p == nullptr ? "" : p;
    if (p != nullptr) env->ReleaseStringUTFChars(js, p);
    return out;
} //jstring → std::string

jstring back(JNIEnv *env, const std::string &s) {
    return env->NewStringUTF(s.c_str());
} //std::string → jstring

} //匿名命名空间

extern "C" {

JNIEXPORT jboolean JNICALL
Java_b_ai_core_Vault_nativeSave(JNIEnv *env, jobject, jstring path, jstring text) {
    return secret::save(jstr(env, path), jstr(env, text)) ? JNI_TRUE : JNI_FALSE;
} //加密后原子落盘

JNIEXPORT jstring JNICALL
Java_b_ai_core_Vault_nativeLoad(JNIEnv *env, jobject, jstring path) {
    return back(env, secret::load(jstr(env, path)));
} //读盘并解密，失败返回空串

JNIEXPORT jstring JNICALL
Java_b_ai_core_Vault_nativeFingerprint(JNIEnv *env, jobject) {
    return back(env, secret::fingerprint());
} //当前密钥指纹

} //extern "C"
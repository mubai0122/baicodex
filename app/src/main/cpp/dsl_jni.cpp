#include <jni.h>

#include <string>

#include "dsl.h"

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

/**统一返回 "OK\n<文本>" 或 "ERR\n<错误>"，Kotlin 侧只看第一行 */
std::string pack(const dsl::Result &r) {
    return (r.ok ? std::string("OK\n") : std::string("ERR\n")) + r.text;
} //打包返回

} //匿名命名空间

extern "C" {

JNIEXPORT jstring JNICALL
Java_b_ai_core_Dsl_nativeLint(JNIEnv *env, jobject, jstring tpl, jstring kind, jstring params) {
    dsl::Table p;
    if (!dsl::parseFlatJson(jstr(env, params), p)) return back(env, "ERR\n参数表不是合法 JSON");
    const dsl::Result r = dsl::lint(jstr(env, tpl), jstr(env, kind), p, nullptr);
    return back(env, pack(r));
} //加载期校验

JNIEXPORT jstring JNICALL
Java_b_ai_core_Dsl_nativeRender(JNIEnv *env, jobject, jstring tpl, jstring kind, jstring params,
                                jstring secrets, jstring args) {
    dsl::Table p, s, a;
    if (!dsl::parseFlatJson(jstr(env, params), p)) return back(env, "ERR\n参数表不是合法 JSON");
    if (!dsl::parseFlatJson(jstr(env, secrets), s)) return back(env, "ERR\nsecret 表不是合法 JSON");
    if (!dsl::parseFlatJson(jstr(env, args), a)) return back(env, "ERR\n调用参数不是合法 JSON");
    const dsl::Result r = dsl::render(jstr(env, tpl), jstr(env, kind), p, s, a);
    return back(env, pack(r));
} //校验并生成

} // extern "C"
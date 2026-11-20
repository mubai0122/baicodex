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

#include <string>

#include "budget.h"
#include "dsl.h"
#include "jni_util.h"

using jni::back;
using jni::jstr;

namespace {

/**统一返回 "OK\n<文本>" 或 "ERR\n<错误>"，Kotlin 侧只看第一行 */
std::string pack(const dsl::Result &r) {
    return (r.ok ? std::string("OK\n") : std::string("ERR\n")) + r.text;
} //打包返回

} //匿名命名空间

extern "C" {

JNIEXPORT jstring JNICALL
Java_b_ai_native_script_Dsl_nativeLint(JNIEnv *env, jobject, jstring tpl, jstring kind, jstring params) {
    dsl::Table p;
    if (!dsl::parseFlatJson(jstr(env, params), p)) return back(env, "ERR\n参数表不是合法 JSON");
    const dsl::Result r = dsl::lint(jstr(env, tpl), jstr(env, kind), p, nullptr);
    return back(env, pack(r));
} //加载期校验

/** 工具结果治理：超预算直接截止，返回 "OK\n<处理后文本>"；引擎未就绪由 Kotlin 侧兜底 */
JNIEXPORT jstring JNICALL
Java_b_ai_native_script_Dsl_nativeGovern(JNIEnv *env, jobject, jstring text, jint maxChars) {
    std::string t = jstr(env, text);
    std::string out;
    baicore::govern(t, static_cast<size_t>(maxChars), out);
    return back(env, std::string("OK\n") + out);
} //工具结果治理

JNIEXPORT jstring JNICALL
Java_b_ai_native_script_Dsl_nativeRender(JNIEnv *env, jobject, jstring tpl, jstring kind, jstring params,
                                jstring secrets, jstring args) {
    dsl::Table p, s, a;
    if (!dsl::parseFlatJson(jstr(env, params), p)) return back(env, "ERR\n参数表不是合法 JSON");
    if (!dsl::parseFlatJson(jstr(env, secrets), s)) return back(env, "ERR\nsecret 表不是合法 JSON");
    if (!dsl::parseFlatJson(jstr(env, args), a)) return back(env, "ERR\n调用参数不是合法 JSON");
    const dsl::Result r = dsl::render(jstr(env, tpl), jstr(env, kind), p, s, a);
    return back(env, pack(r));
} //校验并生成

} // extern "C"
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
#include <vector>
#include "core/feature/llm/llm.h"
namespace {
struct Cb {
    JNIEnv *env = nullptr;
    jobject obj = nullptr;
    jmethodID mid = nullptr;
    bool dead = false;
};
void onTok(void *ud, const char *s) {
    auto *c = static_cast<Cb *>(ud);
    if (c->dead) return;
    jstring js = c->env->NewStringUTF(s != nullptr ? s : "");
    if (js == nullptr) {
        c->env->ExceptionClear();
        c->dead = true; //新建字符串失败（多为 OOM），停掉回调别再撞
        return;
    }
    c->env->CallVoidMethod(c->obj, c->mid, js);
    c->env->DeleteLocalRef(js);
    if (c->env->ExceptionCheck()) {
        c->env->ExceptionClear();
        c->dead = true; //Kotlin 侧回调里抛了异常，吞掉并终止本轮
    }
} //单 token 回调：C 字符串直接转 jstring 交给 Kotlin
std::string toStd(JNIEnv *env, jstring s) {
    if (s == nullptr) return "";
    const char *raw = env->GetStringUTFChars(s, nullptr);
    if (raw == nullptr) {
        env->ExceptionClear();
        return "";
    }
    std::string out(raw);
    env->ReleaseStringUTFChars(s, raw);
    return out;
} //jstring 转 std::string
std::string fromArr(JNIEnv *env, jobjectArray arr, int i) {
    auto s = static_cast<jstring>(env->GetObjectArrayElement(arr, i));
    std::string out = toStd(env, s);
    if (s != nullptr) env->DeleteLocalRef(s);
    return out;
} //从字符串数组取第 i 项

/**
 * 找 token 回调方法 `void on(String)`。
 * release 包开 R8，`Llama$OnToken` 这个 fun interface 的方法名可能被改名（混淆不认 JNI 字符串），
 * 所以先按名字找，找不到再用反射按签名兜底：参数唯一且是 String、返回 void。
 */
jmethodID findOn(JNIEnv *env, jclass cls) {
    jmethodID m = env->GetMethodID(cls, "on", "(Ljava/lang/String;)V");
    if (m != nullptr) return m;
    env->ExceptionClear();
    //反射兜底：遍历方法，按签名挑唯一一个 void(String)
    jclass clsCls = env->FindClass("java/lang/Class");
    if (clsCls == nullptr) {
        env->ExceptionClear();
        return nullptr;
    }
    jmethodID mMethods = env->GetMethodID(clsCls, "getMethods", "()[Ljava/lang/reflect/Method;");
    jmethodID mName = nullptr;
    jmethodID mParams = nullptr;
    if (mMethods != nullptr) {
        jclass mCls = env->FindClass("java/lang/reflect/Method");
        if (mCls != nullptr) {
            mName = env->GetMethodID(mCls, "getName", "()Ljava/lang/String;");
            mParams = env->GetMethodID(mCls, "getParameterTypes", "()[Ljava/lang/Class;");
            env->DeleteLocalRef(mCls);
        } else {
            env->ExceptionClear();
        }
    }
    if (mMethods == nullptr || mName == nullptr || mParams == nullptr) {
        env->ExceptionClear();
        return nullptr;
    }
    auto all = static_cast<jobjectArray>(env->CallObjectMethod(cls, mMethods));
    jmethodID found = nullptr;
    if (all != nullptr && !env->ExceptionCheck()) {
        const jsize n = env->GetArrayLength(all);
        for (jsize i = 0; i < n && found == nullptr; ++i) {
            auto mi = static_cast<jobject>(env->GetObjectArrayElement(all, i));
            if (mi == nullptr) {
                env->ExceptionClear();
                continue;
            }
            auto types = static_cast<jobjectArray>(env->CallObjectMethod(mi, mParams));
            const jsize np = types != nullptr ? env->GetArrayLength(types) : 0; //只要恰好 1 个参数
            bool hit = np == 1;
            if (hit) {
                auto t = static_cast<jclass>(env->GetObjectArrayElement(types, 0));
                if (t != nullptr) {
                    jclass sCls = env->FindClass("java/lang/String"); //参数类型必须是 String
                    if (sCls != nullptr) hit = env->IsSameObject(t, sCls);
                    env->ExceptionClear();
                    env->DeleteLocalRef(sCls);
                    env->DeleteLocalRef(t);
                } else {
                    hit = false;
                    env->ExceptionClear();
                }
            }
            if (types != nullptr) env->DeleteLocalRef(types);
            if (hit) {
                auto nm = static_cast<jstring>(env->CallObjectMethod(mi, mName));
                if (nm != nullptr) {
                    std::string name = toStd(env, nm);
                    env->DeleteLocalRef(nm);
                    found = env->GetMethodID(cls, name.c_str(), "(Ljava/lang/String;)V");
                    if (found == nullptr) env->ExceptionClear();
                } else {
                    env->ExceptionClear();
                }
            }
            env->DeleteLocalRef(mi);
        }
        env->DeleteLocalRef(all);
    } else {
        env->ExceptionClear();
    }
    env->DeleteLocalRef(clsCls);
    return found;
} //按名找 on(String)，失败则反射按签名兜底
} //namespace

extern "C" JNIEXPORT jlong JNICALL
Java_b_ai_native_llm_Llama_nativeLoad(JNIEnv *env, jobject, jstring path, jint threads, jint gpuLayers) {
    const std::string p = toStd(env, path);
    return static_cast<jlong>(bai_llm_load(p.c_str(), threads, gpuLayers));
} //加载模型
extern "C" JNIEXPORT void JNICALL
Java_b_ai_native_llm_Llama_nativeFree(JNIEnv *, jobject, jlong h) {
    bai_llm_free(static_cast<uint64_t>(h));
} //释放模型
extern "C" JNIEXPORT jstring JNICALL
Java_b_ai_native_llm_Llama_nativeMeta(JNIEnv *env, jobject, jlong h) {
    return env->NewStringUTF(bai_llm_meta(static_cast<uint64_t>(h)));
} //模型元信息（key=value 多行）
extern "C" JNIEXPORT jstring JNICALL
Java_b_ai_native_llm_Llama_nativeErr(JNIEnv *env, jobject) {
    return env->NewStringUTF(bai_llm_err());
} //最近一次失败原因
extern "C" JNIEXPORT jlong JNICALL
Java_b_ai_native_llm_Llama_nativeCtxNew(JNIEnv *, jobject, jlong h, jint ctxLen) {
    return static_cast<jlong>(bai_llm_ctx_new(static_cast<uint64_t>(h), ctxLen));
} //建推理上下文
extern "C" JNIEXPORT void JNICALL
Java_b_ai_native_llm_Llama_nativeCtxFree(JNIEnv *, jobject, jlong c) {
    bai_llm_ctx_free(static_cast<uint64_t>(c));
} //释放推理上下文
extern "C" JNIEXPORT void JNICALL
Java_b_ai_native_llm_Llama_nativeSetSampling(JNIEnv *, jobject, jlong c, jfloat temp, jfloat topP, jint topK,
                                             jfloat repeatPenalty, jint seed) {
    bai_llm_set_sampling(static_cast<uint64_t>(c), temp, topP, topK, repeatPenalty, seed);
} //设置采样参数
extern "C" JNIEXPORT jint JNICALL
Java_b_ai_native_llm_Llama_nativeRun(JNIEnv *env, jobject, jlong c, jstring prompt, jint maxTokens, jobject cb) {
    const std::string p = toStd(env, prompt);
    if (cb == nullptr) return bai_llm_run(static_cast<uint64_t>(c), p.c_str(), maxTokens, nullptr, nullptr);
    Cb ctx{};
    ctx.env = env;
    ctx.obj = cb;
    jclass cls = env->GetObjectClass(cb);
    ctx.mid = findOn(env, cls); //混淆过的包按名字找不到，内部已按签名兜底
    env->DeleteLocalRef(cls);
    if (ctx.mid == nullptr) {
        env->ExceptionClear();
        return -5; //-5 表示回调接口不可用，跟 -1（参数错）区分开，方便 UI 说人话
    }
    return bai_llm_run(static_cast<uint64_t>(c), p.c_str(), maxTokens, onTok, &ctx);
} //跑一轮生成：token 逐个回调给 Kotlin
extern "C" JNIEXPORT void JNICALL
Java_b_ai_native_llm_Llama_nativeCancel(JNIEnv *, jobject, jlong c) {
    bai_llm_cancel(static_cast<uint64_t>(c));
} //请求取消生成
extern "C" JNIEXPORT jstring JNICALL
Java_b_ai_native_llm_Llama_nativeChat(JNIEnv *env, jobject, jlong h, jobjectArray roles, jobjectArray texts,
                                      jboolean addAss) {
    if (roles == nullptr || texts == nullptr) return nullptr;
    const jsize n = env->GetArrayLength(roles);
    if (n <= 0 || env->GetArrayLength(texts) < n) return nullptr;
    std::vector<std::string> rs;
    std::vector<std::string> ts;
    rs.reserve(static_cast<size_t>(n));
    ts.reserve(static_cast<size_t>(n));
    for (jsize i = 0; i < n; ++i) {
        rs.push_back(fromArr(env, roles, i));
        ts.push_back(fromArr(env, texts, i));
    }
    std::vector<const char *> rp;
    std::vector<const char *> tp;
    rp.reserve(static_cast<size_t>(n));
    tp.reserve(static_cast<size_t>(n));
    for (jsize i = 0; i < n; ++i) {
        rp.push_back(rs[static_cast<size_t>(i)].c_str());
        tp.push_back(ts[static_cast<size_t>(i)].c_str());
    }
    //两段式：先问需要多少字节，再按结果一次分配（探长度时给个 1 字节落点，避免实现里裸 memcpy 到空指针）
    char probe = 0;
    int need = bai_llm_chat(static_cast<uint64_t>(h), rp.data(), tp.data(), n, addAss ? 1 : 0, &probe, 0);
    if (need < 0) return nullptr;
    std::string out(static_cast<size_t>(need) + 1, '\0');
    const int got = bai_llm_chat(static_cast<uint64_t>(h), rp.data(), tp.data(), n, addAss ? 1 : 0, &out[0], need);
    if (got < 0) return nullptr;
    out.resize(static_cast<size_t>(got));
    return env->NewStringUTF(out.c_str());
} //按 chat template 拼 prompt
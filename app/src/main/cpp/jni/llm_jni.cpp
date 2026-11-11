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
    ctx.mid = env->GetMethodID(cls, "on", "(Ljava/lang/String;)V");
    env->DeleteLocalRef(cls);
    if (ctx.mid == nullptr) {
        env->ExceptionClear();
        return -1;
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
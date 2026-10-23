#include <jni.h>

#include <string>

#include "http.h"

namespace {
std::string gErr; //最近一次失败原因，Kotlin 侧想看就看
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_b_ai_net_Net_nativeGet(JNIEnv* env, jobject, jstring url, jint timeoutMs) {
    if (url == nullptr) return nullptr;
    const char* raw = env->GetStringUTFChars(url, nullptr);
    if (raw == nullptr) return nullptr;
    std::string u(raw);
    env->ReleaseStringUTFChars(url, raw);

    net::Resp r = net::get(u, timeoutMs > 0 ? timeoutMs : 6000);
    if (r.code < 200 || r.code >= 300 || r.body.empty()) {
        gErr = r.err.empty() ? ("http " + std::to_string(r.code)) : r.err;
        return nullptr;
    }
    gErr.clear();
    auto arr = env->NewByteArray(static_cast<jsize>(r.body.size()));
    if (arr == nullptr) return nullptr;
    env->SetByteArrayRegion(arr, 0, static_cast<jsize>(r.body.size()),
                            reinterpret_cast<const jbyte*>(r.body.data()));
    return arr;
} //C层 HTTPS GET：成功给字节，失败给 null

extern "C" JNIEXPORT jstring JNICALL
Java_b_ai_net_Net_nativeErr(JNIEnv* env, jobject) {
    return env->NewStringUTF(gErr.c_str());
} //最近一次失败原因
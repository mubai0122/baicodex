#include <jni.h>
#include <string>
#include "qjs.h"
namespace {
JavaVM *gVm = nullptr;
/**一个句柄就是一个长会话：运行时 + 宿主工具桥，Kotlin 侧负责复用与加锁 */
struct Holder {
    jsrt::Engine eng;
    jobject bridge = nullptr;
    jmethodID callMid = nullptr;
};
std::string jstr(JNIEnv *env, jstring js) {
    if (js == nullptr) return "";
    const char *p = env->GetStringUTFChars(js, nullptr);
    std::string out = p == nullptr ? "" : p;
    if (p != nullptr) env->ReleaseStringUTFChars(js, p);
    return out;
} //jstring → std::string
jstring back(JNIEnv *env, const std::string &s) { return env->NewStringUTF(s.c_str()); }
std::string packResult(const jsrt::Result &r) {
    return (r.ok ? std::string("OK\n") : std::string("ERR\n")) + r.text;
} //打包返回
/**调 Kotlin 的 JsBridge.call：工具名 + JSON → 结果文本 */
std::string callBridge(JNIEnv *env, Holder *h, const std::string &name, const std::string &args) {
    if (h->bridge == nullptr || h->callMid == nullptr) return "ERR\n宿主工具桥没接上";
    jstring jn = env->NewStringUTF(name.c_str());
    jstring ja = env->NewStringUTF(args.c_str());
    auto r = static_cast<jstring>(env->CallObjectMethod(h->bridge, h->callMid, jn, ja));
    std::string out = r == nullptr ? "ERR\n宿主工具没有返回" : jstr(env, r);
    if (r != nullptr) env->DeleteLocalRef(r);
    env->DeleteLocalRef(jn);
    env->DeleteLocalRef(ja);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return "ERR\n宿主工具抛了 Java 异常";
    }
    return out;
} //回调 Kotlin
Holder *of(jlong handle) { return reinterpret_cast<Holder *>(handle); }
} //匿名命名空间
extern "C" {
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *) {
    gVm = vm;
    return JNI_VERSION_1_6;
} //记下 JavaVM
JNIEXPORT jlong JNICALL Java_b_ai_core_Js_nativeOpen(JNIEnv *, jobject, jint memMb, jint stackKb) {
    auto *h = new Holder();
    if (!h->eng.open(memMb, stackKb)) {
        delete h;
        return 0;
    }
    return reinterpret_cast<jlong>(h);
} //建会话
JNIEXPORT void JNICALL Java_b_ai_core_Js_nativeClose(JNIEnv *env, jobject, jlong handle) {
    Holder *h = of(handle);
    if (h == nullptr) return;
    h->eng.close();
    if (h->bridge != nullptr) env->DeleteGlobalRef(h->bridge);
    delete h;
} //释放会话
JNIEXPORT void JNICALL Java_b_ai_core_Js_nativeSetBridge(JNIEnv *env, jobject, jlong handle, jobject bridge) {
    Holder *h = of(handle);
    if (h == nullptr || bridge == nullptr) return;
    if (h->bridge != nullptr) env->DeleteGlobalRef(h->bridge);
    h->bridge = env->NewGlobalRef(bridge);
    jclass c = env->GetObjectClass(h->bridge);
    h->callMid = env->GetMethodID(c, "call", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;");
    env->DeleteLocalRef(c);
    Holder *self = h;
    h->eng.setHost([self](const std::string &name, const std::string &args) -> std::string {
        if (gVm == nullptr) return "ERR\n没拿到 JavaVM";
        JNIEnv *e = nullptr;
        bool detach = false;
        if (gVm->GetEnv(reinterpret_cast<void **>(&e), JNI_VERSION_1_6) != JNI_OK) {
            if (gVm->AttachCurrentThread(&e, nullptr) != JNI_OK) return "ERR\n宿主线程附加失败";
            detach = true;
        }
        std::string out = callBridge(e, self, name, args);
        if (detach) gVm->DetachCurrentThread();
        return out;
    });
} //接宿主工具桥
JNIEXPORT jstring JNICALL Java_b_ai_core_Js_nativeEval(JNIEnv *env, jobject, jlong handle, jstring code,
                                                       jstring name, jint timeoutMs) {
    Holder *h = of(handle);
    if (h == nullptr) return back(env, "ERR\nJS 引擎没起来");
    return back(env, packResult(h->eng.eval(jstr(env, code), jstr(env, name), timeoutMs)));
} //执行脚本
JNIEXPORT jstring JNICALL Java_b_ai_core_Js_nativeTools(JNIEnv *env, jobject, jlong handle) {
    Holder *h = of(handle);
    if (h == nullptr) return back(env, "[]");
    return back(env, h->eng.toolList());
} //注册表
JNIEXPORT jboolean JNICALL Java_b_ai_core_Js_nativeHasTool(JNIEnv *env, jobject, jlong handle, jstring name) {
    Holder *h = of(handle);
    if (h == nullptr) return JNI_FALSE;
    return h->eng.hasTool(jstr(env, name)) ? JNI_TRUE : JNI_FALSE;
} //是否注册过
JNIEXPORT jstring JNICALL Java_b_ai_core_Js_nativeCallTool(JNIEnv *env, jobject, jlong handle, jstring name,
                                                           jstring args, jint timeoutMs) {
    Holder *h = of(handle);
    if (h == nullptr) return back(env, "ERR\nJS 引擎没起来");
    return back(env, packResult(h->eng.callTool(jstr(env, name), jstr(env, args), timeoutMs)));
} //调已注册工具
} // extern "C"
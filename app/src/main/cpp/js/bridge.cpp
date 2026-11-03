#include "bridge.h"

#include <string>

#include "jni_util.h"
#include "qjs.h"

namespace jsbridge {

namespace {

JavaVM *gVm = nullptr;

/** 一个句柄就是一个长会话：运行时 + 宿主工具桥 */
struct Holder {
    jsrt::Engine eng;
    jobject bridge = nullptr;
    jmethodID callMid = nullptr;
};

Holder *of(jlong handle) { return reinterpret_cast<Holder *>(handle); }

std::string packResult(const jsrt::Result &r) {
    return (r.ok ? std::string("OK\n") : std::string("ERR\n")) + r.text;
} //打包返回

/** 调 Kotlin 的 JsBridge.call：工具名 + JSON → 结果文本 */
std::string callBridge(JNIEnv *env, Holder *h, const std::string &name, const std::string &args) {
    if (h->bridge == nullptr || h->callMid == nullptr) return "ERR\n宿主工具桥没接上";
    jstring jn = env->NewStringUTF(name.c_str());
    jstring ja = env->NewStringUTF(args.c_str());
    auto r = static_cast<jstring>(env->CallObjectMethod(h->bridge, h->callMid, jn, ja));
    std::string out = r == nullptr ? "ERR\n宿主工具没有返回" : jni::jstr(env, r);
    if (r != nullptr) env->DeleteLocalRef(r);
    env->DeleteLocalRef(jn);
    env->DeleteLocalRef(ja);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return "ERR\n宿主工具抛了 Java 异常";
    }
    return out;
} //回调 Kotlin

/** JS 引擎在自己的线程上调宿主工具：没有 JNIEnv 就临时附加，用完摘掉 */
std::string callHost(Holder *self, const std::string &name, const std::string &args) {
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
} //跨线程回调

} //匿名命名空间

void onLoad(JavaVM *vm) { gVm = vm; } //记下 JavaVM

jlong open(jint memMb, jint stackKb) {
    auto *h = new Holder();
    if (!h->eng.open(memMb, stackKb)) {
        delete h;
        return 0;
    }
    return reinterpret_cast<jlong>(h);
} //建会话

void close(JNIEnv *env, jlong handle) {
    Holder *h = of(handle);
    if (h == nullptr) return;
    h->eng.close();
    if (h->bridge != nullptr) env->DeleteGlobalRef(h->bridge);
    delete h;
} //释放会话

void setBridge(JNIEnv *env, jlong handle, jobject bridge) {
    Holder *h = of(handle);
    if (h == nullptr || bridge == nullptr) return;
    if (h->bridge != nullptr) env->DeleteGlobalRef(h->bridge);
    h->bridge = env->NewGlobalRef(bridge);
    jclass c = env->GetObjectClass(h->bridge);
    h->callMid = env->GetMethodID(c, "call", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;");
    env->DeleteLocalRef(c);
    h->eng.setHost([h](const std::string &name, const std::string &args) -> std::string {
        return callHost(h, name, args);
    });
} //接宿主工具桥

std::string eval(jlong handle, const std::string &code, const std::string &filename, jint timeoutMs) {
    Holder *h = of(handle);
    if (h == nullptr) return "ERR\nJS 引擎没起来";
    return packResult(h->eng.eval(code, filename, timeoutMs));
} //执行脚本

std::string tools(jlong handle) {
    Holder *h = of(handle);
    return h == nullptr ? "[]" : h->eng.toolList();
} //注册表

bool hasTool(jlong handle, const std::string &name) {
    Holder *h = of(handle);
    return h != nullptr && h->eng.hasTool(name);
} //是否注册过

std::string callTool(jlong handle, const std::string &name, const std::string &args, jint timeoutMs) {
    Holder *h = of(handle);
    if (h == nullptr) return "ERR\nJS 引擎没起来";
    return packResult(h->eng.callTool(name, args, timeoutMs));
} //调已注册工具

} //命名空间 jsbridge
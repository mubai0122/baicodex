#include <jni.h>

#include "bridge.h"
#include "jni_util.h"

extern "C" {

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *) {
    jsbridge::onLoad(vm);
    return JNI_VERSION_1_6;
} //记下 JavaVM

JNIEXPORT jlong JNICALL Java_b_ai_core_Js_nativeOpen(JNIEnv *, jobject, jint memMb, jint stackKb) {
    return jsbridge::open(memMb, stackKb);
} //建会话

JNIEXPORT void JNICALL Java_b_ai_core_Js_nativeClose(JNIEnv *env, jobject, jlong handle) {
    jsbridge::close(env, handle);
} //释放会话

JNIEXPORT void JNICALL Java_b_ai_core_Js_nativeSetBridge(JNIEnv *env, jobject, jlong handle, jobject bridge) {
    jsbridge::setBridge(env, handle, bridge);
} //接宿主工具桥

JNIEXPORT jstring JNICALL Java_b_ai_core_Js_nativeEval(JNIEnv *env, jobject, jlong handle, jstring code,
                                                       jstring name, jint timeoutMs) {
    return jni::back(env, jsbridge::eval(handle, jni::jstr(env, code), jni::jstr(env, name), timeoutMs));
} //执行脚本

JNIEXPORT jstring JNICALL Java_b_ai_core_Js_nativeTools(JNIEnv *env, jobject, jlong handle) {
    return jni::back(env, jsbridge::tools(handle));
} //注册表

JNIEXPORT jboolean JNICALL Java_b_ai_core_Js_nativeHasTool(JNIEnv *env, jobject, jlong handle, jstring name) {
    return jsbridge::hasTool(handle, jni::jstr(env, name)) ? JNI_TRUE : JNI_FALSE;
} //是否注册过

JNIEXPORT jstring JNICALL Java_b_ai_core_Js_nativeCallTool(JNIEnv *env, jobject, jlong handle, jstring name,
                                                          jstring args, jint timeoutMs) {
    return jni::back(env, jsbridge::callTool(handle, jni::jstr(env, name), jni::jstr(env, args), timeoutMs));
} //调已注册工具

} //extern "C"
#include <jni.h>

#include <string>

#include "guard.h"
#include "jni_util.h"

using jni::back;
using jni::jstr;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_b_ai_native_Guard_nativeStart(JNIEnv *env, jobject, jstring dev) {
    return guard::start(jni::jstr(env, dev)) ? JNI_TRUE : JNI_FALSE;
} //派生组合半密钥

JNIEXPORT jboolean JNICALL
Java_b_ai_native_Guard_nativeUnlock(JNIEnv *env, jobject, jstring r64) {
    return guard::unlock(jstr(env, r64)) ? JNI_TRUE : JNI_FALSE;
} //异或组合成数据密钥

JNIEXPORT void JNICALL
Java_b_ai_native_Guard_nativeLock(JNIEnv *, jobject) {
    guard::lock();
} //清掉 native 侧密钥

JNIEXPORT jboolean JNICALL
Java_b_ai_native_Guard_nativeReady(JNIEnv *, jobject) {
    return guard::ready() ? JNI_TRUE : JNI_FALSE;
} //是否已解锁

JNIEXPORT jstring JNICALL
Java_b_ai_native_Guard_nativeRandom(JNIEnv *env, jobject) {
    return back(env, guard::randomHex());
} //随机半密钥

} //extern "C"
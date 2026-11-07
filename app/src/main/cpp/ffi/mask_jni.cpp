#include <jni.h>
#include "jni/jni_util.h"
#include "core/security/mask/mask.h"

using jni::back;
using jni::jstr;

extern "C" {
JNIEXPORT jboolean JNICALL
Java_b_ai_native_security_Mask_nativeRules(JNIEnv *env, jobject, jstring kv) {
    return bai::security::mask::rules(jstr(env, kv)) == 1 ? JNI_TRUE : JNI_FALSE;
} //载入脱敏规则

JNIEXPORT jstring JNICALL
Java_b_ai_native_security_Mask_nativeMask(JNIEnv *env, jobject, jstring s) {
    return back(env, bai::security::mask::text(jstr(env, s)));
} //文本脱敏

JNIEXPORT jint JNICALL
Java_b_ai_native_security_Mask_nativeShape(JNIEnv *, jobject) {
    return bai::security::mask::shape();
} //规则规模

} //extern "C"
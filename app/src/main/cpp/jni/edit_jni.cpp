#include <jni.h>

#include <string>

#include "edit.h"
#include "jni_util.h"

using jni::back;
using jni::jstr;

extern "C" {

JNIEXPORT jstring JNICALL
Java_b_ai_core_TextEdit_nativeFind(JNIEnv *env, jobject, jstring src, jstring oldBlock) {
    return back(env, edit::find(jstr(env, src), jstr(env, oldBlock)));
} //按行定位，只回行号与缩进，不回文本

} //extern "C"
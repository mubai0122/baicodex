#pragma once

#include <jni.h>

#include <string>

//JS 会话层：一个句柄就是一个长会话（运行时 + 宿主工具桥），Kotlin 侧负责复用与加锁
namespace jsbridge {

void onLoad(JavaVM *vm);

jlong open(jint memMb, jint stackKb);

void close(JNIEnv *env, jlong handle);

void setBridge(JNIEnv *env, jlong handle, jobject bridge);

/** 返回 "OK\n<文本>" 或 "ERR\n<错误>"，Kotlin 侧只看第一行 */
std::string eval(jlong handle, const std::string &code, const std::string &filename, jint timeoutMs);

std::string tools(jlong handle);

bool hasTool(jlong handle, const std::string &name);

std::string callTool(jlong handle, const std::string &name, const std::string &args, jint timeoutMs);

} //命名空间 jsbridge
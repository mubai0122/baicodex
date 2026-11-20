/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:02
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

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
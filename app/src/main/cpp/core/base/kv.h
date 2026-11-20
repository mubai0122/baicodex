/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:46:58
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

#pragma once
// 极简 KV 文本解析：一行一个 key=value，不做转义与嵌套（规则由服务端生产、本地消费）
#include <cstddef>
#include <string>
#include <utility>
#include <vector>

namespace bai::base {

// 单项长度上限：服务端下发的键名 / 前缀不会这么长，超长当坏数据丢掉
constexpr size_t kMaxItemLen = 64;
// 单表条数上限：防止坏数据把内存撑爆
constexpr size_t kMaxItems = 256;

// 去两端空白
std::string trim(const std::string &s);

// 取出全部键值对：键统一转小写，跳过空行与 # 开头的注释行
std::vector<std::pair<std::string, std::string>> pairs(const std::string &text);

// 逗号分隔列表：丢空项与超长项，条数封顶
std::vector<std::string> csv(const std::string &v);

// 取整数并夹到 [lo, hi]：解析失败退回 def
size_t usizeIn(const std::string &v, size_t def, size_t lo, size_t hi);

} //KV 文本
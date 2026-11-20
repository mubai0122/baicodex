/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:00
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

#pragma once
// 规则表：键名 / 已知前缀 / 认证头 / 长度阈值 / 掩码串
// 键名与前缀一律按长度降序：短项先匹配会把 access_token 拆成 access_ + token
#include <cstddef>
#include <string>
#include <vector>

namespace bai::security::mask {

struct Rules {
    std::vector<std::string> keys;      //键名
    std::vector<std::string> prefixes;  //已知凭据前缀
    std::vector<std::string> auth;      //认证头标签
    size_t minLen = 16;                 //前缀后密文最短长度
    size_t alnumLen = 24;               //高熵串最短长度
    size_t minVal = 4;                  //键值对里值的最短长度
    std::string mark = "******";        //掩码串
};

// 内置兜底：服务端规则拉不到时用这套
Rules builtin();

// 解析服务端下发的规则文本，缺省字段继承内置兜底
Rules parse(const std::string &text);

// 在一张表里找从 at 起命中的项（表已按长度降序），没命中返回 nullptr
const std::string *matchTable(const std::vector<std::string> &table, const std::string &src, size_t at);

// 从 at 起命中哪个键名
inline const std::string *hitKey(const Rules &r, const std::string &src, size_t at) { return matchTable(r.keys, src, at); }

// 从 at 起命中哪个已知前缀
inline const std::string *hitPrefix(const Rules &r, const std::string &src, size_t at) { return matchTable(r.prefixes, src, at); }

// 从 at 起命中哪个认证头标签
inline const std::string *hitAuth(const Rules &r, const std::string &src, size_t at) { return matchTable(r.auth, src, at); }

} //脱敏规则
/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:03
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

#pragma once

#include <map>
#include <string>

namespace dsl {

/**名字 →值：params里是正则，secrets/args里是实际值 */
using Table = std::map<std::string, std::string>;

struct Result {
    bool ok = true;
    /** ok=true 时是生成结果，否则是「第N行第M列: 原因」 */
    std::string text;
};

/**
 * 只校验不生成：kind 合法、花括号配对、参数名合法、参数都已声明、$secret 名字合法。
 * secrets 传 nullptr 表示跳过「secret 是否已配置」的检查（加载期 lint 用）。
 */
Result lint(const std::string &tpl, const std::string &kind,
            const Table &params, const Table *secrets);

/** 校验 + 用 args 插值生成最终 shell 命令 / http 报文 */
Result render(const std::string &tpl, const std::string &kind,
              const Table &params, const Table &secrets, const Table &args);

/** 解析扁平的 {"k":"v"} JSON：只给参数表/密钥表用，值一律当字符串 */
bool parseFlatJson(const std::string &s, Table &out);

} //dsl 命名空间
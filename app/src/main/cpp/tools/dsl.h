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
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

#include <string>
#include <vector>

namespace secret {

/** 明文加密成完整文件内容：magic + 版本 + 长度 + nonce + 密文 + tag */
std::string seal(const std::string &plain);

/** 解开整份文件内容，头不对或认证失败一律返回空串 */
std::string open(const std::string &blob);

/** 先写临时文件再改名，进程被杀不会留下半截密文 */
bool save(const std::string &path, const std::string &plain);

/** 读盘并解密，读不到或解不开返回空串 */
std::string load(const std::string &path);

/** 当前编译进去的密钥指纹，换密钥导致解不开时用它比对 */
std::string fingerprint();

/** 主种子副本（32 字节），给数据保障层派生第二种子；调用方用完自己擦 */
std::vector<unsigned char> seedCopy();

} //命名空间 secret
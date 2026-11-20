/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:46:59
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

#pragma once
// 显式凭据形态：PEM 私钥块、认证头、JWT
#include <cstddef>
#include <string>
#include "core/security/mask/rules.h"

namespace bai::security::mask {

// PEM 私钥 / 证书整块：命中返回结束位置，没命中返回 npos
size_t pem(const std::string &src, size_t at);

// 认证头：命中返回结束位置并写入 out，没命中返回 npos
size_t auth(const std::string &src, size_t at, const Rules &r, std::string &out);

// JWT：命中返回结束位置并写入 out
size_t jwt(const std::string &src, size_t at, const Rules &r, std::string &out);

} //显式形态
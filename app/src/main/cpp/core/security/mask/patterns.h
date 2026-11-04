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
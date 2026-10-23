#pragma once

#include <cstddef>
#include <string>

namespace baicore {

/** 工具结果治理：超预算时直接截止（只保留头部），不再保留尾部。
 *  未超限时 out=text 且返回 true；超限时 out=硬截断文本且返回 false。 */
bool govern(const std::string &text, size_t maxChars, std::string &out);

} //baicore 命名空间
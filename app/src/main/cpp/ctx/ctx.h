#pragma once

#include <cstddef>
#include <string>

namespace baicore {

/** 上下文字符预算：超出 ctxChars 时直接截止，只保留头部。 */
std::string budgetContext(size_t ctxChars, const std::string &history);

/** token 折字符预算：总 token × 百分比 × 1.8 字符/token。 */
size_t charBudget(size_t ctxTokens, size_t pct);

} //baicore 命名空间
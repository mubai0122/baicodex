#include "ctx.h"

namespace baicore {

std::string budgetContext(size_t ctxChars, const std::string &history) {
    if (history.size() <= ctxChars) return history;
    //预算不够就直接截止：只保留头部，不做 head+tail
    return history.substr(0, ctxChars);
} //超出预算硬截止

size_t charBudget(size_t ctxTokens, size_t pct) {
    const size_t p = pct > 100 ? 100 : pct;
    //1 token ≈ 1.8 字符；pct 是百分比，所以整体是 ctxTokens * pct * 18 / 1000
    return ctxTokens * p * 18 / 1000;
} //token 折字符预算

} //baicore 命名空间
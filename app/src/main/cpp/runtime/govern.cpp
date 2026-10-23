#include "govern.h"

namespace baicore {

bool govern(const std::string &text, size_t maxChars, std::string &out) {
    if (text.size() <= maxChars) {
        out = text;
        return true;
    }
    //预算不够就直接截止：只取头部，不做 head+tail，避免尾部内容干扰模型判断
    out = text.substr(0, maxChars);
    return false;
} //超出预算硬截止

} //baicore 命名空间
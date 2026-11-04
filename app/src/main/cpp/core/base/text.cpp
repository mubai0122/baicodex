#include "core/base/text.h"
#include "core/base/bytes.h"

namespace bai::base {

size_t skipSet(const std::string &src, size_t at, const char *set) {
    while (at < src.size() && inSet(static_cast<unsigned char>(src[at]), set)) ++at;
    return at;
} //跳字符

size_t valueEnd(const std::string &src, size_t at) {
    if (at >= src.size()) return at;
    const char q = src[at];
    if (inSet(static_cast<unsigned char>(q), kQuotes)) {
        size_t j = at + 1;
        while (j < src.size()) {
            if (src[j] == '\\') {
                j += 2; //跳过转义，避免 \" 提前收尾
                continue;
            }
            if (src[j] == q) return j;
            ++j;
        }
        return src.size();
    }
    size_t j = at;
    while (j < src.size()) {
        const unsigned char b = static_cast<unsigned char>(src[j]);
        if (!ident(b) && !inSet(b, kVal)) break;
        ++j;
    }
    return j;
} //读值

size_t tokenEnd(const std::string &src, size_t at) {
    size_t j = at;
    while (j < src.size()) {
        const unsigned char b = static_cast<unsigned char>(src[j]);
        if (!ident(b) && !inSet(b, kTok)) break;
        ++j;
    }
    return j;
} //走 token

bool b64url(unsigned char b) {
    return ident(b) || b == '-' || b == '+' || b == '/' || b == '=';
}

} //扫描游走
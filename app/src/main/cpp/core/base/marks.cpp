#include "core/base/marks.h"
#include "core/base/bytes.h"

namespace bai::base {

std::string normalizeMark(const std::string &mark) {
    std::string out;
    out.reserve(kMaxMarkLen);
    for (char c : mark) {
        if (out.size() >= kMaxMarkLen) break;
        if (!printable(static_cast<unsigned char>(c))) continue;
        out.push_back(c);
    }
    if (out.empty()) out = "*";
    return out;
} //收紧掩码串

void pushMask(std::string &out, const std::string &mark, size_t len) {
    const size_t n = len < kMaxMarks ? len : kMaxMarks;
    if (n == 0) return;
    static const std::string star = "*";
    const std::string &m = mark.empty() ? star : mark;
    for (size_t k = 0; k < n; ++k) out.push_back(m[k % m.size()]);
} //写入掩码

} //掩码写入
#include "core/base/bytes.h"

namespace bai::base {

bool startsCi(const std::string &src, size_t at, const std::string &pat) {
    if (pat.empty() || at >= src.size() || pat.size() > src.size() - at) return false;
    for (size_t k = 0; k < pat.size(); ++k) {
        if (lower(static_cast<unsigned char>(src[at + k])) != lower(static_cast<unsigned char>(pat[k]))) return false;
    }
    return true;
} //忽略大小写前缀匹配

size_t findSub(const std::string &src, size_t at, const char *sub) {
    if (sub == nullptr || sub[0] == '\0' || at >= src.size()) return std::string::npos;
    return src.find(sub, at);
} //找子串

} //字节工具

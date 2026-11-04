#include "core/security/mask/patterns.h"
#include "core/base/bytes.h"
#include "core/base/marks.h"
#include "core/base/text.h"

namespace bai::security::mask {

namespace {

constexpr const char *kPemBegin = "-----BEGIN ";
constexpr const char *kPemEnd = "-----END ";

} //匿名命名空间

size_t pem(const std::string &src, size_t at) {
    if (src.compare(at, std::char_traits<char>::length(kPemBegin), kPemBegin) != 0) return std::string::npos;
    const size_t tail = bai::base::findSub(src, at + 11, kPemEnd);
    if (tail == std::string::npos) return std::string::npos;
    const size_t close = bai::base::findSub(src, tail + 9, "-----");
    if (close == std::string::npos) return std::string::npos;
    size_t end = close + 5;
    if (end < src.size() && src[end] == '\r') ++end;
    if (end < src.size() && src[end] == '\n') ++end;
    if (end - at < 32) return std::string::npos;
    return end;
} //私钥整块

size_t auth(const std::string &src, size_t at, const Rules &r, std::string &out) {
    const std::string *tag = hitAuth(r, src, at);
    if (tag == nullptr) return std::string::npos;
    size_t j = bai::base::skipSet(src, at + tag->size(), bai::base::kBlank);
    j = bai::base::skipSet(src, j, bai::base::kQuotes);
    const size_t end = bai::base::tokenEnd(src, j);
    if (end - j < 8) return std::string::npos;
    out.append(src, at, j - at);
    bai::base::pushMask(out, r.mark, end - j);
    return end;
} //认证头

size_t jwt(const std::string &src, size_t at, const Rules &r, std::string &out) {
    if (src.compare(at, 3, "eyJ") != 0 && src.compare(at, 3, "eyA") != 0) return std::string::npos;
    size_t j = at;
    size_t dots = 0;
    while (j < src.size()) {
        const unsigned char b = static_cast<unsigned char>(src[j]);
        if (b == '.') {
            ++dots;
        } else if (!bai::base::b64url(b)) {
            break;
        }
        ++j;
    }
    if (dots != 2 || j - at < r.alnumLen) return std::string::npos;
    bai::base::pushMask(out, r.mark, j - at);
    return j;
} //JWT

} //显式形态
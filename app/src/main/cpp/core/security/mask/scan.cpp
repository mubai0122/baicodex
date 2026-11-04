#include "core/security/mask/scan.h"
#include "core/base/bytes.h"
#include "core/base/marks.h"
#include "core/base/text.h"
#include "core/security/mask/patterns.h"

namespace bai::security::mask {

namespace {

constexpr size_t kNpos = std::string::npos;

// 键值对：键名后跟 : 或 =，把值整段打码；值太短就不动
size_t pair(const std::string &src, size_t at, const Rules &r, std::string &out) {
    const std::string *k = hitKey(r, src, at);
    if (k == nullptr) return kNpos;
    size_t j = bai::base::skipSet(src, at + k->size(), " \t");
    j = bai::base::skipSet(src, j, bai::base::kQuotes);
    if (j >= src.size()) return kNpos;
    const char sep = src[j];
    if (sep != ':' && sep != '=') return kNpos;
    ++j;
    j = bai::base::skipSet(src, j, " \t");
    j = bai::base::skipSet(src, j, bai::base::kQuotes);
    const size_t end = bai::base::valueEnd(src, j);
    if (end - j < r.minVal) return kNpos;
    out.append(src, at, j - at);
    bai::base::pushMask(out, r.mark, end - j);
    return end;
} //键值对

// 已知前缀：前缀连同后面的密文一起打码
size_t prefix(const std::string &src, size_t at, const Rules &r, std::string &out) {
    const std::string *p = hitPrefix(r, src, at);
    if (p == nullptr) return kNpos;
    const size_t end = bai::base::tokenEnd(src, at + p->size());
    if (end - at < r.minLen) return kNpos;
    bai::base::pushMask(out, r.mark, end - at);
    return end;
} //已知前缀

} //匿名命名空间

bool entropy(const std::string &tok, const Rules &r) {
    if (tok.size() < r.alnumLen) return false;
    size_t up = 0;
    size_t low = 0;
    size_t dig = 0;
    size_t sep = 0;
    for (char c : tok) {
        const unsigned char b = static_cast<unsigned char>(c);
        if (bai::base::upper(b)) {
            ++up;
        } else if (bai::base::lowerCh(b)) {
            ++low;
        } else if (bai::base::digit(b)) {
            ++dig;
        } else {
            ++sep;
        }
    }
    if (up == 0 || low == 0 || dig == 0) return false;
    //分隔符超过八分之一更像路径或域名
    if (sep * 8 > tok.size()) return false;
    //每类至少占十分之一，挡掉半结构化串
    const size_t tenth = tok.size() / 10 > 0 ? tok.size() / 10 : 1;
    return up >= tenth && low >= tenth && dig >= tenth;
} //高熵判定

std::string run(const std::string &src, const Rules &r) {
    std::string out;
    out.reserve(src.size() + 8);
    size_t i = 0;
    while (i < src.size()) {
        const bool boundary = i == 0 || !bai::base::ident(static_cast<unsigned char>(src[i - 1]));
        if (boundary) {
            size_t end = pem(src, i);
            if (end != kNpos) {
                bai::base::pushMask(out, r.mark, end - i);
                i = end;
                continue;
            }
            end = pair(src, i, r, out);
            if (end != kNpos) {
                i = end;
                continue;
            }
            end = prefix(src, i, r, out);
            if (end != kNpos) {
                i = end;
                continue;
            }
            end = auth(src, i, r, out);
            if (end != kNpos) {
                i = end;
                continue;
            }
            end = jwt(src, i, r, out);
            if (end != kNpos) {
                i = end;
                continue;
            }
            end = bai::base::tokenEnd(src, i);
            if (end > i) {
                const std::string tok = src.substr(i, end - i);
                if (entropy(tok, r)) {
                    bai::base::pushMask(out, r.mark, tok.size());
                } else {
                    out += tok;
                }
                i = end;
                continue;
            }
        }
        out.push_back(src[i]);
        ++i;
    }
    return out;
} //扫描整段

} //脱敏扫描
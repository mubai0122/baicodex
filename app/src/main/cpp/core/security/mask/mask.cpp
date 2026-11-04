#include "core/security/mask/mask.h"
#include "core/security/mask/rules.h"
#include "core/security/mask/scan.h"
#include <algorithm>
#include <mutex>

#ifdef BAI_RUST
// arm64：算法在 Rust 层（rust/baiguard），这里只做转发，符号名与下面的 C++ 兜底完全一致
extern "C" int bai_mask_rules(const unsigned char *p, size_t n);
extern "C" long bai_mask(const unsigned char *s, size_t sl, unsigned char *d, size_t dl);
extern "C" int bai_mask_shape();
#endif

namespace bai::security::mask {

namespace {

std::mutex mu;
Rules held;
bool loaded = false;

// 内置兜底只构造一次，避免每段文本都重新排表
const Rules &fallback() {
    static const Rules built = builtin();
    return built;
} //兜底规则

} //匿名命名空间

int rules(const std::string &kv) {
    if (kv.empty()) return 0;
#ifdef BAI_RUST
    return bai_mask_rules(reinterpret_cast<const unsigned char *>(kv.data()), kv.size());
#else
    std::lock_guard<std::mutex> g(mu);
    held = parse(kv);
    loaded = true;
    return 1;
#endif
} //载入规则

std::string text(const std::string &s) {
    if (s.empty()) return s;
#ifdef BAI_RUST
    std::string buf(s.size() + 8, '\0');
    const long n = bai_mask(reinterpret_cast<const unsigned char *>(s.data()), s.size(),
                            reinterpret_cast<unsigned char *>(buf.data()), buf.size());
    if (n < 0) return s;
    buf.resize(static_cast<size_t>(n));
    return buf;
#else
    std::lock_guard<std::mutex> g(mu);
    return run(s, loaded ? held : fallback());
#endif
} //文本脱敏

int shape() {
#ifdef BAI_RUST
    return bai_mask_shape();
#else
    std::lock_guard<std::mutex> g(mu);
    const Rules &r = loaded ? held : fallback();
    const int keys = static_cast<int>(std::min<size_t>(r.keys.size(), 255));
    const int prefixes = static_cast<int>(std::min<size_t>(r.prefixes.size(), 255));
    return (keys << 8) | prefixes;
#endif
} //规则规模

} //脱敏入口
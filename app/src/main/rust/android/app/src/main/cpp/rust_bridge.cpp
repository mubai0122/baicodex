// rust_bridge.cpp —— 见同名头文件里的说明。
//
// 这里只做三件事：拼请求、调 C ABI、释放内存。任何"顺手处理一下"的念头都该
// 掐掉：处理逻辑一旦出现在这里，两个前端的行为就开始分叉。

#include "rust_bridge.h"

#include <cstring>
#include <mutex>
#include <utility>

// Rust 侧导出的 C ABI（src/ffi/mod.rs）。签名必须与那边逐字一致：
//
//   char* baicodex_call(const char* req);          // 用完要 baicodex_free
//   void  baicodex_free(char* p);
//   int   baicodex_set_event_cb(void (*cb)(const char*));
extern "C" {
char* baicodex_call(const char* req);
void baicodex_free(char* p);
int baicodex_set_event_cb(void (*cb)(const char*));
}

namespace bai {
namespace {

/// 全局事件回调。Rust 只会保留一个函数指针，所以真正的分发在这里做。
std::mutex g_cb_mutex;
std::function<void(const std::string&)> g_cb;

/// Rust 调过来的入口。**不能抛异常**：跨语言边界抛异常是未定义行为。
extern "C" void bai_event_trampoline(const char* json) {
    if (json == nullptr) return;
    std::function<void(const std::string&)> cb;
    {
        std::lock_guard<std::mutex> lock(g_cb_mutex);
        cb = g_cb;
    }
    if (!cb) return;
    try {
        cb(std::string(json));
    } catch (...) {
        // 回调自己的异常自己负责，绝不能让它穿过 C ABI。
    }
}

/// 极简 JSON 字符串转义，只处理拼请求时用得到的那几个字符。
std::string escape(const std::string& s) {
    std::string out;
    out.reserve(s.size() + 8);
    for (char c : s) {
        switch (c) {
            case '"': out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\n': out += "\\n"; break;
            case '\r': out += "\\r"; break;
            case '\t': out += "\\t"; break;
            default: out += c;
        }
    }
    return out;
}

/// 从 JSON 里找 "key":"value" 的值。只用于取 ok / data / message 这种顶层短字段。
std::string find_string(const std::string& json, const std::string& key) {
    const std::string needle = "\"" + key + "\"";
    size_t pos = json.find(needle);
    if (pos == std::string::npos) return "";
    pos = json.find(':', pos + needle.size());
    if (pos == std::string::npos) return "";
    ++pos;
    while (pos < json.size() && (json[pos] == ' ' || json[pos] == '\t')) ++pos;
    if (pos >= json.size() || json[pos] != '"') return "";
    ++pos;
    std::string out;
    while (pos < json.size() && json[pos] != '"') {
        if (json[pos] == '\\' && pos + 1 < json.size()) {
            char n = json[pos + 1];
            switch (n) {
                case 'n': out += '\n'; break;
                case 'r': out += '\r'; break;
                case 't': out += '\t'; break;
                case '"': out += '"'; break;
                case '\\': out += '\\'; break;
                default: out += n;
            }
            pos += 2;
        } else {
            out += json[pos++];
        }
    }
    return out;
}

}  // namespace

Kernel::Kernel(std::string base) : base_(std::move(base)) {}

std::string Kernel::boot() {
    auto args = std::string("{\"base\":\"") + escape(base_) + "\"}";
    return call("boot", args);
}

std::string Kernel::call(const std::string& op, const std::string& args_json) const {
    const std::string req =
        std::string("{\"op\":\"") + escape(op) + "\",\"args\":" + args_json + "}";

    char* raw = baicodex_call(req.c_str());
    if (raw == nullptr) {
        return R"({"ok":false,"error":{"code":"ffi","message":"内核没有返回","retryable":false}})";
    }
    std::string out(raw);
    baicodex_free(raw);
    return out;
}

void Kernel::on_event(std::function<void(const std::string&)> cb) {
    {
        std::lock_guard<std::mutex> lock(g_cb_mutex);
        g_cb = std::move(cb);
    }
    // 只挂一次转发器。Rust 侧对重复注册是幂等的，这里再调一次也无害。
    baicodex_set_event_cb(&bai_event_trampoline);
}

bool Kernel::ok(const std::string& response) {
    // 找 "ok":true —— 只认这一个形态，内核保证输出紧凑 JSON。
    return response.find("\"ok\":true") != std::string::npos;
}

std::string Kernel::data(const std::string& response) {
    return find_string(response, "data");
}

std::string Kernel::error_message(const std::string& response) {
    return find_string(response, "message");
}

}  // namespace bai

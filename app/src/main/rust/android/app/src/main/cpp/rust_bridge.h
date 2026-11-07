// rust_bridge.h —— C++ 侧与 Rust 内核之间唯一的通道。
//
// ============================================================================
// 这一层为什么几乎是空的
// ============================================================================
//
// 老版本这里是 27 个文件、2000 多行：DSL 渲染、文本编辑定位、盒式模糊、
// QuickJS 绑定、HTTP、AES 落盘、三层密钥、预算…… 这些东西现在全在 Rust 里
// （见 src/core/security、src/exec、src/net），并且有单元测试。
//
// 留在 C++ 里的理由只剩两种：
//
//   1. 某些第三方 SDK 只给 C++ 头文件，必须在 C++ 里调；
//   2. 已有的 JNI 方法名不能变（老版本 APK 的热修、外部插件还在用）。
//
// 两种情况都只需要"把 JSON 递过去、把 JSON 接回来"。所以这个头文件就两件事：
// 调一次、收事件。**不要在这里加业务逻辑**——一旦加了，Android 与终端就会
// 走出两套行为。
//
// ============================================================================
// 用法
// ============================================================================
//
//   bai::Kernel kernel("");                       // "" = 用默认数据根
//   auto r = kernel.call("config_get");           // {"ok":true,"data":{...}}
//   if (!bai::Kernel::ok(r)) { ... }              // 统一判 ok
//
//   kernel.on_event([](const std::string& json) { // 后台线程回调
//       // 解析 json 里的 type 字段
//   });
//
// 线程：`call` 是阻塞的，**不要在 UI 线程调**。事件回调在 Rust 的守护线程上，
// 回调里不要做重活，也不要直接碰 UI（Android 上要 post 到主线程）。
//
// ============================================================================

#ifndef BAI_RUST_BRIDGE_H
#define BAI_RUST_BRIDGE_H

#include <functional>
#include <string>

namespace bai {

/// 内核句柄。
///
/// 进程内单例语义：内核自己保证"只启动一次"，所以这里可以有多个实例，
/// 它们操作的是同一个内核。构造时不会自动启动，需要显式调 [boot]。
class Kernel {
public:
    /// @param base 数据根目录；空串表示用默认（$BAICODE_HOME 或 ~/.baicode）。
    explicit Kernel(std::string base = "");

    /// 启动内核。重复调用是幂等的（已启动就复用）。
    /// @return 内核返回的 JSON，形如 {"ok":true,"data":{"version":"2.0.0",...}}
    std::string boot();

    /// 调一次。返回值永远是合法 JSON。
    std::string call(const std::string& op, const std::string& args_json = "{}") const;

    /// 注册事件回调。回调在后台线程执行，参数是一行 JSON。
    /// 只注册一次；重复注册会覆盖（不会重复推送）。
    void on_event(std::function<void(const std::string&)> cb);

    /// 判断一次调用是否成功。
    static bool ok(const std::string& response);

    /// 取 data 字段（失败时返回空串）。只做最浅的一层提取，够用即可；
    /// 需要精细解析请交给 nlohmann/json 之类的库。
    static std::string data(const std::string& response);

    /// 取 error.message（失败时）。没有则返回空串。
    static std::string error_message(const std::string& response);

private:
    std::string base_;
};

}  // namespace bai

#endif  // BAI_RUST_BRIDGE_H

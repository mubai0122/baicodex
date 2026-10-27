#pragma once
#include <cstdint>
#include <functional>
#include <string>
#include "quickjs.h"
namespace jsrt {
/**宿主工具桥：工具名 + JSON 参数 → 结果文本（"OK\n..." / "ERR\n..."），由 Kotlin 侧实现 */
using HostCall = std::function<std::string(const std::string &name, const std::string &argsJson)>;
struct Result {
    bool ok = true;
    std::string text;
};
class Engine {
public:
    Engine() = default;
    ~Engine();
    Engine(const Engine &) = delete;
    Engine &operator=(const Engine &) = delete;
    /**建运行时：内存上限 MB、栈上限 KB；失败返回 false */
    bool open(int memMb, int stackKb);
    void close();
    bool ready() const { return ctx_ != nullptr; }
    void setHost(HostCall fn) { host_ = std::move(fn); }
    /**执行脚本：超时由中断处理器打断，返回值与 console 输出一起带出 */
    Result eval(const std::string &code, const std::string &filename, int timeoutMs);
    /**已注册工具清单：[{name,description,parameters}]，没有就是 [] */
    std::string toolList();
    /**调已注册工具：把 argsJson 交给脚本里的 run(args) */
    Result callTool(const std::string &name, const std::string &argsJson, int timeoutMs);
    /**脚本注册过哪些工具名（判断工具是否属于 JS 侧） */
    bool hasTool(const std::string &name);
private:
    static JSValue fnCall(JSContext *ctx, JSValueConst thisVal, int argc, JSValueConst *argv);
    static JSValue fnLog(JSContext *ctx, JSValueConst thisVal, int argc, JSValueConst *argv);
    static JSValue fnRegister(JSContext *ctx, JSValueConst thisVal, int argc, JSValueConst *argv);
    static int onTick(JSRuntime *rt, void *opaque);
    void arm(int timeoutMs);
    void disarm() { deadline_ = 0; }
    bool timedOut() const;
    JSValue toolsArray();
    std::string exceptionText();
    std::string stringify(JSValueConst v);
    JSRuntime *rt_ = nullptr;
    JSContext *ctx_ = nullptr;
    HostCall host_;
    int64_t deadline_ = 0;
    int depth_ = 0;
    int lastTimeout_ = 0;
    std::string log_;
};
} //jsrt 命名空间
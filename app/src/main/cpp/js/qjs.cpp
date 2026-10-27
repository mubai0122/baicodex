#include "qjs.h"
#include <chrono>
#include <cstdio>
#include <cstring>
namespace jsrt {
namespace {
/**墙钟毫秒：超时只看这个，不用 QuickJS 的指令计数 */
int64_t nowMs() {
    using namespace std::chrono;
    return duration_cast<milliseconds>(steady_clock::now().time_since_epoch()).count();
} //当前毫秒
std::string toStd(JSContext *ctx, JSValueConst v) {
    size_t len = 0;
    const char *p = JS_ToCStringLen(ctx, &len, v);
    if (p == nullptr) return "";
    std::string out(p, len);
    JS_FreeCString(ctx, p);
    return out;
} //JS 字符串 → std::string
std::string escape(const std::string &s) {
    std::string o;
    o.reserve(s.size() + 8);
    for (char raw : s) {
        const unsigned char c = static_cast<unsigned char>(raw);
        switch (c) {
            case '"': o += "\\\""; break;
            case '\\': o += "\\\\"; break;
            case '\n': o += "\\n"; break;
            case '\r': o += "\\r"; break;
            case '\t': o += "\\t"; break;
            default:
                if (c < 0x20) {
                    char b[8];
                    std::snprintf(b, sizeof b, "\\u%04x", c);
                    o += b;
                } else {
                    o += raw;
                }
        }
    }
    return o;
} //JSON 字符串转义
constexpr int kMaxDepth = 8;
} //匿名命名空间
Engine::~Engine() { close(); }
bool Engine::open(int memMb, int stackKb) {
    close();
    rt_ = JS_NewRuntime();
    if (rt_ == nullptr) return false;
    if (memMb > 0) JS_SetMemoryLimit(rt_, static_cast<size_t>(memMb) * 1024u * 1024u);
    if (stackKb > 0) JS_SetMaxStackSize(rt_, static_cast<size_t>(stackKb) * 1024u);
    JS_SetInterruptHandler(rt_, onTick, this);
    ctx_ = JS_NewContext(rt_);
    if (ctx_ == nullptr) {
        JS_FreeRuntime(rt_);
        rt_ = nullptr;
        return false;
    }
    JS_SetContextOpaque(ctx_, this);
    JSValue g = JS_GetGlobalObject(ctx_);
    JS_SetPropertyStr(ctx_, g, "print", JS_NewCFunction(ctx_, fnLog, "print", 1));
    JSValue console = JS_NewObject(ctx_);
    JS_SetPropertyStr(ctx_, console, "log", JS_NewCFunction(ctx_, fnLog, "log", 1));
    JS_SetPropertyStr(ctx_, console, "info", JS_NewCFunction(ctx_, fnLog, "info", 1));
    JS_SetPropertyStr(ctx_, console, "warn", JS_NewCFunction(ctx_, fnLog, "warn", 1));
    JS_SetPropertyStr(ctx_, console, "error", JS_NewCFunction(ctx_, fnLog, "error", 1));
    JS_SetPropertyStr(ctx_, g, "console", console);
    JSValue bai = JS_NewObject(ctx_);
    JS_SetPropertyStr(ctx_, bai, "call", JS_NewCFunction(ctx_, fnCall, "call", 2));
    JS_SetPropertyStr(ctx_, g, "bai", bai);
    JS_SetPropertyStr(ctx_, g, "registerTool", JS_NewCFunction(ctx_, fnRegister, "registerTool", 1));
    JS_SetPropertyStr(ctx_, g, "__baiTools", JS_NewArray(ctx_));
    JS_FreeValue(ctx_, g);
    return true;
} //建运行时
void Engine::close() {
    if (ctx_ != nullptr) {
        JS_FreeContext(ctx_);
        ctx_ = nullptr;
    }
    if (rt_ != nullptr) {
        JS_FreeRuntime(rt_);
        rt_ = nullptr;
    }
    host_ = nullptr;
    depth_ = 0;
    deadline_ = 0;
} //释放运行时
void Engine::arm(int timeoutMs) {
    lastTimeout_ = timeoutMs;
    deadline_ = timeoutMs > 0 ? nowMs() + timeoutMs : 0;
} //上超时
bool Engine::timedOut() const { return lastTimeout_ > 0 && deadline_ == 0; }
int Engine::onTick(JSRuntime *, void *opaque) {
    auto *self = static_cast<Engine *>(opaque);
    if (self == nullptr || self->deadline_ == 0) return 0;
    return nowMs() > self->deadline_ ? 1 : 0;
} //中断检查
std::string Engine::exceptionText() {
    JSValue e = JS_GetException(ctx_);
    const std::string msg = toStd(ctx_, e);
    std::string stack;
    if (JS_IsError(ctx_, e)) {
        JSValue st = JS_GetPropertyStr(ctx_, e, "stack");
        if (!JS_IsUndefined(st)) stack = toStd(ctx_, st);
        JS_FreeValue(ctx_, st);
    }
    JS_FreeValue(ctx_, e);
    //QuickJS 的 stack 里通常已经含首行消息；只有它不含时才追加，避免同一句话打两遍
    std::string out = msg;
    if (!stack.empty() && stack.find(msg) == std::string::npos) {
        if (!out.empty()) out += '\n';
        out += stack;
    }
    if (out.empty()) out = "脚本抛了异常，但没有拿到错误信息";
    return out;
} //取异常文本
std::string Engine::stringify(JSValueConst v) {
    if (JS_IsUndefined(v) || JS_IsNull(v)) return "";
    if (JS_IsString(v)) return toStd(ctx_, v);
    JSValue s = JS_JSONStringify(ctx_, v, JS_UNDEFINED, JS_UNDEFINED);
    if (JS_IsException(s)) {
        JS_FreeValue(ctx_, JS_GetException(ctx_));
        JS_FreeValue(ctx_, s);
        return "";
    }
    std::string out = JS_IsUndefined(s) ? "" : toStd(ctx_, s);
    JS_FreeValue(ctx_, s);
    return out;
} //结果转文本
JSValue Engine::toolsArray() {
    JSValue g = JS_GetGlobalObject(ctx_);
    JSValue arr = JS_GetPropertyStr(ctx_, g, "__baiTools");
    JS_FreeValue(ctx_, g);
    return arr;
} //取注册表
JSValue Engine::fnLog(JSContext *ctx, JSValueConst, int argc, JSValueConst *argv) {
    auto *self = static_cast<Engine *>(JS_GetContextOpaque(ctx));
    if (self == nullptr) return JS_UNDEFINED;
    for (int i = 0; i < argc; i++) {
        if (i > 0) self->log_ += ' ';
        if (JS_IsString(argv[i])) {
            self->log_ += toStd(ctx, argv[i]);
        } else {
            JSValue s = JS_JSONStringify(ctx, argv[i], JS_UNDEFINED, JS_UNDEFINED);
            if (JS_IsException(s)) {
                JS_FreeValue(ctx, JS_GetException(ctx));
            } else if (!JS_IsUndefined(s)) {
                self->log_ += toStd(ctx, s);
            }
            JS_FreeValue(ctx, s);
        }
    }
    self->log_ += '\n';
    return JS_UNDEFINED;
} //console.log
JSValue Engine::fnCall(JSContext *ctx, JSValueConst, int argc, JSValueConst *argv) {
    auto *self = static_cast<Engine *>(JS_GetContextOpaque(ctx));
    if (self == nullptr || !self->host_) return JS_ThrowInternalError(ctx, "宿主工具桥没接上");
    if (argc < 1 || !JS_IsString(argv[0])) return JS_ThrowTypeError(ctx, "bai.call(工具名, 参数对象)");
    const std::string name = toStd(ctx, argv[0]);
    if (name.empty()) return JS_ThrowTypeError(ctx, "bai.call 的工具名不能为空");
    std::string args = "{}";
    if (argc > 1 && !JS_IsUndefined(argv[1])) {
        JSValue s = JS_JSONStringify(ctx, argv[1], JS_UNDEFINED, JS_UNDEFINED);
        if (JS_IsException(s)) {
            JS_FreeValue(ctx, JS_GetException(ctx));
            JS_FreeValue(ctx, s);
            return JS_ThrowTypeError(ctx, "bai.call 的参数转不成 JSON");
        }
        args = JS_IsUndefined(s) ? "{}" : toStd(ctx, s);
        JS_FreeValue(ctx, s);
    }
    if (self->depth_ >= kMaxDepth) return JS_ThrowInternalError(ctx, "工具嵌套调用超过 %d 层", kMaxDepth);
    self->depth_++;
    const std::string out = self->host_(name, args);
    self->depth_--;
    return JS_NewStringLen(ctx, out.c_str(), out.size());
} //bai.call
JSValue Engine::fnRegister(JSContext *ctx, JSValueConst, int argc, JSValueConst *argv) {
    auto *self = static_cast<Engine *>(JS_GetContextOpaque(ctx));
    if (self == nullptr) return JS_ThrowInternalError(ctx, "引擎已释放");
    if (argc < 1 || !JS_IsObject(argv[0])) {
        return JS_ThrowTypeError(ctx, "registerTool({name, description, parameters, run})");
    }
    JSValue nv = JS_GetPropertyStr(ctx, argv[0], "name");
    const std::string name = JS_IsString(nv) ? toStd(ctx, nv) : "";
    JS_FreeValue(ctx, nv);
    if (name.empty()) return JS_ThrowTypeError(ctx, "registerTool 的 name 不能为空");
    JSValue rv = JS_GetPropertyStr(ctx, argv[0], "run");
    const bool isFn = JS_IsFunction(ctx, rv) != 0;
    JS_FreeValue(ctx, rv);
    if (!isFn) return JS_ThrowTypeError(ctx, "registerTool 的 run 必须是函数");
    JSValue arr = self->toolsArray();
    if (JS_IsException(arr) || JS_IsUndefined(arr)) {
        JS_FreeValue(ctx, arr);
        return JS_ThrowInternalError(ctx, "注册表丢了");
    }
    uint32_t len = 0;
    JSValue lv = JS_GetPropertyStr(ctx, arr, "length");
    JS_ToUint32(ctx, &len, lv);
    JS_FreeValue(ctx, lv);
    for (uint32_t i = 0; i < len; i++) {
        JSValue o = JS_GetPropertyUint32(ctx, arr, i);
        JSValue on = JS_GetPropertyStr(ctx, o, "name");
        const bool dup = JS_IsString(on) && toStd(ctx, on) == name;
        JS_FreeValue(ctx, on);
        JS_FreeValue(ctx, o);
        if (dup) {
            JS_FreeValue(ctx, arr);
            return JS_ThrowTypeError(ctx, "工具名 %s 已经注册过了", name.c_str());
        }
    }
    JS_SetPropertyUint32(ctx, arr, len, JS_DupValue(ctx, argv[0]));
    JS_FreeValue(ctx, arr);
    return JS_UNDEFINED;
} //registerTool
Result Engine::eval(const std::string &code, const std::string &filename, int timeoutMs) {
    Result r;
    if (ctx_ == nullptr) {
        r.ok = false;
        r.text = "JS 引擎没起来";
        return r;
    }
    log_.clear();
    arm(timeoutMs);
    JSValue v = JS_Eval(ctx_, code.c_str(), code.size(), filename.c_str(), JS_EVAL_TYPE_GLOBAL);
    const bool dead = (lastTimeout_ > 0 && deadline_ != 0 && nowMs() > deadline_);
    disarm();
    if (JS_IsException(v)) {
        std::string err = exceptionText();
        JS_FreeValue(ctx_, v);
        r.ok = false;
        r.text = dead ? "脚本超过 " + std::to_string(lastTimeout_) + " ms 被强行中断" : err;
        if (!log_.empty()) r.text = log_ + r.text;
        return r;
    }
    std::string body = stringify(v);
    JS_FreeValue(ctx_, v);
    std::string out = log_;
    if (!body.empty()) out += body;
    if (out.empty()) out = "（脚本没有输出）";
    r.text = out;
    return r;
} //执行脚本
std::string Engine::toolList() {
    if (ctx_ == nullptr) return "[]";
    JSValue arr = toolsArray();
    if (JS_IsException(arr) || JS_IsUndefined(arr)) {
        JS_FreeValue(ctx_, arr);
        return "[]";
    }
    uint32_t len = 0;
    JSValue lv = JS_GetPropertyStr(ctx_, arr, "length");
    JS_ToUint32(ctx_, &len, lv);
    JS_FreeValue(ctx_, lv);
    std::string out = "[";
    uint32_t n = 0;
    for (uint32_t i = 0; i < len; i++) {
        JSValue o = JS_GetPropertyUint32(ctx_, arr, i);
        JSValue nv = JS_GetPropertyStr(ctx_, o, "name");
        JSValue dv = JS_GetPropertyStr(ctx_, o, "description");
        JSValue pv = JS_GetPropertyStr(ctx_, o, "parameters");
        const std::string name = JS_IsString(nv) ? toStd(ctx_, nv) : "";
        const std::string desc = JS_IsString(dv) ? toStd(ctx_, dv) : "";
        std::string params = JS_IsObject(pv) ? stringify(pv) : "";
        JS_FreeValue(ctx_, nv);
        JS_FreeValue(ctx_, dv);
        JS_FreeValue(ctx_, pv);
        JS_FreeValue(ctx_, o);
        if (name.empty()) continue;
        if (n > 0) out += ',';
        out += "{\"name\":\"" + escape(name) + "\",\"description\":\"" + escape(desc) + "\",\"parameters\":" +
               (params.empty() ? std::string("{\"type\":\"object\",\"properties\":{}}") : params) + "}";
        n++;
    }
    JS_FreeValue(ctx_, arr);
    out += "]";
    return out;
} //注册表 → JSON
bool Engine::hasTool(const std::string &name) {
    if (ctx_ == nullptr) return false;
    JSValue arr = toolsArray();
    if (JS_IsException(arr) || JS_IsUndefined(arr)) {
        JS_FreeValue(ctx_, arr);
        return false;
    }
    uint32_t len = 0;
    JSValue lv = JS_GetPropertyStr(ctx_, arr, "length");
    JS_ToUint32(ctx_, &len, lv);
    JS_FreeValue(ctx_, lv);
    bool found = false;
    for (uint32_t i = 0; i < len && !found; i++) {
        JSValue o = JS_GetPropertyUint32(ctx_, arr, i);
        JSValue nv = JS_GetPropertyStr(ctx_, o, "name");
        found = JS_IsString(nv) && toStd(ctx_, nv) == name;
        JS_FreeValue(ctx_, nv);
        JS_FreeValue(ctx_, o);
    }
    JS_FreeValue(ctx_, arr);
    return found;
} //是否注册过
Result Engine::callTool(const std::string &name, const std::string &argsJson, int timeoutMs) {
    Result r;
    if (ctx_ == nullptr) {
        r.ok = false;
        r.text = "JS 引擎没起来";
        return r;
    }
    JSValue arr = toolsArray();
    if (JS_IsException(arr) || JS_IsUndefined(arr)) {
        JS_FreeValue(ctx_, arr);
        r.ok = false;
        r.text = "JS 工具注册表丢了";
        return r;
    }
    uint32_t len = 0;
    JSValue lv = JS_GetPropertyStr(ctx_, arr, "length");
    JS_ToUint32(ctx_, &len, lv);
    JS_FreeValue(ctx_, lv);
    JSValue fn = JS_UNDEFINED;
    for (uint32_t i = 0; i < len && JS_IsUndefined(fn); i++) {
        JSValue o = JS_GetPropertyUint32(ctx_, arr, i);
        JSValue nv = JS_GetPropertyStr(ctx_, o, "name");
        const bool hit = JS_IsString(nv) && toStd(ctx_, nv) == name;
        JS_FreeValue(ctx_, nv);
        if (hit) fn = JS_GetPropertyStr(ctx_, o, "run");
        JS_FreeValue(ctx_, o);
    }
    JS_FreeValue(ctx_, arr);
    if (!JS_IsFunction(ctx_, fn)) {
        JS_FreeValue(ctx_, fn);
        r.ok = false;
        r.text = "JS 里没有这个工具：" + name;
        return r;
    }
    JSValue arg = argsJson.empty()
                      ? JS_NewObject(ctx_)
                      : JS_ParseJSON(ctx_, argsJson.c_str(), argsJson.size(), "<args>");
    if (JS_IsException(arg)) {
        JS_FreeValue(ctx_, JS_GetException(ctx_));
        JS_FreeValue(ctx_, arg);
        JS_FreeValue(ctx_, fn);
        arg = JS_NewObject(ctx_);
    }
    log_.clear();
    arm(timeoutMs);
    JSValue v = JS_Call(ctx_, fn, JS_UNDEFINED, 1, &arg);
    const bool dead = (lastTimeout_ > 0 && deadline_ != 0 && nowMs() > deadline_);
    disarm();
    JS_FreeValue(ctx_, arg);
    JS_FreeValue(ctx_, fn);
    if (JS_IsException(v)) {
        std::string err = exceptionText();
        JS_FreeValue(ctx_, v);
        r.ok = false;
        r.text = dead ? "JS 工具超过 " + std::to_string(lastTimeout_) + " ms 被强行中断" : err;
        if (!log_.empty()) r.text = log_ + r.text;
        return r;
    }
    std::string body = stringify(v);
    JS_FreeValue(ctx_, v);
    std::string out = log_;
    if (!body.empty()) out += body;
    if (out.empty()) out = "（工具没有输出）";
    r.text = out;
    return r;
} //调已注册工具
} //jsrt 命名空间
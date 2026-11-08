#include <jni.h>

#include <cstdio>
#include <string>

#include "http.h"

namespace {
std::string gErr; //最近一次失败原因，Kotlin 侧想看就看
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_b_ai_native_net_Net_nativeGet(JNIEnv* env, jobject, jstring url, jint timeoutMs) {
    if (url == nullptr) return nullptr;
    const char* raw = env->GetStringUTFChars(url, nullptr);
    if (raw == nullptr) return nullptr;
    std::string u(raw);
    env->ReleaseStringUTFChars(url, raw);

    net::Resp r = net::get(u, timeoutMs > 0 ? timeoutMs : 6000);
    if (r.code < 200 || r.code >= 300 || r.body.empty()) {
        gErr = r.err.empty() ? ("http " + std::to_string(r.code)) : r.err;
        return nullptr;
    }
    gErr.clear();
    auto arr = env->NewByteArray(static_cast<jsize>(r.body.size()));
    if (arr == nullptr) return nullptr;
    env->SetByteArrayRegion(arr, 0, static_cast<jsize>(r.body.size()),
                            reinterpret_cast<const jbyte*>(r.body.data()));
    return arr;
} //C层 HTTPS GET：成功给字节，失败给 null

extern "C" JNIEXPORT jstring JNICALL
Java_b_ai_native_net_Net_nativeErr(JNIEnv* env, jobject) {
    return env->NewStringUTF(gErr.c_str());
} //最近一次失败原因
namespace {
std::string toStd(JNIEnv* env, jstring s) {
    if (s == nullptr) return "";
    const char* raw = env->GetStringUTFChars(s, nullptr);
    if (raw == nullptr) return "";
    std::string out(raw);
    env->ReleaseStringUTFChars(s, raw);
    return out;
} //jstring → std::string
} //namespace

extern "C" JNIEXPORT jstring JNICALL
Java_b_ai_native_net_Net_nativePing(JNIEnv* env, jobject, jstring host, jint port, jint count,
                                   jint timeoutMs) {
    auto h = toStd(env, host);
    if (h.empty()) return env->NewStringUTF("失败：没给主机名");
    net::Ping p = net::ping(h, port, count, timeoutMs);
    std::string out = "域名 " + h;
    if (!p.ip.empty()) out += " → " + p.ip;
    out += "\n";
    if (!p.err.empty() && p.rtt.empty()) {
        out += "失败：" + p.err;
        return env->NewStringUTF(out.c_str());
    }
    int lo = p.rtt.empty() ? 0 : p.rtt[0];
    int hi = lo;
    long sum = 0;
    for (size_t i = 0; i < p.rtt.size(); ++i) {
        int v = p.rtt[i];
        if (v < lo) lo = v;
        if (v > hi) hi = v;
        sum += v;
        out += "第" + std::to_string(i + 1) + "次 " + std::to_string(v) + " ms\n";
    }
    if (p.rtt.empty()) {
        out += "全部失败（" + p.err + "）";
        return env->NewStringUTF(out.c_str());
    }
    double avg = static_cast<double>(sum) / static_cast<double>(p.rtt.size());
    char buf[128];
    snprintf(buf, sizeof(buf), "最小 %d ms · 平均 %.1f ms · 最大 %d ms · 成功 %zu/%d", lo, avg, hi,
             p.rtt.size(), p.sent);
    out += buf;
    return env->NewStringUTF(out.c_str());
} //测延迟：TCP 建连时间

extern "C" JNIEXPORT jstring JNICALL
Java_b_ai_native_net_Net_nativeCurl(JNIEnv* env, jobject, jstring method, jstring url,
                                   jstring headers, jstring body, jint timeoutMs, jint maxBody) {
    auto u = toStd(env, url);
    if (u.empty()) return env->NewStringUTF("失败：没给 url");
    net::Resp r = net::curl(toStd(env, method), u, toStd(env, headers), toStd(env, body),
                            timeoutMs, static_cast<size_t>(maxBody > 0 ? maxBody : 0));
    if (r.code == 0) return env->NewStringUTF(("失败：" + r.err).c_str());
    std::string out = "HTTP " + std::to_string(r.code) + "\n";
    out += r.head;
    out += "\n\n";
    size_t n = r.body.size();
    out.append(reinterpret_cast<const char*>(r.body.data()), n);
    if (n == 0) out += "（响应体为空）";
    return env->NewStringUTF(out.c_str());
} //发请求并原样回显状态码 / 头 / 体

extern "C" JNIEXPORT jstring JNICALL
Java_b_ai_native_net_Net_nativePage(JNIEnv* env, jobject, jstring url, jint timeoutMs,
                                   jint maxChars) {
    auto u = toStd(env, url);
    if (u.empty()) return env->NewStringUTF("失败：没给 url");
    net::Resp r = net::curl("GET", u, "Accept: text/html,application/xhtml+xml,*/*", "",
                            timeoutMs, 4u * 1024 * 1024);
    if (r.code == 0) return env->NewStringUTF(("失败：" + r.err).c_str());
    std::string raw(reinterpret_cast<const char*>(r.body.data()), r.body.size());
    std::string txt = net::text(raw);
    int cap = maxChars > 0 ? maxChars : 20000;
    std::string body = txt.size() > static_cast<size_t>(cap)
                           ? txt.substr(0, static_cast<size_t>(cap)) + "\n…已截断（共 " +
                                 std::to_string(txt.size()) + " 字）"
                           : txt;
    std::string out = "HTTP " + std::to_string(r.code) + " · " + std::to_string(txt.size()) +
                      " 字\n\n" + body;
    return env->NewStringUTF(out.c_str());
} //抓网页：native 取 HTML 再抽正文

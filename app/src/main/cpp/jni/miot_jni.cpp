#include <jni.h>

#include <string>
#include <vector>

#include "core/feature/miot/miot.h"
#include "jni_util.h"

namespace {

/** 收 C ABI 写出来的字节：扩容到 8KB 再按实际长度截断 */
std::string take(const std::vector<unsigned char> &buf, int n) {
    if (n <= 0) return std::string();
    return std::string(reinterpret_cast<const char *>(buf.data()), static_cast<size_t>(n));
} //截取有效字节
} //命名空间

extern "C" {
//广播探测：返回若干行「did|ip|stamp」，没探到返回空串
JNIEXPORT jstring JNICALL
Java_b_ai_native_miot_MiLanNative_nativeDiscover(JNIEnv *env, jobject, jint timeoutMs) {
    std::vector<unsigned char> buf(8192);
    const int n = bai_miot_discover(timeoutMs, buf.data(), static_cast<int>(buf.size()));
    return jni::back(env, take(buf, n));
} //广播探测

//单播请求-应答：返回「时间戳\n明文JSON」，失败返回 null
JNIEXPORT jstring JNICALL
Java_b_ai_native_miot_MiLanNative_nativeCall(JNIEnv *env, jobject, jstring ip, jlong did, jstring tokenHex,
                                             jint stamp, jstring json, jint timeoutMs) {
    const std::string sIp = jni::jstr(env, ip);
    const std::string sTok = jni::jstr(env, tokenHex);
    const std::string sJson = jni::jstr(env, json);
    if (sIp.empty() || sJson.empty()) return nullptr;
    std::vector<unsigned char> buf(16384);
    unsigned int stampOut = 0;
    const int n = bai_miot_call(sIp.c_str(), static_cast<long long>(did), sTok.c_str(),
                                static_cast<unsigned int>(stamp), sJson.c_str(), timeoutMs, buf.data(),
                                static_cast<int>(buf.size()), &stampOut);
    if (n <= 0) return nullptr;
    const std::string head = std::to_string(stampOut) + "\n";
    return jni::back(env, head + take(buf, n));
} //单播请求-应答

//探活：0 = 离线，否则是设备时间戳
JNIEXPORT jlong JNICALL
Java_b_ai_native_miot_MiLanNative_nativeAlive(JNIEnv *env, jobject, jstring ip, jlong did, jint timeoutMs) {
    const std::string sIp = jni::jstr(env, ip);
    if (sIp.empty()) return 0;
    unsigned int stampOut = 0;
    if (bai_miot_alive(sIp.c_str(), static_cast<long long>(did), timeoutMs, &stampOut) != 1) return 0;
    return static_cast<jlong>(stampOut);
} //探活
}
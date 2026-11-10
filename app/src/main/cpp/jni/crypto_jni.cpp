#include <jni.h>
#include <string>
#include <vector>

#include "core/security/crypto/crypto.h"
#include "jni_util.h"

namespace {

std::vector<uint8_t> bytes(JNIEnv *env, jbyteArray a) {
    if (a == nullptr) return {};
    const jsize n = env->GetArrayLength(a);
    std::vector<uint8_t> v(static_cast<size_t>(n));
    if (n > 0) env->GetByteArrayRegion(a, 0, n, reinterpret_cast<jbyte *>(v.data()));
    return v;
} //jbyteArray → vector

jbyteArray back(JNIEnv *env, const std::string &s) {
    jbyteArray a = env->NewByteArray(static_cast<jsize>(s.size()));
    if (a == nullptr) return nullptr;
    if (!s.empty()) env->SetByteArrayRegion(a, 0, static_cast<jsize>(s.size()),
                                           reinterpret_cast<const jbyte *>(s.data()));
    return a;
} //std::string → jbyteArray
} //命名空间

extern "C" {
//插件侧要能自测：只有 16 字节密钥与 IV 才对，其他一律当调用错误
JNIEXPORT jbyteArray JNICALL
Java_b_ai_native_security_MiCrypto_nativeAes(JNIEnv *env, jobject, jbyteArray key, jbyteArray iv,
                                             jbyteArray data, jboolean encrypt) {
    const std::vector<uint8_t> k = bytes(env, key);
    const std::vector<uint8_t> v = bytes(env, iv);
    const std::vector<uint8_t> d = bytes(env, data);
    if (k.size() != 16 || v.size() != 16) return nullptr;
    std::string out;
    const bool ok = encrypt
                        ? bai::security::crypto::aes128CbcEncrypt(k.data(), v.data(), d.data(), d.size(), out)
                        : bai::security::crypto::aes128CbcDecrypt(k.data(), v.data(), d.data(), d.size(), out);
    return ok ? back(env, out) : nullptr;
} //AES-128-CBC 加解密

JNIEXPORT jbyteArray JNICALL
Java_b_ai_native_security_MiCrypto_nativeMd5(JNIEnv *env, jobject, jbyteArray data) {
    const std::vector<uint8_t> d = bytes(env, data);
    uint8_t out[16];
    if (!bai::security::crypto::md5(d.data(), d.size(), out)) return nullptr;
    return back(env, std::string(reinterpret_cast<const char *>(out), sizeof(out)));
} //MD5 原始 16 字节

JNIEXPORT jstring JNICALL
Java_b_ai_native_security_MiCrypto_nativeMd5Hex(JNIEnv *env, jobject, jbyteArray data) {
    const std::vector<uint8_t> d = bytes(env, data);
    return jni::back(env, bai::security::crypto::md5Hex(d.data(), d.size()));
} //MD5 十六进制

JNIEXPORT jstring JNICALL
Java_b_ai_native_security_MiCrypto_nativeSha1Hex(JNIEnv *env, jobject, jbyteArray data) {
    const std::vector<uint8_t> d = bytes(env, data);
    return jni::back(env, bai::security::crypto::sha1Hex(d.data(), d.size()));
} //SHA-1 十六进制

JNIEXPORT jboolean JNICALL
Java_b_ai_native_security_MiCrypto_nativeSelfTest(JNIEnv *, jobject) {
    return bai::security::crypto::selfTest() ? JNI_TRUE : JNI_FALSE;
} //自检入口
}
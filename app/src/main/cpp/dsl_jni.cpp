#include <jni.h>

#include <string>

#include "dsl.h"

namespace {

/**极简 JSON：只认 {"k":"v", ...} 这种扁平的字符串表，够传参数用，不引第三方库 */
bool parseObj(const std::string &s, dsl::Table &out) {
    size_t i = 0;
    const size_t n = s.size();
    auto ws = [&]() { while (i < n && (s[i] == ' ' || s[i] == '\t' || s[i] == '\n' || s[i] == '\r')) i++; };
    auto str = [&](std::string &dst) -> bool {
        if (i >= n || s[i] != '"') return false;
        i++;
        dst.clear();
        while (i < n && s[i] != '"') {
            char c = s[i];
            if (c != '\\') {
                dst += c;
                i++;
                continue;
            }
            i++;
            if (i >= n) return false;
            const char e = s[i++];
            switch (e) {
                case 'n': dst += '\n'; break;
                case 't': dst += '\t'; break;
                case 'r': dst += '\r'; break;
                case 'b': dst += '\b'; break;
                case 'f': dst += '\f'; break;
                case '/': dst += '/'; break;
                case '"': dst += '"'; break;
                case '\\': dst += '\\'; break;
                case 'u': {
                    //\uXXXX 只处理 BMP，够用；非法就原样留个 ?
                    if (i + 4 > n) return false;
                    unsigned cp = 0;
                    for (int k = 0; k < 4; k++) {
                        const char h = s[i + k];
                        cp <<= 4;
                        if (h >= '0' && h <= '9') cp |= (unsigned) (h - '0');
                        else if (h >= 'a' && h <= 'f') cp |= (unsigned) (h - 'a' + 10);
                        else if (h >= 'A' && h <= 'F') cp |= (unsigned) (h - 'A' + 10);
                        else return false;
                    }
                    i += 4;
                    if (cp < 0x80) {
                        dst += (char) cp;
                    } else if (cp < 0x800) {
                        dst += (char) (0xC0 | (cp >> 6));
                        dst += (char) (0x80 | (cp & 0x3F));
                    } else {
                        dst += (char) (0xE0 | (cp >> 12));
                        dst += (char) (0x80 | ((cp >> 6) & 0x3F));
                        dst += (char) (0x80 | (cp & 0x3F));
                    }
                    break;
                }
                default: dst += e; break;
            }
        }
        if (i >= n) return false;
        i++;
        return true;
    };
    ws();
    if (i >= n || s[i] != '{') return false;
    i++;
    ws();
    if (i < n && s[i] == '}') return true;
    while (i < n) {
        std::string k, v;
        ws();
        if (!str(k)) return false;
        ws();
        if (i >= n || s[i] != ':') return false;
        i++;
        ws();
        if (i < n && s[i] == 'n') {
            //null 当空串
            if (s.compare(i, 4, "null") == 0) i += 4;
            else return false;
        } else if (!str(v)) {
            return false;
        }
        out[k] = v;
        ws();
        if (i < n && s[i] == ',') {
            i++;
            continue;
        }
        if (i < n && s[i] == '}') return true;
        return false;
    }
    return true;
} //解析扁平对象

std::string jstr(JNIEnv *env, jstring js) {
    if (js == nullptr) return "";
    const char *p = env->GetStringUTFChars(js, nullptr);
    std::string out = p == nullptr ? "" : p;
    if (p != nullptr) env->ReleaseStringUTFChars(js, p);
    return out;
} //jstring → std::string

jstring back(JNIEnv *env, const std::string &s) {
    return env->NewStringUTF(s.c_str());
} //std::string → jstring

/**统一返回 "OK\n<文本>" 或 "ERR\n<错误>"，Kotlin 侧只看第一行 */
std::string pack(const dsl::Result &r) {
    return (r.ok ? std::string("OK\n") : std::string("ERR\n")) + r.text;
} //打包返回

} //匿名命名空间

extern "C" {

JNIEXPORT jstring JNICALL
Java_b_ai_core_Dsl_nativeLint(JNIEnv *env, jobject, jstring tpl, jstring kind, jstring params) {
    dsl::Table p;
    if (!parseObj(jstr(env, params), p)) return back(env, "ERR\n参数表不是合法 JSON");
    const dsl::Result r = dsl::lint(jstr(env, tpl), jstr(env, kind), p, nullptr);
    return back(env, pack(r));
} //加载期校验

JNIEXPORT jstring JNICALL
Java_b_ai_core_Dsl_nativeRender(JNIEnv *env, jobject, jstring tpl, jstring kind, jstring params,
                                jstring secrets, jstring args) {
    dsl::Table p, s, a;
    if (!parseObj(jstr(env, params), p)) return back(env, "ERR\n参数表不是合法 JSON");
    if (!parseObj(jstr(env, secrets), s)) return back(env, "ERR\nsecret 表不是合法 JSON");
    if (!parseObj(jstr(env, args), a)) return back(env, "ERR\n调用参数不是合法 JSON");
    const dsl::Result r = dsl::render(jstr(env, tpl), jstr(env, kind), p, s, a);
    return back(env, pack(r));
} //校验并生成

} // extern "C"
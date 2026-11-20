/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 22:50:10
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

#include "core/feature/archive/tarbz2.h"

#include <cstdio>
#include <cstring>
#include <string>
#include <vector>

#include "bzlib.h"

namespace {

constexpr int kBlock = 512;          // tar 的头部与数据都按 512 字节对齐
constexpr int kChunk = 64 * 1024;    // bz2 单次解压块的读缓冲

//tar 头部里的字符串字段：遇 \0 或字段末尾结束，顺手去掉尾部空格
std::string field(const unsigned char *h, int off, int len) {
    const char *p = reinterpret_cast<const char *>(h) + off;
    int n = 0;
    while (n < len && p[n] != '\0') n++;
    while (n > 0 && p[n - 1] == ' ') n--;
    return std::string(p, static_cast<size_t>(n));
} //取字符串字段

//八进制数字字段（size 等）；脏数据一律当 0，不让一个坏头把整个流程带崩
long long octal(const unsigned char *h, int off, int len) {
    long long v = 0;
    bool seen = false;
    for (int i = 0; i < len; i++) {
        unsigned char c = h[off + i];
        if (c == '\0') break;
        if (c == ' ') {
            if (seen) break;   //数字之后的空格表示字段结束
            continue;          //前导空格跳过
        }
        if (c < '0' || c > '7') break;
        v = v * 8 + static_cast<long long>(c - '0');
        seen = true;
    }
    return v;
} //取八进制字段

//只认 basename：tar 里的目录结构完全丢弃，杜绝 ../ 之类的路径穿越
std::string baseName(const std::string &name) {
    std::string s = name.substr(name.find_last_of('/') == std::string::npos ? 0 : name.find_last_of('/') + 1);
    if (s.empty() || s == "." || s == "..") return "";
    if (s.find("..") != std::string::npos) return "";
    return s;
} //取文件名

//bz2 流式读取器：只管按字节吐数据，tar 的结构解析放在外面
class BzReader {
public:
    explicit BzReader(const char *path) : fp_(std::fopen(path, "rb")) {
        if (fp_ == nullptr) return;
        int err = BZ_OK;
        bz_ = BZ2_bzReadOpen(&err, fp_, 0, 0, nullptr, 0);
        if (err != BZ_OK || bz_ == nullptr) {
            if (bz_ != nullptr) BZ2_bzReadClose(&err, bz_);
            bz_ = nullptr;
        }
    } //打开

    ~BzReader() {
        int err = BZ_OK;
        if (bz_ != nullptr) BZ2_bzReadClose(&err, bz_);
        if (fp_ != nullptr) std::fclose(fp_);
    } //关闭

    BzReader(const BzReader &) = delete;
    BzReader &operator=(const BzReader &) = delete;

    bool ok() const { return bz_ != nullptr; }
    bool bad() const { return bad_; }

    //读满 n 字节，除非流提前结束；返回实际读到的字节数
    size_t read(unsigned char *dst, size_t n) {
        size_t got = 0;
        while (got < n && bz_ != nullptr) {
            int want = static_cast<int>((n - got) > static_cast<size_t>(kChunk) ? kChunk : (n - got));
            int err = BZ_OK;
            int r = BZ2_bzRead(&err, bz_, dst + got, want);
            if (r > 0) got += static_cast<size_t>(r);
            if (err == BZ_STREAM_END) break;
            if (err != BZ_OK || r <= 0) {
                bad_ = true;
                break;
            }
        }
        return got;
    } //读数据

private:
    FILE *fp_ = nullptr;
    BZFILE *bz_ = nullptr;
    bool bad_ = false;
}; //bz2 流

//读一整段数据体并把 512 对齐的填充字节丢掉；out 非空时顺便收下来（给 GNU 长名用）
bool readBody(BzReader &rd, long long size, long long padded, std::string *out) {
    unsigned char buf[kChunk];
    long long left = size;
    while (left > 0) {
        size_t want = static_cast<size_t>(left > kChunk ? kChunk : left);
        size_t got = rd.read(buf, want);
        if (static_cast<long long>(got) != static_cast<long long>(want)) return false;
        if (out != nullptr) out->append(reinterpret_cast<const char *>(buf), got);
        left -= static_cast<long long>(got);
    }
    long long pad = padded - size;
    while (pad > 0) {
        size_t want = static_cast<size_t>(pad > kChunk ? kChunk : pad);
        size_t got = rd.read(buf, want);
        if (static_cast<long long>(got) != static_cast<long long>(want)) return false;
        pad -= static_cast<long long>(got);
    }
    return true;
} //丢弃/收集数据体

//边读边写：先写 .part 再改名，中途失败不留半份「看起来正常」的模型
bool dumpFile(BzReader &rd, long long size, long long padded, const std::string &path) {
    std::string tmp = path + ".part";
    FILE *f = std::fopen(tmp.c_str(), "wb");
    if (f == nullptr) return false;
    unsigned char buf[kChunk];
    long long left = size;
    bool ok = true;
    while (left > 0) {
        size_t want = static_cast<size_t>(left > kChunk ? kChunk : left);
        size_t got = rd.read(buf, want);
        if (static_cast<long long>(got) != static_cast<long long>(want)) { ok = false; break; }
        if (std::fwrite(buf, 1, got, f) != got) { ok = false; break; }
        left -= static_cast<long long>(got);
    }
    long long pad = padded - size;
    while (ok && pad > 0) {
        size_t want = static_cast<size_t>(pad > kChunk ? kChunk : pad);
        size_t got = rd.read(buf, want);
        if (static_cast<long long>(got) != static_cast<long long>(want)) { ok = false; break; }
        pad -= static_cast<long long>(got);
    }
    std::fclose(f);
    if (!ok) {
        std::remove(tmp.c_str());
        return false;
    }
    std::remove(path.c_str());
    return std::rename(tmp.c_str(), path.c_str()) == 0;
} //流式写文件

//是不是块全零（tar 用全零块表示归档结束）
bool allZero(const unsigned char *h) {
    for (int i = 0; i < kBlock; i++) {
        if (h[i] != 0) return false;
    }
    return true;
} //全零块判定

} //命名空间

extern "C" int bai_tar_bz2_extract(const char *src, const char *dstDir, const char *const *names, int count) {
    if (src == nullptr || dstDir == nullptr || count <= 0) return -1;

    std::vector<std::string> want;
    want.reserve(static_cast<size_t>(count));
    for (int i = 0; i < count; i++) {
        if (names[i] != nullptr && names[i][0] != '\0') want.emplace_back(names[i]);
    }
    if (want.empty()) return -1;

    BzReader rd(src);
    if (!rd.ok()) return -1;

    std::string dir = dstDir;
    if (dir.empty() || dir.back() != '/') dir.push_back('/');

    int written = 0;
    std::string pending;   //GNU 长名（type 'L'）暂存
    unsigned char hdr[kBlock];

    while (true) {
        size_t got = rd.read(hdr, kBlock);
        if (got == 0) break;                        //流结束
        if (got < static_cast<size_t>(kBlock)) return -3;
        if (allZero(hdr)) break;                    //归档结束

        char type = static_cast<char>(hdr[156]);
        long long size = octal(hdr, 124, 12);
        long long padded = (size + kBlock - 1) / kBlock * kBlock;

        std::string name;
        if (type == 'L') {
            //GNU 长名：本块的「数据」就是下一个 entry 的真实名字
            std::string ln;
            if (!readBody(rd, size, padded, &ln)) return rd.bad() ? -2 : -3;
            size_t z = ln.find('\0');
            pending = z == std::string::npos ? ln : ln.substr(0, z);
            continue;
        }

        std::string prefix = field(hdr, 345, 155);
        name = field(hdr, 0, 100);
        if (!prefix.empty()) name = prefix + "/" + name;
        if (!pending.empty()) {
            name = pending;
            pending.clear();
        }

        //只处理普通文件；目录（'5'）、PAX 头（'x'）、符号链接一律跳过
        if (type != '0' && type != '\0') {
            if (!readBody(rd, size, padded, nullptr)) return rd.bad() ? -2 : -3;
            continue;
        }

        std::string shortName = baseName(name);
        bool hit = false;
        for (const auto &w : want) {
            if (w == shortName) { hit = true; break; }
        }
        if (!hit) {
            if (!readBody(rd, size, padded, nullptr)) return rd.bad() ? -2 : -3;
            continue;
        }

        if (!dumpFile(rd, size, padded, dir + shortName)) return rd.bad() ? -2 : -4;
        written++;
    } //逐条目解析

    if (rd.bad()) return -2;
    return written;
} //tar.bz2 选择性解包
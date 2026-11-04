#pragma once
// 字节级工具：全部按字节推进，不切 UTF-8 字符边界，中文原样透传
#include <cstddef>
#include <string>
#include <vector>

namespace bai::base {

// 小写化：只认 ASCII 大写字母
constexpr unsigned char lower(unsigned char b) {
    return (b >= 'A' && b <= 'Z') ? static_cast<unsigned char>(b + 32) : b;
}

// 是不是 ASCII 数字
constexpr bool digit(unsigned char b) { return b >= '0' && b <= '9'; }

// 是不是 ASCII 大写字母
constexpr bool upper(unsigned char b) { return b >= 'A' && b <= 'Z'; }

// 是不是 ASCII 小写字母
constexpr bool lowerCh(unsigned char b) { return b >= 'a' && b <= 'z'; }

// 是不是标识符字符：字母 / 数字 / 下划线
inline bool ident(unsigned char b) { return digit(b) || upper(b) || lowerCh(b) || b == '_'; }

// 是不是可打印 ASCII
inline bool printable(unsigned char b) { return b >= 0x20 && b < 0x7f; }

// 字节是否落在给定集合里
inline bool inSet(unsigned char b, const char *set) { return set != nullptr && std::char_traits<char>::find(set, std::char_traits<char>::length(set), static_cast<char>(b)) != nullptr; }

// src 从 at 起是否以 pat 开头（忽略大小写）
bool startsCi(const std::string &src, size_t at, const std::string &pat);

// 从 at 起找 sub 第一次出现的位置，找不到返回 npos
size_t findSub(const std::string &src, size_t at, const char *sub);

} //字节工具

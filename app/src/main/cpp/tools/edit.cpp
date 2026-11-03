#include "edit.h"

#include <vector>

namespace {

bool blankChar(char c) { return c == ' ' || c == '\t'; }

std::string trimRight(const std::string &s) {
    size_t e = s.size();
    while (e > 0 && blankChar(s[e - 1])) e--;
    return s.substr(0, e);
} //去掉行尾空白

std::string lead(const std::string &s) {
    size_t i = 0;
    while (i < s.size() && blankChar(s[i])) i++;
    return s.substr(0, i);
} //取前导缩进

std::string core(const std::string &s) {
    const std::string t = trimRight(s);
    size_t i = 0;
    while (i < t.size() && blankChar(t[i])) i++;
    return t.substr(i);
} //正文：缩进与行尾空白都去掉

std::vector<std::string> split(const std::string &s) {
    std::vector<std::string> out;
    std::string cur;
    for (char c : s) {
        if (c == '\n') {
            out.push_back(cur);
            cur.clear();
        } else if (c != '\r') {
            cur.push_back(c);
        }
    }
    out.push_back(cur);
    return out;
} //按行拆，顺手吃掉 \r

std::string fail(const std::string &m) { return "ERR\t" + m; }

} //匿名命名空间

namespace edit {

std::string find(const std::string &src, const std::string &oldBlock) {
    if (src.empty()) return fail("文件是空的，直接用 write_file 写");
    std::vector<std::string> o = split(oldBlock);
    //模型经常在 old 前后多带空行，这里自动丢掉，不用它自己数
    while (!o.empty() && core(o.front()).empty()) o.erase(o.begin());
    while (!o.empty() && core(o.back()).empty()) o.pop_back();
    if (o.empty()) return fail("old 是空代码块，至少要给一行原文");

    std::vector<std::string> l = split(src);
    std::vector<size_t> hits;
    if (o.size() <= l.size()) {
        for (size_t i = 0; i + o.size() <= l.size(); i++) {
            bool same = true;
            for (size_t k = 0; k < o.size(); k++) {
                if (core(l[i + k]) != core(o[k])) {
                    same = false;
                    break;
                }
            }
            if (same) hits.push_back(i);
        }
    }
    if (hits.empty()) {
        //一行都没命中时把最像的一行指出来，省掉一轮瞎猜
        std::string key = core(o.front());
        if (key.size() > 48) key = key.substr(0, 48);
        for (size_t i = 0; i < l.size(); i++) {
            if (!key.empty() && l[i].find(key) != std::string::npos) {
                return fail("没找到这段代码；第 " + std::to_string(i + 1) +
                            " 行有相似内容，先 read_file 看清现文再改");
            }
        }
        return fail("没找到这段代码（缩进和行尾空白会自动忽略，其余必须一致）");
    }
    if (hits.size() > 1) {
        std::string where;
        for (size_t i = 0; i < hits.size() && i < 6; i++) {
            if (i) where += "、";
            where += std::to_string(hits[i] + 1);
        }
        return fail("这段代码出现 " + std::to_string(hits.size()) + " 次（第 " + where +
                    " 行），old 要带更多上下文才唯一");
    }

    const size_t start = hits[0];
    //分隔符用 SOH：缩进里可能有 tab，不能用 \t 切
    const std::string sep = "\x01";
    return "OK" + sep + std::to_string(start) + sep + std::to_string(o.size()) + sep +
           lead(l[start]) + sep + lead(o.front());
} //按行定位唯一位置

} //命名空间 edit
#!/usr/bin/env python3
#生成 R8 混淆字典：单字母打头，再补两位短名（a1/a2/ab 这种），保证全是合法 Java 标识符
import os

ROOT = "/storage/emulated/0/AndroidCSProjects/BaiAgent"
OUT = os.path.join(ROOT, "app/obfuscation_dict.txt")

#Java 关键字 + 保留型标识符，混进字典会让 R8 直接报 invalid name
RESERVED = {
    "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
    "class", "const", "continue", "default", "do", "double", "else", "enum",
    "extends", "final", "finally", "float", "for", "goto", "if", "implements",
    "import", "instanceof", "int", "interface", "long", "native", "new",
    "package", "private", "protected", "public", "return", "short", "static",
    "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
    "transient", "try", "void", "volatile", "while", "true", "false", "null",
    "var", "record", "yield",
}

LETTERS = "abcdefghijklmnopqrstuvwxyz"
TAIL = "abcdefghijklmnopqrstuvwxyz0123456789"

names = []
for c in LETTERS:
    if c not in RESERVED:
        names.append(c)
for c in LETTERS:
    for t in TAIL:
        n = c + t
        if n not in RESERVED:
            names.append(n)

with open(OUT, "w", encoding="utf-8") as f:
    f.write("\n".join(names) + "\n")

print("写出 %d 条 -> %s" % (len(names), OUT))
print("FQZ".replace("FQZ", "前 8 条: ") + ", ".join(names[:8]))
print("末尾几条: " + ", ".join(names[-6:]))

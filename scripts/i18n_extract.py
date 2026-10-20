#!/usr/bin/env python3
#扫描 Kotlin 源码里所有含中文的字符串字面量，导出清单给多语言迁移用
import os, re, sys, json

ROOT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'app/src/main/kotlin')
OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), '.docs/i18n-strings.tsv')
HAN = re.compile(r'[\u3400-\u9fff\uf900-\ufaff]')
#单行字符串（含转义）与 KDoc 一并跳过：只收代码里的字面量
LIT = re.compile(r'"((?:[^"\\]|\\.)*)"')

def scan():
    rows = []
    for dp, dn, fn in os.walk(ROOT):
        for f in sorted(fn):
            if not f.endswith('.kt'):
                continue
            p = os.path.join(dp, f)
            rel = os.path.relpath(p, os.path.dirname(ROOT))
            with open(p, encoding='utf-8') as fh:
                for no, line in enumerate(fh, 1):
                    code = line.split('//')[0]
                    for m in LIT.finditer(code):
                        s = m.group(1)
                        if HAN.search(s):
                            rows.append((rel, no, s))
    return rows

if __name__ == '__main__':
    rows = scan()
    with open(OUT, 'w', encoding='utf-8') as fh:
        fh.write('file\tline\ttext\n')
        for rel, no, s in rows:
            fh.write('%s\t%d\t%s\n' % (rel, no, s))
    per = {}
    for rel, no, s in rows:
        per[rel] = per.get(rel, 0) + 1
    print('含中文字面量 %d 条，涉及 %d 个文件' % (len(rows), len(per)))
    print('清单已写入 ' + OUT)
    for rel, n in sorted(per.items(), key=lambda x: -x[1])[:15]:
        print('%5d  %s' % (n, rel))

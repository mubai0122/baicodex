#!/usr/bin/env python3
#校验 5 套 strings.xml 的 key 是否一一对齐：缺 key / 多 key 都报出来
import io
import os
import re

ROOT = '/storage/emulated/0/AndroidCSProjects/BaiAgent/app/src/main/res'
LANGS = ['values', 'values-zh-rTW', 'values-en', 'values-ja', 'values-fr']
BASE = 'values'


def keys_of(lang):
    p = os.path.join(ROOT, lang, 'strings.xml')
    s = io.open(p, encoding='utf-8').read()
    return set(re.findall(r'<string name="([^"]+)"', s))


def main():
    base = keys_of(BASE)
    print('%s: %d 条' % (BASE, len(base)))
    bad = 0
    for lang in LANGS[1:]:
        k = keys_of(lang)
        miss = sorted(base - k)
        extra = sorted(k - base)
        print('%s: %d 条，缺 %d，多 %d' % (lang, len(k), len(miss), len(extra)))
        for m in miss:
            print('  缺', m)
        for e in extra:
            print('  多', e)
        bad += len(miss) + len(extra)
    print('总计问题: %d' % bad)


main()
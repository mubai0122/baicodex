#!/usr/bin/env python3
#往 5 份 strings.xml 追加资源：rows = [(key, 简中, 繁中, en, ja, fr)]，已存在的 key 跳过
import io
import os

ROOT = '/storage/emulated/0/AndroidCSProjects/BaiAgent'
RES = ROOT + '/app/src/main/res'
LANGS = ['values', 'values-zh-rTW', 'values-en', 'values-ja', 'values-fr']


def esc(s):
    return (s.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
            .replace('"', '\\"').replace("'", "\\'"))


def add(rows, verbose=True):
    total = 0
    for i, lang in enumerate(LANGS):
        p = os.path.join(RES, lang, 'strings.xml')
        s = io.open(p, encoding='utf-8').read()
        adds = []
        for row in rows:
            key, val = row[0], row[1 + i]
            if ('name="%s"' % key) in s:
                continue
            adds.append('    <string name="%s">%s</string>' % (key, esc(val)))
        if adds:
            s = s.replace('</resources>', '\n'.join(adds) + '\n</resources>')
            io.open(p, 'w', encoding='utf-8').write(s)
            total += len(adds)
        if verbose:
            print(lang, '+%d' % len(adds))
    return total

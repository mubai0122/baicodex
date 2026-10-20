#!/usr/bin/env python3
#批量版：一张表可以同时改多个 Kotlin 文件
# 表格式（TAB）：mode literal key 简中 繁中 en ja fr [目标文件相对 app/src/main/kotlin/b/ai/ 的路径]
#无法直接替换的（带插值、跨行拼接）把 literal 写成 #占位符，资源照样写进 strings.xml
import io
import os

ROOT = '/storage/emulated/0/AndroidCSProjects/BaiAgent'
KTSRC = ROOT + '/app/src/main/kotlin/b/ai/'
RES = ROOT + '/app/src/main/res'
LANGS = ['values', 'values-zh-rTW', 'values-en', 'values-ja', 'values-fr']


def esc(s):
    return (s.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
            .replace('"', '\\"').replace("'", "\\'"))


def main():
    import sys
    table = sys.argv[1]
    rows = []
    for line in io.open(table, encoding='utf-8').read().splitlines():
        if not line.strip() or (line.startswith('#') and '\t' not in line):
            continue
        p = line.split('\t')
        if len(p) < 9:
            print('BAD ROW:', line[:50])
            continue
        rows.append(p[:9])

    groups = {}
    for row in rows:
        groups.setdefault(row[8].strip(), []).append(row)

    for rel, grows in groups.items():
        path = KTSRC + rel
        if not os.path.exists(path):
            print('NO FILE', rel)
            continue
        src = io.open(path, encoding='utf-8').read()
        need_s = False
        for row in grows:
            mode, lit, key = row[0], row[1], row[2]
            old = '"' + lit + '"'
            new = ('stringResource(R.string.%s)' if mode == 's' else 'ctx.getString(R.string.%s)') % key
            n = src.count(old)
            if n == 0:
                print('MISS', rel, key)
                continue
            src = src.replace(old, new)
            print('OK', n, rel, key)
            if mode == 's':
                need_s = True
        lines = src.split('\n')

        def add_import(fq):
            if any(l.strip() == 'import ' + fq for l in lines):
                return
            idx = [i for i, l in enumerate(lines) if l.startswith('import ')]
            lines.insert(idx[-1] + 1, 'import ' + fq)
        if need_s:
            add_import('androidx.compose.ui.res.stringResource')
        add_import('b.ai.R')
        io.open(path, 'w', encoding='utf-8').write('\n'.join(lines))

    for i, lang in enumerate(LANGS):
        p = os.path.join(RES, lang, 'strings.xml')
        s = io.open(p, encoding='utf-8').read()
        add = []
        for row in rows:
            key, val = row[2], row[3 + i]
            if ('name="%s"' % key) in s:
                continue
            add.append('    <string name="%s">%s</string>' % (key, esc(val)))
        if add:
            s = s.replace('</resources>', '\n'.join(add) + '\n</resources>')
            io.open(p, 'w', encoding='utf-8').write(s)
        print(lang, '+%d' % len(add))


main()
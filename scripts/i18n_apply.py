#!/usr/bin/env python3
#把一份映射表应用到某个 Kotlin 文件，并把 5 套译文写进 strings.xml
# 用法: python3 scripts/i18n_apply.py <表.tsv> <目标.kt>
# 表格式（TAB 分隔，无表头）: mode  literal  key  简中  繁中  en  ja  fr
#   mode=s  -> 替换成 stringResource(R.string.key)   （只在 @Composable 作用域里用）
#   mode=c  -> 替换成 ctx.getString(R.string.key)     （非 Composable 作用域）
import io
import os
import sys

ROOT = '/storage/emulated/0/AndroidCSProjects/BaiAgent'
RES = os.path.join(ROOT, 'app/src/main/res')
LANGS = ['values', 'values-zh-rTW', 'values-en', 'values-ja', 'values-fr']


def esc(s):
    #Android strings.xml 转义：& < > 与引号
    return (s.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
            .replace('"', '\\"').replace("'", "\\'"))


def main():
    table, target = sys.argv[1], sys.argv[2]
    rows = []
    for line in io.open(table, encoding='utf-8').read().splitlines():
        if not line.strip() or (line.startswith('#') and '\t' not in line):
            continue
        p = line.split('\t')
        if len(p) < 8:
            print('BAD ROW:', line)
            continue
        rows.append(p[:8])

    #target 传 - 表示只写 strings.xml，不改源码（整份重写的文件用这个）
    if target == '-':
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
        return

    src = io.open(target, encoding='utf-8').read()
    need_s = need_c = False
    for mode, lit, key, zh, tw, en, ja, fr in rows:
        old = '"' + lit + '"'
        new = ('stringResource(R.string.%s)' if mode == 's' else 'ctx.getString(R.string.%s)') % key
        n = src.count(old)
        if n == 0:
            print('MISS', key, '->', lit)
            continue
        src = src.replace(old, new)
        print('OK', n, key)
        if mode == 's':
            need_s = True
        else:
            need_c = True

    lines = src.split('\n')
    def add_import(fq):
        if any(l.strip() == 'import ' + fq for l in lines):
            return
        idx = [i for i, l in enumerate(lines) if l.startswith('import ')]
        lines.insert(idx[-1] + 1, 'import ' + fq)
    if need_s:
        add_import('androidx.compose.ui.res.stringResource')
    add_import('b.ai.R')
    io.open(target, 'w', encoding='utf-8').write('\n'.join(lines))

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
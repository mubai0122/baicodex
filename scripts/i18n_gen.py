#!/usr/bin/env python3
#把「编号 字面量 繁中 en ja fr」5-6 列表转成 i18n_multi 要的 9 列表
#用法：python3 i18n_gen.py 输入.tsv 输出.tsv 目标文件 key前缀
import io
import sys

src, dst, target, prefix = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
rows = []
for line in io.open(src, encoding='utf-8').read().splitlines():
    if not line.strip():
        continue
    p = line.split('\t')
    if len(p) < 6:
        print('BAD', line[:40])
        continue
    idx, lit, tw, en, ja, fr = p[0], p[1], p[2], p[3], p[4], p[5]
    key = '%s%s' % (prefix, idx)
    rows.append('\t'.join(['s', lit, key, lit, tw, en, ja, fr, target]))
io.open(dst, 'w', encoding='utf-8').write('\n'.join(rows) + '\n')
print('rows', len(rows))
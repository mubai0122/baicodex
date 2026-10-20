#!/usr/bin/env python3
#把 i18n-strings.tsv 里某文件的字面量去重后按出现顺序列出，带可疑标记
import io
import sys

ROOT = '/storage/emulated/0/AndroidCSProjects/BaiAgent'
target = sys.argv[1]
out = []
seen = set()
for line in io.open(ROOT + '/.docs/i18n-strings.tsv', encoding='utf-8').read().splitlines():
    p = line.split('\t')
    if len(p) < 3 or target not in p[0]:
        continue
    lit = p[2]
    if lit in seen:
        continue
    seen.add(lit)
    flag = '# ' if ('${' in lit or '" +' in lit or lit.startswith('" +')) else ''
    out.append('%03d\t%s%s' % (len(out) + 1, flag, lit))
io.open(ROOT + '/.tmp/uniq.txt', 'w', encoding='utf-8').write('\n'.join(out) + '\n')
print('unique', len(out))
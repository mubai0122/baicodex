#!/usr/bin/env python3
#给统计页的价格/费用补 9 条文案 × 5 语言
import os, re

BASE = '/storage/emulated/0/AndroidCSProjects/BaiAgent/app/src/main/res'

T = {
    'values': {
        'st_price': '模型价格',
        'st_price_d': '元 / 百万 token；填 0 表示不计价，只统计 token',
        'st_price_in': '输入',
        'st_price_out': '输出',
        'st_price_cache': '缓存命中',
        'st_price_note': '改完立即按新价格重算；缓存命中按缓存价算，不重复计输入价',
        'st_cost_total': '累计费用 %1$s',
        'st_cost_range': '本区间费用 %1$s',
        'st_cost_off': '未设置价格，费用不计',
    },
    'values-zh-rTW': {
        'st_price': '模型價格',
        'st_price_d': '元 / 百萬 token；填 0 表示不計價，只統計 token',
        'st_price_in': '輸入',
        'st_price_out': '輸出',
        'st_price_cache': '快取命中',
        'st_price_note': '改完立即按新價格重算；快取命中按快取價算，不重複計輸入價',
        'st_cost_total': '累計費用 %1$s',
        'st_cost_range': '本區間費用 %1$s',
        'st_cost_off': '未設定價格，費用不計',
    },
    'values-en': {
        'st_price': 'Model pricing',
        'st_price_d': 'CNY per million tokens; leave 0 to count tokens only',
        'st_price_in': 'Input',
        'st_price_out': 'Output',
        'st_price_cache': 'Cache hit',
        'st_price_note': 'Cost is recalculated as you type; cache hits use the cache price and are not billed as input again',
        'st_cost_total': 'Total cost %1$s',
        'st_cost_range': 'Cost in range %1$s',
        'st_cost_off': 'No pricing set, cost not counted',
    },
    'values-ja': {
        'st_price': 'モデル価格',
        'st_price_d': '元 / 100万トークン。0 の場合は料金を計算せずトークンのみ集計',
        'st_price_in': '入力',
        'st_price_out': '出力',
        'st_price_cache': 'キャッシュ命中',
        'st_price_note': '入力すると即座に新価格で再計算。キャッシュ命中分はキャッシュ価格で計算し、入力価格とは二重計上しない',
        'st_cost_total': '累計費用 %1$s',
        'st_cost_range': 'この期間の費用 %1$s',
        'st_cost_off': '価格が未設定のため費用は算出しません',
    },
    'values-fr': {
        'st_price': 'Tarif du modèle',
        'st_price_d': 'CNY par million de tokens ; 0 = compter seulement les tokens',
        'st_price_in': 'Entrée',
        'st_price_out': 'Sortie',
        'st_price_cache': 'Cache',
        'st_price_note': 'Le coût est recalculé à chaque saisie ; les hits de cache utilisent le tarif cache et ne sont pas refacturés en entrée',
        'st_cost_total': 'Coût total %1$s',
        'st_cost_range': 'Coût de la période %1$s',
        'st_cost_off': 'Aucun tarif défini, coût non calculé',
    },
}

def esc(s):
    return s.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')

total = 0
for folder, kv in T.items():
    p = os.path.join(BASE, folder, 'strings.xml')
    s = open(p, encoding='utf-8').read()
    add = []
    for k, v in kv.items():
        if ('name="%s"' % k) in s:
            continue
        add.append('    <string name="%s">%s</string>' % (k, esc(v)))
    if add:
        s = s.replace('</resources>', '\n'.join(add) + '\n</resources>')
        open(p, 'w', encoding='utf-8').write(s)
    total += len(add)
    print(folder, 'added', len(add))
print('total', total)
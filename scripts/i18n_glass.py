#!/usr/bin/env python3
#给「质感与玻璃」区块补 13 条文案 × 5 语言
import os

BASE = '/storage/emulated/0/AndroidCSProjects/BaiAgent/app/src/main/res'

T = {
    'values': {
        'se_glass_t': '质感与玻璃',
        'se_glass_d': '卡片、按钮、底栏的立体感与磨砂质感',
        'se_glass_card': '液态玻璃卡片',
        'se_glass_card_d': '卡片换成半透玻璃，手指按下的位置会浮出一团柔光',
        'se_tactile': '开启质感按钮',
        'se_tactile_d': '深度思考、最大、语音、发送改成带厚度的立体按钮',
        'se_glass_bar': '液态玻璃底栏',
        'se_glass_bar_d': '设置页底部那条悬浮胶囊换成玻璃质感',
        'se_bg': '页面背景',
        'se_bg_d': '聊天与设置页的底色样式',
        'se_bg_plain': '纯色',
        'se_bg_grad': '柔光渐变',
        'se_bg_dots': '点阵',
    },
    'values-zh-rTW': {
        'se_glass_t': '質感與玻璃',
        'se_glass_d': '卡片、按鈕、底欄的立體感與磨砂質感',
        'se_glass_card': '液態玻璃卡片',
        'se_glass_card_d': '卡片換成半透玻璃，手指按下的位置會浮出一團柔光',
        'se_tactile': '開啟質感按鈕',
        'se_tactile_d': '深度思考、最大、語音、傳送改成帶厚度的立體按鈕',
        'se_glass_bar': '液態玻璃底欄',
        'se_glass_bar_d': '設定頁底部那條懸浮膠囊換成玻璃質感',
        'se_bg': '頁面背景',
        'se_bg_d': '聊天與設定頁的底色樣式',
        'se_bg_plain': '純色',
        'se_bg_grad': '柔光漸層',
        'se_bg_dots': '點陣',
    },
    'values-en': {
        'se_glass_t': 'Depth & glass',
        'se_glass_d': 'Lift and frosted finish for cards, buttons and the tab bar',
        'se_glass_card': 'Liquid glass cards',
        'se_glass_card_d': 'Cards become translucent glass with a soft light that follows your finger',
        'se_tactile': 'Tactile buttons',
        'se_tactile_d': 'Thinking, max, voice and send switch to raised 3D buttons',
        'se_glass_bar': 'Liquid glass tab bar',
        'se_glass_bar_d': 'Turn the floating settings tab bar into frosted glass',
        'se_bg': 'Page background',
        'se_bg_d': 'Base backdrop for chat and settings',
        'se_bg_plain': 'Solid',
        'se_bg_grad': 'Soft gradient',
        'se_bg_dots': 'Dots',
    },
    'values-ja': {
        'se_glass_t': '質感とガラス',
        'se_glass_d': 'カード・ボタン・タブバーの立体感とすりガラス調',
        'se_glass_card': 'リキッドグラスカード',
        'se_glass_card_d': 'カードを半透明ガラスにし、押した位置に柔らかい光が広がります',
        'se_tactile': '質感ボタンを有効化',
        'se_tactile_d': '深く考える・最大・音声・送信を厚みのある立体ボタンに変更',
        'se_glass_bar': 'リキッドグラス・タブバー',
        'se_glass_bar_d': '設定画面下部の浮遊カプセルをガラス調にします',
        'se_bg': 'ページ背景',
        'se_bg_d': 'チャットと設定画面の背景スタイル',
        'se_bg_plain': '単色',
        'se_bg_grad': '柔らかいグラデーション',
        'se_bg_dots': 'ドット',
    },
    'values-fr': {
        'se_glass_t': 'Relief et verre',
        'se_glass_d': 'Relief et fini dépoli pour les cartes, les boutons et la barre',
        'se_glass_card': 'Cartes en verre liquide',
        'se_glass_card_d': 'Les cartes deviennent du verre translucide, avec une lueur qui suit le doigt',
        'se_tactile': 'Boutons tactiles',
        'se_tactile_d': 'Réflexion, max, voix et envoi passent en boutons 3D en relief',
        'se_glass_bar': 'Barre en verre liquide',
        'se_glass_bar_d': 'Transforme la barre flottante des réglages en verre dépoli',
        'se_bg': 'Fond de page',
        'se_bg_d': 'Style de fond pour le chat et les réglages',
        'se_bg_plain': 'Uni',
        'se_bg_grad': 'Dégradé doux',
        'se_bg_dots': 'Pointillés',
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

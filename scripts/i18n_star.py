#!/usr/bin/env python3
#把消息收藏相关文案补进 5 份 strings.xml（已存在同名 key 就原地替换，可重复跑）
import io
import os
import re

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'app', 'src', 'main', 'res')
LANGS = ['values', 'values-zh-rTW', 'values-en', 'values-ja', 'values-fr']

#key, 简中, 繁中, en, ja, fr
ROWS = [
    ('dw_stars', '收藏', '收藏', 'Starred', 'お気に入り', 'Favoris'),
    ('star_title', '收藏夹', '收藏夾', 'Starred', 'お気に入り', 'Favoris'),
    (
        'star_empty',
        '还没有收藏的消息：长按任意消息，选「收藏」即可',
        '還沒有收藏的訊息：長按任意訊息，選「收藏」即可',
        'Nothing starred yet — long-press a message and pick Star',
        'まだお気に入りがありません。メッセージを長押しして「お気に入り」を選んでください',
        'Aucun favori — appuyez longuement sur un message et choisissez Favori'
    ),
    ('star_untitled', '未命名会话', '未命名會話', 'Untitled chat', '無題のチャット', 'Conversation sans titre'),
    ('star_unstar', '取消收藏', '取消收藏', 'Remove', '解除', 'Retirer'),
    ('cs_star', '收藏', '收藏', 'Star', 'お気に入り', 'Favori'),
    (
        'cs_star_desc',
        '收进收藏夹，之后从侧边栏「收藏」翻回来',
        '收進收藏夾，之後從側邊欄「收藏」翻回來',
        'Save to starred; reopen it later from Starred in the drawer',
        'お気に入りに保存し、あとでサイドバーの「お気に入り」から開けます',
        'Enregistrer dans les favoris, retrouvable depuis le tiroir'
    ),
    ('cs_star_off', '取消收藏', '取消收藏', 'Unstar', 'お気に入り解除', 'Retirer des favoris'),
    (
        'cs_star_off_desc',
        '把这条从收藏夹里移除',
        '把這則從收藏夾移除',
        'Remove this message from starred',
        'このメッセージをお気に入りから外します',
        'Retirer ce message des favoris'
    ),
    ('cs_speak', '朗读', '朗讀', 'Read aloud', '読み上げ', 'Lire à voix haute'),
    (
        'cs_speak_desc',
        '用系统语音把这条念一遍',
        '用系統語音把這則唸一遍',
        'Read this message with system TTS',
        'システム音声でこのメッセージを読み上げます',
        'Lire ce message avec la synthèse vocale'
    ),
    ('cs_del_msg', '删除这条', '刪除這則', 'Delete message', 'このメッセージを削除', 'Supprimer ce message'),
    (
        'cs_del_msg_desc',
        '从当前会话里移除，不可恢复',
        '從目前會話移除，不可復原',
        'Remove it from this chat; cannot be undone',
        'このチャットから削除します（元に戻せません）',
        'Le retirer de cette conversation, sans retour possible'
    ),
    ('cs_del_msg_confirm', '再点一次确认删除', '再點一次確認刪除', 'Tap again to delete', 'もう一度押すと削除', 'Touchez encore pour supprimer')
]


def esc(s):
    return s.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')


def main():
    total = 0
    for i, lang in enumerate(LANGS):
        p = os.path.join(BASE, lang, 'strings.xml')
        s = io.open(p, encoding='utf-8').read()
        add = []
        for row in ROWS:
            key = row[0]
            line = '    <string name="%s">%s</string>' % (key, esc(row[i + 1]))
            pat = re.compile(r'[ \t]*<string name="%s">.*?</string>' % re.escape(key))
            if pat.search(s):
                s = pat.sub(line, s, count=1)
            else:
                add.append(line)
        if add:
            s = s.replace('</resources>', '\n'.join(add) + '\n</resources>')
            total += len(add)
        io.open(p, 'w', encoding='utf-8').write(s)
        print(lang, '新增', len(add), '条')
    print('共插入', total, '条')


if __name__ == '__main__':
    main()
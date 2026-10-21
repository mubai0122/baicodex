#!/usr/bin/env python3
#把 MCP 相关文案补进 5 份 strings.xml（已存在同名 key 就原地替换，可重复跑）
import io
import os
import re

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'app', 'src', 'main', 'res')
LANGS = ['values', 'values-zh-rTW', 'values-en', 'values-ja', 'values-fr']

#key, 简中, 繁中, en, ja, fr
ROWS = [
    ('mcp_title', 'MCP 服务', 'MCP 服務', 'MCP servers', 'MCP サーバー', 'Serveurs MCP'),
    (
        'mcp_sub',
        '连上本地或远程的 MCP 服务，把它的能力交给模型调用',
        '連上本機或遠端的 MCP 服務，把它的能力交給模型呼叫',
        'Connect local or remote MCP servers and let the model call their tools',
        'ローカルまたはリモートの MCP サーバーに接続し、その機能をモデルに使わせます',
        'Connectez des serveurs MCP locaux ou distants et laissez le modèle appeler leurs outils'
    ),
    ('mcp_none', '还没有 MCP 服务', '還沒有 MCP 服務', 'No MCP server yet', 'MCP サーバーはまだありません', 'Aucun serveur MCP'),
    (
        'mcp_none_sub',
        '点下面「新增服务」填地址；Termux 的原生 MCP 一般是 http://127.0.0.1:8081/mcp',
        '點下方「新增服務」填位址；Termux 原生 MCP 通常是 http://127.0.0.1:8081/mcp',
        'Tap Add server below and fill the address; Termux native MCP is usually http://127.0.0.1:8081/mcp',
        '下の「サーバーを追加」でアドレスを入力。Termux のネイティブ MCP は通常 http://127.0.0.1:8081/mcp',
        'Touchez Ajouter un serveur ci-dessous ; le MCP natif de Termux est en général http://127.0.0.1:8081/mcp'
    ),
    ('mcp_add', '新增服务', '新增服務', 'Add server', 'サーバーを追加', 'Ajouter un serveur'),
    ('mcp_count_all', '共 %1$d 项能力', '共 %1$d 項能力', '%1$d tools in total', '合計 %1$d 件', '%1$d outils au total'),
    (
        'mcp_row_sub',
        '%1$s · %2$s · %3$d 项能力',
        '%1$s · %2$s · %3$d 項能力',
        '%1$s · %2$s · %3$d tools',
        '%1$s · %2$s · %3$d 件',
        '%1$s · %2$s · %3$d outils'
    ),
    ('mcp_refresh', '刷新', '重新整理', 'Refresh', '更新', 'Actualiser'),
    ('mcp_testing', '连接中…', '連線中…', 'Connecting…', '接続中…', 'Connexion…'),
    ('mcp_edit', '编辑', '編輯', 'Edit', '編集', 'Modifier'),
    (
        'mcp_ok',
        '「%1$s」已拉到 %2$d 项能力',
        '「%1$s」已取得 %2$d 項能力',
        '%1$s loaded %2$d tools',
        '「%1$s」から %2$d 件取得しました',
        '%1$s : %2$d outils récupérés'
    ),
    (
        'mcp_fail',
        '「%1$s」连接失败：%2$s',
        '「%1$s」連線失敗：%2$s',
        '%1$s failed: %2$s',
        '「%1$s」に接続できません：%2$s',
        'Échec de %1$s : %2$s'
    ),
    (
        'mcp_no_tool',
        '服务没有返回任何能力',
        '服務沒有回傳任何能力',
        'The server returned no tools',
        'サーバーが機能を返しませんでした',
        'Le serveur n a renvoyé aucun outil'
    ),
    ('mcp_new_title', '新增 MCP 服务', '新增 MCP 服務', 'New MCP server', 'MCP サーバーを追加', 'Nouveau serveur MCP'),
    ('mcp_edit_title', '编辑 MCP 服务', '編輯 MCP 服務', 'Edit MCP server', 'MCP サーバーを編集', 'Modifier le serveur MCP'),
    ('mcp_name', '名称', '名稱', 'Name', '名前', 'Nom'),
    ('mcp_name_ph', 'Termux', 'Termux', 'Termux', 'Termux', 'Termux'),
    ('mcp_url', '地址', '位址', 'URL', 'アドレス', 'URL'),
    ('mcp_url_ph', 'http://127.0.0.1:8081/mcp', 'http://127.0.0.1:8081/mcp', 'http://127.0.0.1:8081/mcp', 'http://127.0.0.1:8081/mcp', 'http://127.0.0.1:8081/mcp'),
    ('mcp_kind', '类型', '類型', 'Type', '種類', 'Type'),
    ('mcp_kind_mcp', 'MCP', 'MCP', 'MCP', 'MCP', 'MCP'),
    ('mcp_kind_rest', 'REST 桥', 'REST 橋', 'REST bridge', 'REST ブリッジ', 'Passerelle REST'),
    (
        'mcp_kind_hint',
        'MCP 走 JSON-RPC，端点一般以 /mcp 结尾；REST 桥是 termux-mcp 的 8080，能力清单来自 GET /tools',
        'MCP 走 JSON-RPC，端點通常以 /mcp 結尾；REST 橋是 termux-mcp 的 8080，能力清單來自 GET /tools',
        'MCP uses JSON-RPC and the endpoint usually ends with /mcp; the REST bridge is termux-mcp on port 8080 and lists tools via GET /tools',
        'MCP は JSON-RPC でエンドポイントは通常 /mcp で終わります。REST ブリッジは termux-mcp の 8080 で、一覧は GET /tools から取得します',
        'MCP utilise JSON-RPC et l URL finit en général par /mcp ; la passerelle REST est termux-mcp sur le port 8080 et liste les outils via GET /tools'
    ),
    ('mcp_token', 'Token（可选）', 'Token（選填）', 'Token (optional)', 'Token（任意）', 'Token (facultatif)'),
    (
        'mcp_token_ph',
        '非本机地址才需要',
        '非本機位址才需要',
        'Only needed for remote servers',
        'ローカル以外のときのみ',
        'Nécessaire seulement pour un serveur distant'
    ),
    ('mcp_test', '测试连接', '測試連線', 'Test connection', '接続テスト', 'Tester la connexion'),
    (
        'mcp_test_ok',
        '连接成功，%1$d 项能力',
        '連線成功，%1$d 項能力',
        'Connected, %1$d tools',
        '接続成功、%1$d 件',
        'Connecté, %1$d outils'
    ),
    ('mcp_test_fail', '连接失败：%1$s', '連線失敗：%1$s', 'Failed: %1$s', '接続失敗：%1$s', 'Échec : %1$s'),
    ('mcp_need_url', '地址不能为空', '位址不能為空', 'URL is required', 'アドレスを入力してください', 'L URL est obligatoire'),
    ('mcp_group', 'MCP 能力', 'MCP 能力', 'MCP tools', 'MCP の機能', 'Outils MCP'),
    (
        'mcp_use_pre',
        '用 MCP 能力 %1$s（服务 %2$s）完成下面的任务：',
        '用 MCP 能力 %1$s（服務 %2$s）完成下面的任務：',
        'Use the MCP tool %1$s (server %2$s) to do the following:',
        'MCP の機能 %1$s（サーバー %2$s）を使って次を実行してください：',
        'Utilise l outil MCP %1$s (serveur %2$s) pour faire ce qui suit :'
    )
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
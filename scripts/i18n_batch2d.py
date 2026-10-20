#!/usr/bin/env python3
#第二批 D：设置页剩余带插值的文案（对话 / 外观 / 扩展 / 更新 / 技能 / 角色）
import io
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import i18n_res

SRC = '/storage/emulated/0/AndroidCSProjects/BaiAgent/app/src/main/kotlin/b/ai/'

ROWS = [
    ('se2_cur_msgs', '当前会话 %1$d 条 / %2$d 字', '目前對話 %1$d 條 / %2$d 字', 'This chat: %1$d messages / %2$d chars', '現在の会話 %1$d 件 / %2$d 文字', 'Cette discussion : %1$d messages / %2$d caractères'),
    ('se2_pct', '压缩阈值 %1$d%%', '壓縮閾值 %1$d%%', 'Compact at %1$d%%', '圧縮しきい値 %1$d%%', 'Seuil de compactage %1$d%%'),
    ('se2_kchar', '约 %1$dk 字', '約 %1$dk 字', 'about %1$dk chars', '約 %1$dk 文字', 'environ %1$dk caractères'),
    ('se2_n_items', '%1$d 条', '%1$d 條', '%1$d messages', '%1$d 件', '%1$d messages'),
    ('se2_rec', '%1$d 条记录 · %2$d 字 · 共 %3$d 个会话', '%1$d 條記錄 · %2$d 字 · 共 %3$d 個對話',
     '%1$d messages · %2$d chars · %3$d chats', '%1$d 件 · %2$d 文字 · 全 %3$d 会話',
     '%1$d messages · %2$d caractères · %3$d discussions'),
    ('se2_ws_files', '%1$d 个文件 · %2$s · ', '%1$d 個檔案 · %2$s · ', '%1$d files · %2$s · ',
     '%1$d ファイル · %2$s · ', '%1$d fichiers · %2$s · '),
    ('se2_ws_bound', '绑定 ', '綁定 ', 'bound to ', 'バインド ', 'lié à '),
    ('se2_ws_more', ' 等 %1$d 个会话', ' 等 %1$d 個對話', ' and %1$d chats', ' ほか %1$d 会話', ' et %1$d discussions'),
    ('se2_ws_deleted', '已删除 %1$s', '已刪除 %1$s', 'Deleted %1$s', '%1$s を削除しました', '%1$s supprimé'),
    ('se2_hint_none', '当前：请输入问题', '目前：請輸入問題', 'Current: 请输入问题 placeholder', '現在：質問を入力してください', 'Actuel : saisissez votre question'),
    ('se2_hint_n', '当前：%1$d 条里随机一条', '目前：%1$d 條裡隨機一條', 'Current: random pick of %1$d lines',
     '現在：%1$d 行からランダム', 'Actuel : une ligne au hasard parmi %1$d'),
    ('se2_back_sub', '系统级 back-to-home、跨任务动画在 targetSdk 36+ 默认开启，应用侧关不掉；这里管的是应用内跟手动画',
     '系統級 back-to-home、跨任務動畫在 targetSdk 36+ 預設開啟，應用端關不掉；這裡管的是應用內跟手動畫',
     'System back-to-home and cross-task animations are on by default from targetSdk 36+ and cannot be disabled by the app; this only controls in-app gesture following',
     'システムの back-to-home・タスク間アニメーションは targetSdk 36+ で既定有効、アプリ側では切れません。ここで扱うのはアプリ内の追従アニメーションです',
     "Les animations système back-to-home et inter-tâches sont actives par défaut à partir de targetSdk 36+ et l'app ne peut pas les couper ; ceci ne règle que le suivi du geste dans l'app"),
    ('se2_back_title', '预测性手势支持', '預測性手勢支援', 'Predictive gesture support', '予測ジェスチャー対応', 'Geste prédictif'),
    ('se2_back_desc', '开启后设置页跟着返回手势缩放淡出，滑过阈值才退回聊天、中途松手归位；关闭则直接切回',
     '開啟後設定頁跟著返回手勢縮放淡出，滑過閾值才退回聊天、中途鬆手歸位；關閉則直接切回',
     'When on, Settings follows the back gesture with a scale fade and only returns to chat past the threshold, snapping back if you let go; off switches back immediately',
     'オンにすると設定画面が戻るジェスチャーに追従して縮小フェードし、しきい値を超えたときだけチャットに戻ります。途中で離すと元に戻ります。オフでは即座に切り替わります',
     "Activé, les réglages suivent le geste de retour avec un fondu et ne reviennent au chat qu'au-delà du seuil ; désactivé, le retour est immédiat"),
    ('se2_task_sub', '到点发通知；勾了「到点自动执行」且模型空闲时会自己跑一轮',
     '到點發通知；勾了「到點自動執行」且模型閒置時會自己跑一輪',
     'Notifies when due; if "run automatically" is checked and the model is idle it also runs one round',
     '時刻になると通知。自動実行にチェックがありモデルが空いていれば1回実行します',
     'Notifie à l\'heure dite ; si « exécuter automatiquement » est coché et le modèle libre, un tour est lancé'),
    ('se2_task_none', '还没有任务', '還沒有任務', 'No tasks yet', 'タスクはまだありません', 'Aucune tâche'),
    ('se2_task_n', '已排 %1$d 条（启用 %2$d 条）', '已排 %1$d 條（啟用 %2$d 條）', '%1$d scheduled (%2$d enabled)',
     '%1$d 件予約（%2$d 件有効）', '%1$d planifiées (%2$d actives)'),
    ('se2_task_hint', '定时让模型干一件事，比如每天 08:30 汇总余票',
     '定時讓模型做一件事，例如每天 08:30 彙總餘票',
     'Let the model do something on schedule, e.g. summarize tickets daily at 08:30',
     'モデルに定期的に作業させます（例：毎日 08:30 に空席を集計）',
     'Faites faire une chose au modèle à heure fixe, par ex. un résumé des billets chaque jour à 08:30'),
    ('se2_task_off', '任务都停用了，点管理里打开', '任務都停用了，點管理裡打開',
     'All tasks are disabled; open Manage to enable them', 'タスクはすべて無効です。管理から有効にしてください',
     'Toutes les tâches sont désactivées ; ouvrez Gérer pour les activer'),
    ('se2_manage', '管理', '管理', 'Manage', '管理', 'Gérer'),
    ('se2_search_sub', '搜索是可选的技能：选了平台并填好 Key，才会把 web_search 下发给模型',
     '搜尋是選用的技能：選了平台並填好 Key，才會把 web_search 下發給模型',
     'Search is an optional skill: only when you pick a platform and fill in the key is web_search sent to the model',
     '検索は任意のスキルです。プラットフォームを選び Key を入力したときだけ web_search をモデルに渡します',
     "La recherche est une compétence optionnelle : web_search n'est transmis au modèle qu'une fois une plateforme choisie et la clé saisie"),
    ('se2_search_empty', '平台清单待补充', '平台清單待補充', 'Platform list not ready yet', 'プラットフォーム一覧は準備中です', 'Liste des plateformes à venir'),
    ('se2_search_empty2', '往 core/Search.kt 的 providers 里加一行、在 Tools.search 里加一个分支，这里就会出现平台选择和 Key 输入框。当前未选平台，模型拿不到 web_search。',
     '往 core/Search.kt 的 providers 裡加一行、在 Tools.search 裡加一個分支，這裡就會出現平台選擇和 Key 輸入框。目前未選平台，模型拿不到 web_search。',
     'Add a row to providers in core/Search.kt and a branch in Tools.search, and the platform picker plus key field appear here. No platform is selected now, so the model gets no web_search.',
     'core/Search.kt の providers に1行、Tools.search に分岐を1つ足すと、ここにプラットフォーム選択と Key 入力欄が出ます。現在は未選択のためモデルに web_search は渡りません。',
     "Ajoutez une ligne dans providers de core/Search.kt et une branche dans Tools.search : le sélecteur de plateforme et le champ de clé apparaîtront ici. Aucune plateforme choisie pour l'instant, donc le modèle n'a pas web_search."),
    ('se2_search_off', '不使用', '不使用', 'Do not use', '使用しない', 'Ne pas utiliser'),
    ('se2_search_needless', '%1$s 不需要配置，选中即可用', '%1$s 不需要設定，選中即可用', '%1$s needs no configuration, just select it',
     '%1$s は設定不要、選ぶだけで使えます', '%1$s ne demande aucune configuration, il suffit de le choisir'),
    ('se2_search_ok', '已配置，模型可以自己调 web_search', '已設定，模型可以自己呼叫 web_search',
     'Configured; the model can call web_search itself', '設定済み。モデルが自分で web_search を呼べます',
     'Configuré ; le modèle peut appeler web_search'),
    ('se2_search_miss', '还缺 %1$s：调用会直接返回未配置提示', '還缺 %1$s：呼叫會直接回傳未設定提示',
     'Missing %1$s: calls return a not-configured notice', '%1$s が未設定です。呼ぶと未設定の案内が返ります',
     'Il manque %1$s : les appels renvoient un avis « non configuré »'),
    ('se2_persona_sub', '每个会话各自记角色；这里选中的是当前会话用的，切换会话会跟着换',
     '每個對話各自記角色；這裡選中的是目前對話用的，切換對話會跟著換',
     'Each chat keeps its own persona; the one selected here applies to the current chat and follows you when you switch',
     '会話ごとにロールを記憶します。ここでの選択は現在の会話に適用され、会話を切り替えるとそれに従います',
     'Chaque discussion garde son propre rôle ; la sélection ici vaut pour la discussion courante et suit lors du changement'),
    ('se2_persona_none', '默认助手', '預設助手', 'Default assistant', '既定のアシスタント', 'Assistant par défaut'),
    ('se2_persona_none_sub', '不加载任何角色提示', '不載入任何角色提示', 'No persona prompt is loaded', 'ロールのプロンプトは読み込みません', 'Aucun prompt de rôle chargé'),
    ('se2_persona_add', '新增角色', '新增角色', 'New persona', 'ロールを追加', 'Nouveau rôle'),
    ('se2_prompt_sub', '拼在角色之后、工作流之前；留空不加，默认留空',
     '拼在角色之後、工作流之前；留空不加，預設留空',
     'Appended after the persona and before the workflow; empty by default and then not added',
     'ロールの後・ワークフローの前に連結します。空なら何も追加しません（既定は空）',
     'Ajouté après le rôle et avant le workflow ; vide par défaut et alors ignoré'),
    ('se2_prompt_label', '系统提示', '系統提示', 'System prompt', 'システムプロンプト', 'Prompt système'),
    ('se2_prompt_len', '已填写 %1$d 字，每次请求都会带上', '已填寫 %1$d 字，每次請求都會帶上',
     '%1$d chars filled in; sent with every request', '%1$d 文字入力済み。毎回のリクエストに含まれます',
     '%1$d caractères saisis ; envoyés à chaque requête'),
    ('se2_skill_enabled', '已启用 %1$d / %2$d', '已啟用 %1$d / %2$d', 'Enabled %1$d / %2$d', '有効 %1$d / %2$d', 'Activées %1$d / %2$d'),
    ('se2_upd_off', '服务端已关闭更新提示（最新 %1$s）', '伺服端已關閉更新提示（最新 %1$s）',
     'Update prompts are turned off server-side (latest %1$s)', 'サーバー側で更新通知が無効です（最新 %1$s）',
     'Les notifications de mise à jour sont désactivées côté serveur (dernière %1$s)'),
    ('se2_upd_new', '发现新版本 %1$s', '發現新版本 %1$s', 'New version %1$s available', '新しいバージョン %1$s があります', 'Nouvelle version %1$s disponible'),
    ('se2_upd_latest', '已是最新版本 %1$s', '已是最新版本 %1$s', 'Already on the latest version %1$s', '最新バージョン %1$s です', 'Déjà en version %1$s'),
    ('se2_upd_fail', '检查失败：%1$s', '檢查失敗：%1$s', 'Check failed: %1$s', '確認に失敗：%1$s', 'Échec de la vérification : %1$s'),
    ('se2_upd_hist_fail', '读取更新历史失败：%1$s', '讀取更新歷史失敗：%1$s', 'Failed to read update history: %1$s',
     '更新履歴の読み込みに失敗：%1$s', "Échec de lecture de l'historique des mises à jour : %1$s"),
    ('se2_skill_len', '%1$d 字', '%1$d 字', '%1$d chars', '%1$d 文字', '%1$d caractères'),
    ('se2_bind_fmt', ' · 绑 %1$s', ' · 綁 %1$s', ' · bound to %1$s', ' · %1$s にバインド', ' · lié à %1$s'),
]


def patch(rel, pairs, allow_many=False):
    path = SRC + rel
    src = io.open(path, encoding='utf-8').read()
    for old, new in pairs:
        n = src.count(old)
        if n == 0 or (n > 1 and not allow_many):
            print('!! %s hit %d: %s' % (rel, n, old[:70].replace('\n', '\\n')))
            continue
        src = src.replace(old, new)
    io.open(path, 'w', encoding='utf-8').write(src)
    print('patched', rel)


def main():
    i18n_res.add(ROWS)

    patch('ui/SettingsScreen.kt', [
        ('Text("当前会话 ${stats.first} 条 / ${stats.second} 字", fontSize = 11.sp, color = TextSecondary)',
         'Text(stringResource(R.string.se2_cur_msgs, stats.first, stats.second), fontSize = 11.sp, color = TextSecondary)'),
        ('Text("压缩阈值 ${Cfg.ctxPct}%", fontSize = 11.sp, color = TextPrimary)',
         'Text(stringResource(R.string.se2_pct, Cfg.ctxPct), fontSize = 11.sp, color = TextPrimary)'),
        ('Text("约 ${Cfg.ctxCharBudget / 1000}k 字", fontSize = 11.sp, color = TextSecondary)',
         'Text(stringResource(R.string.se2_kchar, Cfg.ctxCharBudget / 1000), fontSize = 11.sp, color = TextSecondary)'),
        ('PolicyChip("$v 条", Cfg.ctxKeep == v) { Cfg.ctxKeep = v }',
         'PolicyChip(stringResource(R.string.se2_n_items, v), Cfg.ctxKeep == v) { Cfg.ctxKeep = v }'),
        ('PolicyChip("$v 条", Cfg.ctxMsgCount == v) { Cfg.ctxMsgCount = v }',
         'PolicyChip(stringResource(R.string.se2_n_items, v), Cfg.ctxMsgCount == v) { Cfg.ctxMsgCount = v }'),
        ('                    "${stats.first} 条记录 · ${stats.second} 字 · 共 ${Agent.sessions.size} 个会话",',
         '                    stringResource(R.string.se2_rec, stats.first, stats.second, Agent.sessions.size),'),
        ('''                        "${w.files} 个文件 · ${Workspace.human(w.bytes)} · " +
                            if (w.unbound) stringResource(R.string.se_137) else "绑定 " +
                            w.sessions.take(2).joinToString("、") +
                            if (w.sessions.size > 2) " 等 ${w.sessions.size} 个会话" else "",''',
         '''                        stringResource(R.string.se2_ws_files, w.files, Workspace.human(w.bytes)) +
                            if (w.unbound) stringResource(R.string.se_137) else stringResource(R.string.se2_ws_bound) +
                            w.sessions.take(2).joinToString(ds) +
                            if (w.sessions.size > 2) stringResource(R.string.se2_ws_more, w.sessions.size) else "",'''),
        ('tip = err ?: "已删除 ${w.name}"', 'tip = err ?: stringResource(R.string.se2_ws_deleted, w.name)'),
        ('''            if (Cfg.hintText.isBlank()) "当前：请输入问题"
            else "当前：${Cfg.hintText.lines().count { it.isNotBlank() }} 条里随机一条",''',
         '''            if (Cfg.hintText.isBlank()) stringResource(R.string.se2_hint_none)
            else stringResource(R.string.se2_hint_n, Cfg.hintText.lines().count { it.isNotBlank() }),'''),
        ('SectionTitle(stringResource(R.string.se_037), "系统级 back-to-home、跨任务动画在 targetSdk 36+ 默认开启，应用侧关不掉；这里管的是应用内跟手动画")',
         'SectionTitle(stringResource(R.string.se_037), stringResource(R.string.se2_back_sub))'),
        ('''        CapSwitch(
            "预测性手势支持",
            "开启后设置页跟着返回手势缩放淡出，滑过阈值才退回聊天、中途松手归位；关闭则直接切回",
            Cfg.predictiveBack
        ) { Cfg.predictiveBack = it }''',
         '''        CapSwitch(
            stringResource(R.string.se2_back_title),
            stringResource(R.string.se2_back_desc),
            Cfg.predictiveBack
        ) { Cfg.predictiveBack = it }'''),
        ('SectionTitle(stringResource(R.string.se_044), "到点发通知；勾了「到点自动执行」且模型空闲时会自己跑一轮")',
         'SectionTitle(stringResource(R.string.se_044), stringResource(R.string.se2_task_sub))'),
        ('                    if (tasks.isEmpty()) "还没有任务" else "已排 ${tasks.size} 条（启用 $on 条）",',
         '                    if (tasks.isEmpty()) stringResource(R.string.se2_task_none) else stringResource(R.string.se2_task_n, tasks.size, on),'),
        ('                    if (tasks.isEmpty()) "定时让模型干一件事，比如每天 08:30 汇总余票"',
         '                    if (tasks.isEmpty()) stringResource(R.string.se2_task_hint)'),
        ('        if (tasks.isEmpty()) "任务都停用了，点管理里打开"',
         '        if (tasks.isEmpty()) stringResource(R.string.se2_task_off)'),
        ('            GhostButton("管理") { onNewTask() }',
         '            GhostButton(stringResource(R.string.se2_manage)) { onNewTask() }'),
        ('SectionTitle(stringResource(R.string.se_046), "搜索是可选的技能：选了平台并填好 Key，才会把 web_search 下发给模型")',
         'SectionTitle(stringResource(R.string.se_046), stringResource(R.string.se2_search_sub))'),
        ('            Text("平台清单待补充", fontSize = 13.sp, color = TextPrimary)',
         '            Text(stringResource(R.string.se2_search_empty), fontSize = 13.sp, color = TextPrimary)'),
        ('''                "往 core/Search.kt 的 providers 里加一行、在 Tools.search 里加一个分支，" +
                    "这里就会出现平台选择和 Key 输入框。当前未选平台，模型拿不到 web_search。",''',
         '''                stringResource(R.string.se2_search_empty2),'''),
        ('PolicyChip("不使用", sel.isBlank()) { Cfg.searchProvider = "" }',
         'PolicyChip(stringResource(R.string.se2_search_off), sel.isBlank()) { Cfg.searchProvider = "" }'),
        ('Text("${cur.label} 不需要配置，选中即可用", fontSize = 12.sp, color = OkGreen)',
         'Text(stringResource(R.string.se2_search_needless, cur.label), fontSize = 12.sp, color = OkGreen)'),
        ('''                    if (miss == null) "已配置，模型可以自己调 web_search"
                    else "还缺 ${miss.label}：调用会直接返回未配置提示",''',
         '''                    if (miss == null) stringResource(R.string.se2_search_ok)
                    else stringResource(R.string.se2_search_miss, miss.label),'''),
        ('SectionTitle(stringResource(R.string.se_047), "每个会话各自记角色；这里选中的是当前会话用的，切换会话会跟着换")',
         'SectionTitle(stringResource(R.string.se_047), stringResource(R.string.se2_persona_sub))'),
        ('ChoiceRow("默认助手", "不加载任何角色提示", Cfg.personaId.isBlank()) { Agent.setPersona("") }',
         'ChoiceRow(stringResource(R.string.se2_persona_none), stringResource(R.string.se2_persona_none_sub), Cfg.personaId.isBlank()) { Agent.setPersona("") }'),
        ('        GhostButton("新增角色", modifier = Modifier.height(38.dp)) {',
         '        GhostButton(stringResource(R.string.se2_persona_add), modifier = Modifier.height(38.dp)) {'),
        ('SectionTitle(stringResource(R.string.se_048), "拼在角色之后、工作流之前；留空不加，默认留空")',
         'SectionTitle(stringResource(R.string.se_048), stringResource(R.string.se2_prompt_sub))'),
        ('''            label = "系统提示",''', '''            label = stringResource(R.string.se2_prompt_label),'''),
        ('            else "已填写 ${Cfg.systemPrompt.length} 字，每次请求都会带上",',
         '            else stringResource(R.string.se2_prompt_len, Cfg.systemPrompt.length),'),
        ('                "已启用 ${Skills.enabled.size} / ${list.size}",',
         '                stringResource(R.string.se2_skill_enabled, Skills.enabled.size, list.size),'),
        ('                            !info.updateEnabled -> tip = "服务端已关闭更新提示（最新 ${info.latestVersion}）"',
         '                            !info.updateEnabled -> tip = stringResource(R.string.se2_upd_off, info.latestVersion)'),
        ('                                tip = "发现新版本 ${info.latestVersion}"',
         '                                tip = stringResource(R.string.se2_upd_new, info.latestVersion)'),
        ('                            else -> tip = "已是最新版本 ${info.latestVersion}"',
         '                            else -> tip = stringResource(R.string.se2_upd_latest, info.latestVersion)'),
        ('}.onFailure { tip = "检查失败：${it.message?.take(80)}" }',
         '}.onFailure { tip = stringResource(R.string.se2_upd_fail, it.message?.take(80).orEmpty()) }'),
        ('                        .onFailure { tip = "读取更新历史失败：${it.message?.take(80)}" }',
         '                        .onFailure { tip = stringResource(R.string.se2_upd_hist_fail, it.message?.take(80).orEmpty()) }'),
        ('                if (s.desc.isBlank()) "${s.body.length} 字" else s.desc,',
         '                if (s.desc.isBlank()) stringResource(R.string.se2_skill_len, s.body.length) else s.desc,'),
        ('''                Personas.brief(p) + if (p.profileId.isNotBlank()) {
                    " · 绑 " + (Profiles.list.firstOrNull { it.id == p.profileId }?.label ?: stringResource(R.string.se_267))
                } else "",''',
         '''                Personas.brief(p) + if (p.profileId.isNotBlank()) {
                    stringResource(
                        R.string.se2_bind_fmt,
                        Profiles.list.firstOrNull { it.id == p.profileId }?.label ?: stringResource(R.string.se_267)
                    )
                } else "",'''),
        ('            if (persona.id.isBlank()) "新增角色" else stringResource(R.string.se_269),',
         '            if (persona.id.isBlank()) stringResource(R.string.se2_persona_add) else stringResource(R.string.se_269),'),
    ])


main()
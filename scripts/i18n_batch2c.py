#!/usr/bin/env python3
#第二批 C：新会话相关（会话标题兜底、Agent 状态与压缩提示、导出标题）
import io
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import i18n_res

SRC = '/storage/emulated/0/AndroidCSProjects/BaiAgent/app/src/main/kotlin/b/ai/'

ROWS = [
    ('ag_new_session', '新会话', '新對話', 'New chat', '新しい会話', 'Nouvelle discussion'),
    ('ag_unnamed', '未命名会话', '未命名對話', 'Untitled chat', '無名の会話', 'Discussion sans nom'),
    ('ag_stopped', '已停止', '已停止', 'Stopped', '停止しました', 'Arrêté'),
    ('ag_err_fmt', '出错了：%1$s', '出錯了：%1$s', 'Error: %1$s', 'エラー：%1$s', 'Erreur : %1$s'),
    ('ag_no_model', '还没配置模型：请到设置里填 base_url / API key / 模型。',
     '還沒設定模型：請到設定裡填 base_url / API key / 模型。',
     'No model configured yet: set base_url / API key / model in Settings.',
     'モデル未設定：設定で base_url / API key / モデルを入力してください。',
     'Aucun modèle configuré : renseignez base_url / API key / modèle dans les réglages.'),
    ('ag_ask_model', '正在请求模型…', '正在請求模型…', 'Requesting model…', 'モデルに問い合わせ中…', 'Interrogation du modèle…'),
    ('ag_stream_fallback', '流式不可用，改走整包请求…', '串流不可用，改走整包請求…',
     'Streaming unavailable, switching to a full request…', 'ストリーミング不可、一括リクエストに切替…',
     'Streaming indisponible, passage en requête complète…'),
    ('ag_key_retry', '换个 Key 重试…', '換個 Key 重試…', 'Retrying with another key…', '別の Key で再試行…',
     'Nouvel essai avec une autre clé…'),
    ('ag_tidy', '整理回复…', '整理回覆…', 'Tidying the reply…', '返信を整えています…', 'Mise en forme de la réponse…'),
    ('ag_think_again', '继续推理…', '繼續推理…', 'Reasoning further…', '推論を続行…', 'Raisonnement en cours…'),
    ('ag_compress_run', '正在压缩上下文…', '正在壓縮上下文…', 'Compacting context…', 'コンテキストを圧縮中…',
     'Compactage du contexte…'),
    ('ag_compress_done', '已生成上下文摘要', '已產生上下文摘要', 'Context summary created',
     'コンテキスト要約を作成しました', 'Résumé du contexte créé'),
    ('ag_compress_skip', '内容太少或模型未返回，未压缩', '內容太少或模型未返回，未壓縮',
     'Nothing to compact: too little content or the model returned nothing',
     '内容が少ないかモデルが返さなかったため未圧縮', 'Rien à compacter : trop peu de contenu ou réponse vide'),
    ('ag_compress_busy', '正在跑，稍后再压', '正在執行，稍後再壓', 'Busy running, will compact later',
     '実行中、後で圧縮します', "En cours d'exécution, compactage plus tard"),
    ('ag_compress_none', '没有可压缩的内容', '沒有可壓縮的內容', 'Nothing to compact', '圧縮する内容がありません', 'Rien à compacter'),
    ('ag_compressing', '正在压缩…', '正在壓縮…', 'Compacting…', '圧縮中…', 'Compactage…'),
    ('ag_wait_fmt', '等待确认 %1$s（%2$d/%3$d）', '等待確認 %1$s（%2$d/%3$d）',
     'Waiting for confirmation: %1$s (%2$d/%3$d)', '確認待ち %1$s（%2$d/%3$d）',
     'En attente de confirmation : %1$s (%2$d/%3$d)'),
    ('ag_run_fmt', '执行 %1$s（%2$d/%3$d）…', '執行 %1$s（%2$d/%3$d）…',
     'Running %1$s (%2$d/%3$d)…', '実行中 %1$s（%2$d/%3$d）…', 'Exécution de %1$s (%2$d/%3$d)…'),
    ('ag_no_regen', '当前会话里没有这条回复，无法重新生成', '目前對話裡沒有這條回覆，無法重新產生',
     'That reply is not in this session, cannot regenerate', 'このセッションにその返信がないため再生成できません',
     "Cette réponse n'est pas dans cette session, régénération impossible"),
    ('ag_no_user_msg', '没有可用的用户消息，无法重新生成', '沒有可用的使用者訊息，無法重新產生',
     'No usable user message, cannot regenerate', '再利用できるユーザーメッセージがないため再生成できません',
     'Aucun message utilisateur exploitable, régénération impossible'),
    ('ag_ex_user', '**用户**', '**使用者**', '**User**', '**ユーザー**', '**Utilisateur**'),
    ('ag_ex_assistant', '**助手**', '**助手**', '**Assistant**', '**アシスタント**', '**Assistant**'),
    ('ag_ex_think', '思考过程', '思考過程', 'Reasoning', '思考プロセス', 'Raisonnement'),
    ('ag_ex_summary', '> 上下文摘要：', '> 上下文摘要：', '> Context summary: ', '> コンテキスト要約：', '> Résumé du contexte : '),
]


def patch(rel, pairs, allow_many=False):
    path = SRC + rel
    src = io.open(path, encoding='utf-8').read()
    for old, new in pairs:
        n = src.count(old)
        if n == 0 or (n > 1 and not allow_many):
            print('!! %s hit %d: %s' % (rel, n, old[:60].replace('\n', '\\n')))
            continue
        src = src.replace(old, new)
        print('   ok %d %s' % (n, old[:40].replace('\n', '\\n')))
    io.open(path, 'w', encoding='utf-8').write(src)
    print('patched', rel)


def main():
    i18n_res.add(ROWS)

    #Agent：加一个 Context 取文案（runLoop 里已有局部 ctx，不能用同名）
    agent = io.open(SRC + 'agent/Agent.kt', encoding='utf-8').read()
    if 'private val host: Context' not in agent:
        agent = agent.replace('''    private var job: Job? = null''',
                              '''    /**取资源文案用；不叫 ctx 是因为 runLoop 里有同名局部变量 */
    private val host: Context get() = App.self

    private var job: Job? = null''', 1)
        io.open(SRC + 'agent/Agent.kt', 'w', encoding='utf-8').write(agent)

    patch('agent/Agent.kt', [
        ('var sessionTitle by mutableStateOf("新会话")', 'var sessionTitle by mutableStateOf("")'),
        ('sessionTitle = all.firstOrNull { it.id == sid }?.title ?: "新会话"',
         'sessionTitle = all.firstOrNull { it.id == sid }?.title.orEmpty()'),
        ('Repo.renameSession(id, title.trim().ifBlank { "未命名会话" })',
         'Repo.renameSession(id, title.trim().ifBlank { host.getString(R.string.ag_unnamed) })'),
        ('                Repo.renameSession(sid, "新会话")',
         '                Repo.renameSession(sid, host.getString(R.string.ag_new_session))'),
        ('status = "已停止"', 'status = host.getString(R.string.ag_stopped)'),
        ('content = "出错了：${e.message}"', 'content = host.getString(R.string.ag_err_fmt, e.message.orEmpty())'),
        ('status = "正在请求模型…"', 'status = host.getString(R.string.ag_ask_model)'),
        ('status = "流式不可用，改走整包请求…"', 'status = host.getString(R.string.ag_stream_fallback)'),
        ('status = "换个 Key 重试…"', 'status = host.getString(R.string.ag_key_retry)'),
        ('status = "整理回复…"', 'status = host.getString(R.string.ag_tidy)'),
        ('status = "继续推理…"', 'status = host.getString(R.string.ag_think_again)'),
        ('status = "正在压缩上下文…"', 'status = host.getString(R.string.ag_compress_run)'),
        ('status = if (ok) "已生成上下文摘要" else "内容太少或模型未返回，未压缩"',
         'status = host.getString(if (ok) R.string.ag_compress_done else R.string.ag_compress_skip)'),
        ('if (busy) return "正在跑，稍后再压"', 'if (busy) return host.getString(R.string.ag_compress_busy)'),
        ('if (!ok) status = "没有可压缩的内容"', 'if (!ok) status = host.getString(R.string.ag_compress_none)'),
        ('        return "正在压缩…"', '        return host.getString(R.string.ag_compressing)'),
        ('status = "等待确认 ${Tools.label(c.name)}（${i + 1}/${calls.size}）"',
         'status = host.getString(R.string.ag_wait_fmt, Tools.label(c.name), i + 1, calls.size)'),
        ('status = "执行 ${Tools.label(c.name)}（${i + 1}/${calls.size}）…"',
         'status = host.getString(R.string.ag_run_fmt, Tools.label(c.name), i + 1, calls.size)'),
        ('"当前会话里没有这条回复，无法重新生成"', 'host.getString(R.string.ag_no_regen)'),
        ('"没有可用的用户消息，无法重新生成"', 'host.getString(R.string.ag_no_user_msg)'),
        ('sb.append("# ").append(sessionTitle).append("\\n\\n")',
         'sb.append("# ").append(sessionTitle.ifBlank { host.getString(R.string.ag_new_session) }).append("\\n\\n")'),
        ('sb.append("**用户**\\n\\n")', 'sb.append(host.getString(R.string.ag_ex_user)).append("\\n\\n")'),
        ('sb.append("**助手**\\n\\n")', 'sb.append(host.getString(R.string.ag_ex_assistant)).append("\\n\\n")'),
        ('sb.append("<details><summary>思考过程</summary>\\n\\n")',
         'sb.append("<details><summary>").append(host.getString(R.string.ag_ex_think)).append("</summary>\\n\\n")'),
        ('"summary" -> sb.append("> 上下文摘要：")',
         '"summary" -> sb.append(host.getString(R.string.ag_ex_summary))'),
    ], allow_many=True)

    #Repo：新会话默认标题留空，由界面按当前语言兜底
    patch('core/Repo.kt', [
        ('fun newSession(title: String = "新会话", category: String = "默认"): SessionRow {',
         'fun newSession(title: String = "", category: String = "默认"): SessionRow {'),
    ])

    #会话列表：标题为空时按当前语言显示「新会话」
    patch('ui/Drawer.kt', [
        ('''                Text(
                    s.title,
                    fontSize = 13.sp,
                    color = if (active || checked) AccentBlue else TextPrimary,''',
         '''                Text(
                    s.title.ifBlank { stringResource(R.string.ag_new_session) },
                    fontSize = 13.sp,
                    color = if (active || checked) AccentBlue else TextPrimary,'''),
        ('''                Text(
                    s.title,
                    fontSize = 9.sp,
                    color = TextSecondary,''',
         '''                Text(
                    s.title.ifBlank { stringResource(R.string.ag_new_session) },
                    fontSize = 9.sp,
                    color = TextSecondary,'''),
    ])

    patch('ui/SessionSheet.kt', [
        ('''                            Text(
                                s.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,''',
         '''                            Text(
                                s.title.ifBlank { stringResource(R.string.ag_new_session) },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,'''),
    ])

    patch('ui/ChatScreen.kt', [
        ('                    Agent.sessionTitle,',
         '                    Agent.sessionTitle.ifBlank { stringResource(R.string.ag_new_session) },'),
    ])

    patch('ui/SettingsScreen.kt', [
        ('Text(Agent.sessionTitle, fontSize = 13.sp, color = TextPrimary, maxLines = 1)',
         'Text(Agent.sessionTitle.ifBlank { stringResource(R.string.ag_new_session) }, fontSize = 13.sp, color = TextPrimary, maxLines = 1)'),
    ])


main()
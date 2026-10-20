#!/usr/bin/env python3
#第二批收尾：语音报错 / 工具链检测 / 预置技能名与说明 / 工作区拒删原因
import io
import os
import sys

sys.path.insert(0, '/storage/emulated/0/AndroidCSProjects/BaiAgent/scripts')
import i18n_res

K = '/storage/emulated/0/AndroidCSProjects/BaiAgent/app/src/main/kotlin/b/ai/'

ROWS = [
    #语音识别
    ('vo_no_service', '系统没有可用的语音识别服务', '系統沒有可用的語音辨識服務',
     'No speech recognition service available', '利用可能な音声認識サービスがありません',
     'Aucun service de reconnaissance vocale disponible'),
    ('vo_init_fail', '语音识别初始化失败', '語音辨識初始化失敗',
     'Failed to initialize speech recognition', '音声認識の初期化に失敗しました',
     "Échec de l'initialisation de la reconnaissance vocale"),
    ('vo_start_fail', '启动语音识别失败', '啟動語音辨識失敗',
     'Failed to start speech recognition', '音声認識を開始できませんでした',
     'Échec du démarrage de la reconnaissance vocale'),
    ('vo_audio', '录音出错', '錄音出錯', 'Recording error', '録音エラー', "Erreur d'enregistrement"),
    ('vo_client', '识别客户端错误', '辨識用戶端錯誤', 'Recognition client error',
     '認識クライアントのエラー', 'Erreur du client de reconnaissance'),
    ('vo_perm', '没有麦克风权限', '沒有麥克風權限', 'No microphone permission',
     'マイクの権限がありません', 'Autorisation micro manquante'),
    ('vo_net', '网络错误', '網路錯誤', 'Network error', 'ネットワークエラー', 'Erreur réseau'),
    ('vo_net_timeout', '网络超时', '網路逾時', 'Network timeout', 'ネットワークタイムアウト',
     'Délai réseau dépassé'),
    ('vo_no_match', '没听清，再说一次', '沒聽清，再說一次', "Didn't catch that, try again",
     '聞き取れませんでした。もう一度', 'Pas compris, réessayez'),
    ('vo_busy', '识别器忙', '辨識器忙碌', 'Recognizer busy', '認識エンジンがビジーです',
     'Reconnaissance occupée'),
    ('vo_server', '语音服务报错', '語音服務報錯', 'Speech service error', '音声サービスでエラー',
     'Erreur du service vocal'),
    ('vo_speech_timeout', '没检测到说话', '沒偵測到說話', 'No speech detected',
     '発話が検出されませんでした', 'Aucune parole détectée'),
    ('vo_fail_fmt', '识别失败(%1$d)', '辨識失敗(%1$d)', 'Recognition failed (%1$d)',
     '認識に失敗 (%1$d)', 'Échec de la reconnaissance (%1$d)'),
    #工具链检测
    ('tc_java', 'JDK（java）', 'JDK（java）', 'JDK (java)', 'JDK（java）', 'JDK (java)'),
    ('tc_javac', '编译器（javac）', '編譯器（javac）', 'Compiler (javac)', 'コンパイラ（javac）',
     'Compilateur (javac)'),
    ('tc_gradle', 'gradle', 'gradle', 'gradle', 'gradle', 'gradle'),
    ('tc_aapt2', 'aapt2（资源打包）', 'aapt2（資源打包）', 'aapt2 (resources)', 'aapt2（リソース）',
     'aapt2 (ressources)'),
    ('tc_apksigner', 'apksigner（签名）', 'apksigner（簽章）', 'apksigner (signing)', 'apksigner（署名）',
     'apksigner (signature)'),
    ('tc_zipalign', 'zipalign（对齐）', 'zipalign（對齊）', 'zipalign (alignment)', 'zipalign（整列）',
     'zipalign (alignement)'),
    ('tc_unzip', 'unzip（解包）', 'unzip（解包）', 'unzip (extract)', 'unzip（展開）', 'unzip (extraction)'),
    ('tc_curl', 'curl（下载）', 'curl（下載）', 'curl (download)', 'curl（ダウンロード）',
     'curl (téléchargement)'),
    ('tc_git', 'git', 'git', 'git', 'git', 'git'),
    ('tc_sdk', 'Android SDK（platforms）', 'Android SDK（platforms）', 'Android SDK (platforms)',
     'Android SDK（platforms）', 'Android SDK (platforms)'),
    ('tc_project_fmt', '工程目录 %1$s', '專案目錄 %1$s', 'Project dir %1$s', 'プロジェクト %1$s',
     'Dossier projet %1$s'),
    ('tc_sep', '、', '、', ', ', '、', ', '),
    ('tc_note_normal', '当前是「普通用户」模式：读不到 Termux 私有目录。切到 Root 或 Shizuku 才能检测',
     '當前是「一般使用者」模式：讀不到 Termux 私有目錄。切到 Root 或 Shizuku 才能偵測',
     "Currently in normal-user mode: Termux's private directory is not readable. Switch to Root or Shizuku to scan.",
     '現在は「一般ユーザー」モードです：Termux のプライベートディレクトリを読めません。Root か Shizuku に切り替えてください',
     "Mode utilisateur normal : le répertoire privé de Termux n'est pas lisible. Passez en Root ou Shizuku pour analyser."),
    ('tc_note_empty', '探测没拿到结果（exit=%1$d）：%2$s', '探測沒拿到結果（exit=%1$d）：%2$s',
     'Probe returned nothing (exit=%1$d): %2$s', '検出結果が得られませんでした（exit=%1$d）：%2$s',
     'Aucun résultat (exit=%1$d) : %2$s'),
    ('tc_note_ok', '工具链齐全，可以直接编译', '工具鏈齊全，可以直接編譯',
     'Toolchain complete, ready to build', 'ツールチェーンは揃っています。ビルドできます',
     "Chaîne d'outils complète, prête à compiler"),
    ('tc_note_miss', '缺 %1$s', '缺 %1$s', 'Missing %1$s', '不足 %1$s', 'Manque %1$s'),
    #预置技能（只翻译界面显示名，正文与提示仍用中文）
    ('sk_build', '构建并安装本机应用', '建置並安裝本機應用', 'Build and install this app',
     'アプリをビルドしてインストール', "Compiler et installer l'appli"),
    ('sk_build_d', '用户说编译 / 打包 / 出 APK 时按这条走', '使用者說編譯 / 打包 / 出 APK 時按這條走',
     'Follow these steps when the user asks to build, package or produce an APK',
     'ビルド / パッケージ / APK 作成と言われたらこの手順',
     "À suivre quand l'utilisateur demande de compiler, packager ou produire un APK"),
    ('sk_reverse', 'APK 逆向速查', 'APK 逆向速查', 'APK reverse-engineering cheat sheet',
     'APK リバースエンジニアリング早見', 'Aide-mémoire rétro-ingénierie APK'),
    ('sk_reverse_d', '分析别人 APK 的包名、权限、组件与硬编码', '分析別人 APK 的套件名、權限、元件與硬編碼',
     "Inspect another APK's package name, permissions, components and hardcoded strings",
     '他人の APK のパッケージ名・権限・コンポーネント・ハードコードを調べる',
     "Analyser le package, les permissions, les composants et les chaînes codées en dur d'un APK"),
    ('sk_ui', '操作助手（点屏幕）', '操作助手（點螢幕）', 'UI operator (tap the screen)',
     '操作アシスタント（画面をタップ）', "Assistant d'interface (toucher l'écran)"),
    ('sk_ui_d', '按用户描述在界面上点、滑、输入', '依使用者描述在介面上點、滑、輸入',
     'Tap, swipe and type on screen as the user describes',
     'ユーザーの説明どおりに画面をタップ・スワイプ・入力',
     'Toucher, glisser et saisir selon la description'),
    ('sk_rail', '12306 查票', '12306 查票', '12306 tickets', '12306 チケット検索', 'Billets 12306'),
    ('sk_rail_d', '余票 / 中转 / 经停，一律用 rail_query', '餘票 / 轉乘 / 停靠站，一律用 rail_query',
     'Availability, transfers, stops — always via rail_query', '残席・乗換・停車駅は常に rail_query',
     'Disponibilité, correspondances, arrêts — toujours via rail_query'),
    ('sk_media', '音视频处理（ffmpeg）', '影音處理（ffmpeg）', 'Audio & video (ffmpeg)',
     '音声・動画処理（ffmpeg）', 'Audio et vidéo (ffmpeg)'),
    ('sk_media_d', '抽音轨 / 截取片段 / 转码 / 压缩，先探工具再动手', '抽音軌 / 擷取片段 / 轉碼 / 壓縮，先探工具再動手',
     'Extract audio, trim, transcode, compress — check the tools first',
     '音声抽出・切り出し・変換・圧縮。まずツールを確認',
     "Extraire l'audio, découper, transcoder, compresser — vérifier les outils d'abord"),
    ('sk_doc', '资料整理成文档', '資料整理成文件', 'Turn material into a document', '資料をドキュメントに整理',
     'Transformer des notes en document'),
    ('sk_doc_d', '把散落的笔记 / 日志 / 网页内容收拢成一份 markdown',
     '把散落的筆記 / 日誌 / 網頁內容收攏成一份 markdown',
     'Gather scattered notes, logs and web pages into one markdown file',
     '散らばったメモ・ログ・web ページを1つの markdown にまとめる',
     'Rassembler notes, journaux et pages web dans un seul markdown'),
    ('sk_log', '日志排错', '日誌除錯', 'Log troubleshooting', 'ログでトラブルシュート', 'Débogage par les logs'),
    ('sk_log_d', '应用崩溃 / 脚本报错，按证据定位而不是靠猜', '應用程式崩潰 / 腳本報錯，依證據定位而不是靠猜',
     'Crashes and script errors — locate by evidence, not guesses',
     'アプリのクラッシュ・スクリプトのエラーは証拠で特定',
     'Plantages et erreurs de script — localiser par preuves, pas au hasard'),
    ('sk_unnamed', '未命名技能', '未命名技能', 'Unnamed skill', '無題のスキル', 'Compétence sans nom'),
    #工作区拒删原因
    ('ws_del_root', '这是数据根，不能删', '這是資料根，不能刪',
     'This is the data root and cannot be deleted', 'これはデータルートのため削除できません',
     "C'est la racine des données, suppression impossible"),
    ('ws_del_gone', '目录已经不存在', '目錄已經不存在', 'The folder no longer exists',
     'フォルダは既に存在しません', "Le dossier n'existe plus"),
    ('ws_del_inuse', '这是当前使用中的工作区，先换一个再删', '這是目前使用中的工作區，先換一個再刪',
     'This is the workspace in use — switch to another first',
     '現在使用中のワークスペースです。別のものに切り替えてから削除してください',
     "Espace de travail en cours d'utilisation — changez-en d'abord"),
    ('ws_del_bound', '还有会话绑定着它，先解绑再删', '還有會話綁定著它，先解綁再刪',
     'Sessions are still bound to it — unbind first', 'まだ会話が紐付いています。解除してから削除してください',
     'Des conversations y sont liées — déliez-les d\'abord'),
    ('ws_del_fail', '删除失败，可能没有写权限', '刪除失敗，可能沒有寫入權限',
     'Delete failed — possibly no write permission', '削除に失敗しました。書き込み権限がない可能性があります',
     "Échec de la suppression — permissions d'écriture peut-être absentes"),
]


def patch(rel, pairs):
    p = K + rel
    s = io.open(p, encoding='utf-8').read()
    for old, new in pairs:
        n = s.count(old)
        if n != 1:
            print('!! %s 命中 %d 次：%s' % (rel, n, old[:60].replace('\n', '\\n')))
            continue
        s = s.replace(old, new)
    io.open(p, 'w', encoding='utf-8').write(s)
    print('patched', rel)


def main():
    i18n_res.add(ROWS)

    #语音识别：报错文案走 ctx.getString
    patch('core/Voice.kt', [
        ('import androidx.compose.runtime.setValue',
         'import androidx.compose.runtime.setValue\nimport b.ai.R'),
        ('"系统没有可用的语音识别服务"', 'ctx.getString(R.string.vo_no_service)'),
        ('"语音识别初始化失败"', 'ctx.getString(R.string.vo_init_fail)'),
        ('error = errText(e)', 'error = errText(ctx, e)'),
        ('"启动语音识别失败"', 'ctx.getString(R.string.vo_start_fail)'),
        ('private fun errText(e: Int): String = when (e) {',
         'private fun errText(c: Context, e: Int): String = when (e) {'),
        ('"录音出错"', 'c.getString(R.string.vo_audio)'),
        ('"识别客户端错误"', 'c.getString(R.string.vo_client)'),
        ('"没有麦克风权限"', 'c.getString(R.string.vo_perm)'),
        ('"网络错误"', 'c.getString(R.string.vo_net)'),
        ('"网络超时"', 'c.getString(R.string.vo_net_timeout)'),
        ('"没听清，再说一次"', 'c.getString(R.string.vo_no_match)'),
        ('"识别器忙"', 'c.getString(R.string.vo_busy)'),
        ('"语音服务报错"', 'c.getString(R.string.vo_server)'),
        ('"没检测到说话"', 'c.getString(R.string.vo_speech_timeout)'),
        ('"识别失败($e)"', 'c.getString(R.string.vo_fail_fmt, e)'),
    ])

    #工具链检测：探测结果只在设置页显示，标签与结论全部走资源
    patch('exec/Toolchain.kt', [
        ('import b.ai.core.Cfg', 'import android.content.Context\n\nimport b.ai.R\nimport b.ai.core.Cfg'),
        ('private fun label(c: String) = when (c) {', 'private fun label(ctx: Context, c: String) = when (c) {'),
        ('"java" -> "JDK（java）"', '"java" -> ctx.getString(R.string.tc_java)'),
        ('"javac" -> "编译器（javac）"', '"javac" -> ctx.getString(R.string.tc_javac)'),
        ('"gradle" -> "gradle"', '"gradle" -> ctx.getString(R.string.tc_gradle)'),
        ('"aapt2" -> "aapt2（资源打包）"', '"aapt2" -> ctx.getString(R.string.tc_aapt2)'),
        ('"apksigner" -> "apksigner（签名）"', '"apksigner" -> ctx.getString(R.string.tc_apksigner)'),
        ('"zipalign" -> "zipalign（对齐）"', '"zipalign" -> ctx.getString(R.string.tc_zipalign)'),
        ('"unzip" -> "unzip（解包）"', '"unzip" -> ctx.getString(R.string.tc_unzip)'),
        ('"curl" -> "curl（下载）"', '"curl" -> ctx.getString(R.string.tc_curl)'),
        ('"git" -> "git"', '"git" -> ctx.getString(R.string.tc_git)'),
        ('"sdk" -> "Android SDK（platforms）"', '"sdk" -> ctx.getString(R.string.tc_sdk)'),
        ('"project" -> "工程目录 /sdcard/AndroidCSProjects/BaiAgent"',
         '"project" -> ctx.getString(R.string.tc_project_fmt, "/sdcard/AndroidCSProjects/BaiAgent")'),
        ('fun probe(timeoutMs: Long = 20_000L): Report {',
         'fun probe(ctx: Context, timeoutMs: Long = 20_000L): Report {'),
        ('"当前是「普通用户」模式：读不到 Termux 私有目录。切到 Root 或 Shizuku 才能检测"',
         'ctx.getString(R.string.tc_note_normal)'),
        ('parse(Root.run(script(), timeoutMs))', 'parse(ctx, Root.run(script(), timeoutMs))'),
        ('parse(Shiz.run(script(), timeoutMs = timeoutMs))',
         'parse(ctx, Shiz.run(script(), timeoutMs = timeoutMs))'),
        ('private fun parse(r: ShellResult): Report {',
         'private fun parse(ctx: Context, r: ShellResult): Report {'),
        ('items.add(Item(label(p[1]), p[0] == "ok"))', 'items.add(Item(label(ctx, p[1]), p[0] == "ok"))'),
        ('"探测没拿到结果（exit=${r.code}）：${r.out.take(200)}"',
         'ctx.getString(R.string.tc_note_empty, r.code, r.out.take(200))'),
        ('if (miss.isEmpty()) "工具链齐全，可以直接编译" else "缺 ${miss.joinToString("、")}"',
         'val sep = ctx.getString(R.string.tc_sep)\n'
         '                if (miss.isEmpty()) ctx.getString(R.string.tc_note_ok) else '
         'ctx.getString(R.string.tc_note_miss, miss.joinToString(sep))'),
    ])

    #工作区拒删原因：只有设置页会显示，返回值改按 ctx 取文案
    patch('exec/Workspace.kt', [
        ('import b.ai.core.Cfg', 'import android.content.Context\n\nimport b.ai.R\nimport b.ai.core.Cfg'),
        ('fun delete(path: String): String? {', 'fun delete(c: Context, path: String): String? {'),
        ('"这是数据根，不能删"', 'c.getString(R.string.ws_del_root)'),
        ('"目录已经不存在"', 'c.getString(R.string.ws_del_gone)'),
        ('"这是当前使用中的工作区，先换一个再删"', 'c.getString(R.string.ws_del_inuse)'),
        ('"还有会话绑定着它，先解绑再删"', 'c.getString(R.string.ws_del_bound)'),
        ('"删除失败，可能没有写权限"', 'c.getString(R.string.ws_del_fail)'),
    ])

    #技能：库里仍存中文原文，只有界面按当前语言显示；用户改过就原样返回
    sk_block = '''    /** 预置技能的中文原文与资源 id：库里存中文，只有界面按当前语言显示 */
    private data class SkText(val name: String, val desc: String, val nameRes: Int, val descRes: Int)
    private val TEXTS = mapOf(
        "preset-build" to SkText("构建并安装本机应用", "用户说编译 / 打包 / 出 APK 时按这条走",
            R.string.sk_build, R.string.sk_build_d),
        "preset-reverse" to SkText("APK 逆向速查", "分析别人 APK 的包名、权限、组件与硬编码",
            R.string.sk_reverse, R.string.sk_reverse_d),
        "preset-ui" to SkText("操作助手（点屏幕）", "按用户描述在界面上点、滑、输入",
            R.string.sk_ui, R.string.sk_ui_d),
        "preset-rail" to SkText("12306 查票", "余票 / 中转 / 经停，一律用 rail_query",
            R.string.sk_rail, R.string.sk_rail_d),
        "preset-media" to SkText("音视频处理（ffmpeg）", "抽音轨 / 截取片段 / 转码 / 压缩，先探工具再动手",
            R.string.sk_media, R.string.sk_media_d),
        "preset-doc" to SkText("资料整理成文档", "把散落的笔记 / 日志 / 网页内容收拢成一份 markdown",
            R.string.sk_doc, R.string.sk_doc_d),
        "preset-log" to SkText("日志排错", "应用崩溃 / 脚本报错，按证据定位而不是靠猜",
            R.string.sk_log, R.string.sk_log_d)
    )

    /** 界面显示名：预置原样才翻译，用户自己改过就原样返回 */
    fun displayName(c: Context, s: Skill): String {
        val t = TEXTS[s.id] ?: return s.name
        return if (s.name == t.name) c.getString(t.nameRes) else s.name
    } //显示名

    /** 界面显示说明：同上 */
    fun displayDesc(c: Context, s: Skill): String {
        val t = TEXTS[s.id] ?: return s.desc
        return if (s.desc == t.desc) c.getString(t.descRes) else s.desc
    } //显示说明

'''
    patch('core/Skill.kt', [
        ('import androidx.compose.runtime.mutableStateOf',
         'import androidx.compose.runtime.mutableStateOf\nimport b.ai.R'),
        ('name.ifBlank { "未命名技能" },', 'name.trim(),'),
        ('    fun prompt(): String {', sk_block + '    fun prompt(): String {'),
        ('append("${i + 1}) ${s.name}")', 'append("${i + 1}) ${s.name.ifBlank { "未命名技能" }}")'),
    ])

    #界面侧：技能名与说明按当前语言显示
    patch('ui/SettingsScreen.kt', [
        ('Workspace.delete(w.path)', 'Workspace.delete(ctx, w.path)'),
        ('Toolchain.probe()', 'Toolchain.probe(ctx)'),
        ('private fun SkillRow(s: Skill, onEdit: () -> Unit) {\n    Row(',
         'private fun SkillRow(s: Skill, onEdit: () -> Unit) {\n    val ctx = LocalContext.current\n    Row('),
        ('            Text(s.name, fontSize = 13.sp, color = TextPrimary, maxLines = 1)',
         '            val nm = Skills.displayName(ctx, s).ifBlank { ctx.getString(R.string.sk_unnamed) }\n'
         '            Text(nm, fontSize = 13.sp, color = TextPrimary, maxLines = 1)'),
        ('                if (s.desc.isBlank()) stringResource(R.string.se2_skill_len, s.body.length) else s.desc,',
         '                if (s.desc.isBlank()) stringResource(R.string.se2_skill_len, s.body.length)\n'
         '                else Skills.displayDesc(ctx, s),'),
    ])

    patch('ui/ChatSheets.kt', [
        ('            val pfx = LocalContext.current.getString(R.string.ip_skill_pre)',
         '            val ctx = LocalContext.current\n'
         '            val pfx = ctx.getString(R.string.ip_skill_pre)'),
        ('                            s.name + if (s.on) stringResource(R.string.ch_on) else "",',
         '                            Skills.displayName(ctx, s) + if (s.on) stringResource(R.string.ch_on) else "",'),
        ('                            s.desc.ifBlank { s.body.take(40) },',
         '                            Skills.displayDesc(ctx, s).ifBlank { s.body.take(40) },'),
    ])

    patch('ui/InputPanels.kt', [
        ('        val pfx = LocalContext.current.getString(R.string.ip_skill_pre)',
         '        val ctx = LocalContext.current\n'
         '        val pfx = ctx.getString(R.string.ip_skill_pre)'),
        ('                        s.name + if (s.on) "" else stringResource(R.string.ip_off),',
         '                        Skills.displayName(ctx, s) + if (s.on) "" else stringResource(R.string.ip_off),'),
        ('                    Text(s.desc.ifBlank { s.body.take(40) }, fontSize = 10.sp, color = TextSecondary, maxLines = 1)',
         '                    Text(\n'
         '                        Skills.displayDesc(ctx, s).ifBlank { s.body.take(40) },\n'
         '                        fontSize = 10.sp,\n'
         '                        color = TextSecondary,\n'
         '                        maxLines = 1\n'
         '                    )'),
    ])

    patch('ui/Mention.kt', [
        ('                            title = s.name + if (s.on) "" else stringResource(R.string.mn_off),',
         '                            title = Skills.displayName(ctx, s) + if (s.on) "" else stringResource(R.string.mn_off),'),
        ('                            sub = if (s.desc.isBlank()) stringResource(R.string.mn_chars, s.body.length) else s.desc,',
         '                            sub = if (s.desc.isBlank()) stringResource(R.string.mn_chars, s.body.length)\n'
         '                            else Skills.displayDesc(ctx, s),'),
    ])


main()

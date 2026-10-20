#!/usr/bin/env python3
#第二批 A：首页能力卡（标题/简介）+ 角色卡（名称/简介/开场白）本地化
import io
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import i18n_res

SRC = '/storage/emulated/0/AndroidCSProjects/BaiAgent/app/src/main/kotlin/b/ai/'

ROWS = [
    #首页 6 张能力卡：标题 + 简介（起手式 prompt 保持中文不翻）
    ('cd_t1', '一键构建', '一鍵建置', 'One-tap build', 'ワンタップビルド', 'Build en un clic'),
    ('cd_d1', '编译并安装 APK', '編譯並安裝 APK', 'Compile and install the APK', 'APKをビルドしてインストール', "Compiler et installer l'APK"),
    ('cd_t2', '逆向分析', '逆向分析', 'Reverse engineering', 'リバースエンジニアリング', 'Rétro-ingénierie'),
    ('cd_d2', '看包名、权限、dex、硬编码', '看套件名、權限、dex、硬編碼', 'Package name, permissions, dex, hardcoded strings', 'パッケージ名・権限・dex・ハードコード', 'Nom du paquet, permissions, dex, chaînes en dur'),
    ('cd_t3', '编译策划', '編譯策劃', 'Build planning', 'ビルド計画', 'Plan de build'),
    ('cd_d3', '一句话出完整开发计划', '一句話出完整開發計畫', 'A full dev plan from one sentence', '一言で完全な開発計画を', 'Un plan de dev complet en une phrase'),
    ('cd_t4', '行程规划', '行程規劃', 'Trip planning', '旅程プラン', 'Planification de trajet'),
    ('cd_d4', '12306 余票 + 中转', '12306 餘票 + 轉乘', '12306 tickets + transfers', '12306空席 + 乗り換え', 'Billets 12306 + correspondances'),
    ('cd_t5', '操作助手', '操作助手', 'UI assistant', '操作アシスタント', "Assistant d'interface"),
    ('cd_d5', '看屏幕并点按应用', '看螢幕並點按應用', 'Reads the screen and taps apps', '画面を見てアプリを操作', "Lit l'écran et pilote les applis"),
    ('cd_t6', '写代码', '寫程式', 'Write code', 'コードを書く', 'Écrire du code'),
    ('cd_d6', '工作区里写并跑脚本', '工作區裡寫並跑腳本', 'Write and run scripts in the workspace', 'ワークスペースでスクリプトを書いて実行', "Écrire et lancer des scripts dans l'espace de travail"),
    #内置角色卡：名称 / 简介 / 开场白
    ('per_n1', '代码搭子', '程式夥伴', 'Code buddy', 'コード相棒', 'Binôme de code'),
    ('per_d1', '话少的资深工程师，只给代码和关键那一句', '話少的資深工程師，只給程式碼和關鍵那一句', 'A terse senior engineer: code plus the one line that matters', '口数の少ないシニアエンジニア。コードと要点の一行だけ', 'Un ingénieur senior laconique : du code et la phrase clé'),
    ('per_o1', '要改哪个文件？贴代码或者给路径。', '要改哪個檔案？貼程式碼或者給路徑。', 'Which file are we changing? Paste the code or give the path.', 'どのファイルを直しますか？コードかパスをください。', 'Quel fichier doit-on modifier ? Collez le code ou donnez le chemin.'),
    ('per_n2', '严谨审阅', '嚴謹審閱', 'Strict review', '厳格レビュー', 'Relecture stricte'),
    ('per_d2', '先挑问题再给结论，每条都带依据', '先挑問題再給結論，每條都帶依據', 'Problems first, verdict after — each point backed by evidence', 'まず問題点、次に結論。各項目に根拠を添える', "D'abord les problèmes, ensuite la conclusion, chaque point justifié"),
    ('per_o2', '把要审的东西贴上来，我先按严重程度列风险，再给结论。', '把要審的東西貼上來，我先按嚴重程度列風險，再給結論。', "Paste what needs reviewing; I'll list risks by severity, then give a verdict.", 'レビュー対象を貼ってください。深刻度順にリスクを挙げ、その後に結論を出します。', "Collez ce qu'il faut relire ; je liste les risques par gravité, puis je conclus."),
    ('per_n3', '运维值班', '維運值班', 'Ops on-call', '運用当番', 'Astreinte ops'),
    ('per_d3', '先给命令再说后果，破坏性操作先亮红牌', '先給指令再說後果，破壞性操作先亮紅牌', 'Commands first, consequences stated, destructive steps flagged', 'まずコマンド、次に影響。破壊的操作は赤信号', "Commandes d'abord, conséquences ensuite, opérations destructives signalées"),
    ('per_o3', '什么现象？把报错和最近改动给我，我先给只读命令。', '什麼現象？把報錯和最近改動給我，我先給唯讀指令。', "What are the symptoms? Send the error and recent changes; I'll start with read-only commands.", 'どんな症状ですか？エラーと直近の変更をください。まず読み取り専用コマンドを出します。', 'Quels sont les symptômes ? Donnez l’erreur et les changements récents, je commence par des commandes en lecture seule.'),
    ('per_n4', '文字打磨', '文字打磨', 'Copy polish', '文章推敲', 'Polissage de texte'),
    ('per_d4', '只给成品，不加点评', '只給成品，不加點評', 'Final copy only, no commentary', '完成稿だけ、講評なし', 'Le texte final uniquement, sans commentaire'),
    ('per_n5', '口语翻译', '口語翻譯', 'Translator', '翻訳', 'Traduction'),
    ('per_d5', '只给译文，专名首次出现带原文', '只給譯文，專名首次出現帶原文', "Translation only, proper nouns kept in the original on first use", '訳文のみ。固有名詞は初出時に原文を添える', "Traduction seule, noms propres en langue d'origine à la première occurrence"),
    ('per_brief_fmt', '%1$d 字人设', '%1$d 字人設', '%1$d chars of persona', '人物設定 %1$d 文字', 'persona : %1$d caractères'),
    ('per_empty', '空角色卡', '空角色卡', 'Empty card', '空のカード', 'Carte vide'),
    ('per_unnamed', '未命名角色', '未命名角色', 'Unnamed persona', '無名のロール', 'Persona sans nom'),
]


def patch(rel, pairs):
    path = SRC + rel
    src = io.open(path, encoding='utf-8').read()
    for old, new in pairs:
        n = src.count(old)
        if n != 1:
            print('!! %s 命中 %d 次：%s' % (rel, n, old[:50]))
            continue
        src = src.replace(old, new)
    io.open(path, 'w', encoding='utf-8').write(src)
    print('patched', rel)


def main():
    i18n_res.add(ROWS)

    #Cards.kt：标题与简介改成资源 id，起手式 prompt 不动
    cards = io.open(SRC + 'core/Cards.kt', encoding='utf-8').read()
    heads = [
        ('一键构建', '编译并安装 APK'),
        ('逆向分析', '看包名、权限、dex、硬编码'),
        ('编译策划', '一句话出完整开发计划'),
        ('行程规划', '12306 余票 + 中转'),
        ('操作助手', '看屏幕并点按应用'),
        ('写代码', '工作区里写并跑脚本')
    ]
    for i, (t, d) in enumerate(heads, start=1):
        old_block = '            title = "%s",\n            desc = "%s",' % (t, d)
        if old_block not in cards:
            print('!! 卡片头没找到', t)
            continue
        cards = cards.replace(
            old_block,
            '            titleRes = R.string.cd_t%d,\n            descRes = R.string.cd_d%d,' % (i, i)
        )
    cards = cards.replace('    val title: String,\n    val desc: String,',
                          '    val titleRes: Int,\n    val descRes: Int,')
    cards = cards.replace('package b.ai.core\n', 'package b.ai.core\n\nimport b.ai.R\n', 1)
    io.open(SRC + 'core/Cards.kt', 'w', encoding='utf-8').write(cards)
    print('patched core/Cards.kt')

    #ChatScreen：卡片渲染改成取资源
    patch('ui/ChatScreen.kt', [
        ('                        title = c.title,\n                        desc = c.desc,',
         '                        title = stringResource(c.titleRes),\n                        desc = stringResource(c.descRes),'),
        ('                        role?.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.cs_default_persona),',
         '                        role?.let { Personas.displayName(it) }?.takeIf { it.isNotBlank() }\n                            ?: stringResource(R.string.cs_default_persona),'),
        ('                p.name.ifBlank { stringResource(R.string.cs_persona_unnamed) },\n                p.brief,',
         '                Personas.displayName(p).ifBlank { stringResource(R.string.cs_persona_unnamed) },\n                Personas.brief(p),'),
    ])

    #SettingsScreen：角色行名称与副标题
    patch('ui/SettingsScreen.kt', [
        ('            Text(p.name.ifBlank { stringResource(R.string.se_265) }, fontSize = 13.sp, color = TextPrimary, maxLines = 1)',
         '            Text(Personas.displayName(p).ifBlank { stringResource(R.string.se_265) }, fontSize = 13.sp, color = TextPrimary, maxLines = 1)'),
        ('                p.brief + if (p.profileId.isNotBlank()) {',
         '                Personas.brief(p) + if (p.profileId.isNotBlank()) {'),
    ])

    #Persona.kt：去掉 brief 属性，加本地化显示层
    persona = io.open(SRC + 'core/Persona.kt', encoding='utf-8').read()
    old_brief = '''    /** 列表副标题：优先一句话简介，没有就报人设字数 */
    val brief: String
        get() = when {
            desc.isNotBlank() -> desc
            prompt.isNotBlank() -> "${prompt.length} 字人设"
            else -> "空角色卡"
        }
} //角色卡'''
    assert old_brief in persona, 'brief 属性没匹配上'
    persona = persona.replace(old_brief, '} //角色卡')

    persona = persona.replace('''    private val cache = mutableStateOf<List<Persona>>(emptyList())
    private var sp: SharedPreferences? = null''',
'''    /**内置角色卡的界面文案：中文锚点 → 名称 / 简介 / 开场白，库里仍存中文原文 */
    private data class PresetText(val name: Int, val desc: Int, val opener: Int)

    private val TEXTS = mapOf(
        "代码搭子" to PresetText(R.string.per_n1, R.string.per_d1, R.string.per_o1),
        "严谨审阅" to PresetText(R.string.per_n2, R.string.per_d2, R.string.per_o2),
        "运维值班" to PresetText(R.string.per_n3, R.string.per_d3, R.string.per_o3),
        "文字打磨" to PresetText(R.string.per_n4, R.string.per_d4, 0),
        "口语翻译" to PresetText(R.string.per_n5, R.string.per_d5, 0)
    )

    private val cache = mutableStateOf<List<Persona>>(emptyList())
    private var sp: SharedPreferences? = null
    private var app: Context? = null''', 1)

    persona = persona.replace('''        val p = c.applicationContext.getSharedPreferences("bai_cfg", Context.MODE_PRIVATE)
        sp = p''',
'''        val p = c.applicationContext.getSharedPreferences("bai_cfg", Context.MODE_PRIVATE)
        sp = p
        app = c.applicationContext''', 1)

    #未命名角色兜底改资源
    persona = persona.replace('name = p.name.ifBlank { "未命名角色" }', 'name = p.name.ifBlank { res(R.string.per_unnamed) }')
    persona = persona.replace('name = p.name.ifBlank { "未命名角色" },', 'name = p.name.ifBlank { res(R.string.per_unnamed) },')

    #显示层：名称 / 简介 / 开场白 / 副标题
    persona = persona.replace('''    /** 当前角色的开场白：新会话建好后自动落一条 AI 消息，没有就返回空串 */
    fun opener(): String = current()?.firstMsg?.trim().orEmpty()''',
'''    /**取一条资源文案：没有 Context（初始化前）时退回空串 */
    private fun res(id: Int): String = if (id == 0) "" else app?.getString(id).orEmpty()

    /**内置角色的中文原文；命中说明这张卡是预置的 */
    private fun presetOf(p: Persona): Persona? = PRESETS.firstOrNull { it.name == p.name.trim() }

    /**卡片标题：内置角色按当前语言取，用户自建或改过名字的原样返回 */
    fun displayName(p: Persona): String {
        val t = TEXTS[p.name.trim()] ?: return p.name
        return res(t.name).ifBlank { p.name }
    } //本地化角色名

    /**卡片简介：内置角色的简介没被改过才用译文 */
    fun displayDesc(p: Persona): String {
        val pre = presetOf(p) ?: return p.desc
        if (p.desc.trim() != pre.desc) return p.desc
        return res(TEXTS[p.name.trim()]?.desc ?: 0).ifBlank { p.desc }
    } //本地化角色简介

    /**开场白：内置角色的开场白没被改过才用译文，新会话落库的就是这个 */
    fun displayOpener(p: Persona): String {
        val pre = presetOf(p) ?: return p.firstMsg.trim()
        val raw = p.firstMsg.trim()
        if (raw.isNotEmpty() && raw != pre.firstMsg.trim()) return raw
        return res(TEXTS[p.name.trim()]?.opener ?: 0).ifBlank { raw }
    } //本地化开场白

    /** 列表副标题：优先一句话简介，没有就报人设字数 */
    fun brief(p: Persona): String {
        val d = displayDesc(p)
        return when {
            d.isNotBlank() -> d
            p.prompt.isNotBlank() -> app?.getString(R.string.per_brief_fmt, p.prompt.length).orEmpty()
            else -> res(R.string.per_empty)
        }
    } //列表副标题

    /** 当前角色的开场白：新会话建好后自动落一条 AI 消息，没有就返回空串 */
    fun opener(): String = current()?.let { displayOpener(it) }.orEmpty()''', 1)

    persona = persona.replace('package b.ai.core\n', 'package b.ai.core\n\nimport b.ai.R\n', 1)
    io.open(SRC + 'core/Persona.kt', 'w', encoding='utf-8').write(persona)
    print('patched core/Persona.kt')


main()
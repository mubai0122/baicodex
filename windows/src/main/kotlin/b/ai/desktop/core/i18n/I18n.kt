/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27 03:40:00
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baicodex Windows - 界面语言（i18n）
 * 选项与手机端 Lang 一致；中文是源语言（key 即中文原文），其余语言查表，缺条目回退中文。
 * t() 在 Composable 里调用：lang 是 mutableStateOf，切语言后所有用到 t() 的文案随重组刷新。
 */

package b.ai.desktop.core.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

object I18n {

    /** 可选语言：BCP-47 to 自称（自称固定不翻译，任何语言下都认得出自己） */
    val options = listOf(
        "" to "跟随系统",
        "zh-CN" to "简体中文",
        "zh-TW" to "繁體中文",
        "en" to "English",
        "ja" to "日本語",
        "fr" to "Français",
    )

    /** 当前设置的原始值（空串 = 跟随系统）；由 AppState 在启动与切换时写入 */
    var lang by mutableStateOf("")
        private set

    /** 生效的语言代码：跟随系统时按系统 Locale 映射，映射不到回 zh-CN */
    val effective: String
        get() = if (lang.isBlank()) systemTag() else lang

    /** 系统语言落在支持列表里的哪一个（与手机端同一套映射） */
    fun systemTag(): String {
        val l = Locale.getDefault()
        val country = l.country.uppercase(Locale.ROOT)
        return when {
            l.language == "zh" && (country == "TW" || country == "HK" || country == "MO") -> "zh-TW"
            l.language == "zh" -> "zh-CN"
            l.language == "en" -> "en"
            l.language == "ja" -> "ja"
            l.language == "fr" -> "fr"
            else -> "zh-CN"
        }
    }

    fun init(saved: String) {
        lang = saved
    }

    fun switch(tag: String) {
        lang = tag
    }

    /** 取译文：key 就是中文原文；没有对应译文时原样返回（渐进覆盖，不阻塞新功能） */
    fun t(key: String): String = tables[effective]?.get(key) ?: key

    /** 自称不走翻译（语言列表里每一项永远显示自己的名字） */
    fun selfName(tag: String): String = options.firstOrNull { it.first == tag }?.second ?: tag

    // ------------------------------------------------------------ 译文表
    // key = 中文原文。只放设置导航层与新增页面用到的条目；深层内容随版本逐步覆盖。

    private val en: Map<String, String> = mapOf(
        // 一级分类
        "设置" to "Settings",
        "先选左边的分类" to "Pick a category on the left",
        "模型" to "Model",
        "连接、参数、思考、语音" to "Connections, sampling, thinking, speech",
        "对话" to "Chat",
        "上下文、注入、会话、优化、收藏" to "Context, injection, sessions, optimizer, stars",
        "外观" to "Appearance",
        "主题、布局、字体、Markdown" to "Theme, layout, fonts, Markdown",
        "统计" to "Stats",
        "用量与分布" to "Usage and breakdown",
        "扩展" to "Extensions",
        "工具权限、联网、数据" to "Tools, search, data",
        "关于" to "About",
        "版本、快捷键、账号" to "Version, shortcuts, account",
        // 二级条目 · 模型
        "模型配置" to "Model profiles",
        "提供商 + Key + 模型，可存多套" to "Provider + key + model, multiple profiles",
        "生成参数" to "Sampling",
        "温度、top_p、输出上限、超时" to "Temperature, top_p, output cap, timeout",
        "思考默认" to "Thinking defaults",
        "新会话是否先推理、默认档位" to "Reasoning on by default, default effort",
        "语音朗读" to "Speech",
        "自动朗读与系统语速" to "Auto read-out and system voice rate",
        "长期记忆" to "Long-term memory",
        "跨会话保留的偏好与事实" to "Preferences and facts kept across sessions",
        "余额" to "Balance",
        "查询来源与低余额提醒" to "Balance source and low-balance alert",
        // 二级条目 · 对话
        "上下文" to "Context",
        "预算环与历史条数" to "Budget ring and history limit",
        "注入与输出" to "Injection & output",
        "运行环境、文风、回复信息、草稿" to "Environment, style, meta, drafts",
        "会话" to "Sessions",
        "自动命名、删除确认、条数" to "Auto title, delete confirm, limits",
        "提示词优化" to "Prompt optimizer",
        "用哪个连接改写提示词" to "Which connection rewrites prompts",
        "收藏夹" to "Starred",
        "跨会话的收藏消息" to "Starred messages across sessions",
        // 二级条目 · 外观
        "主题与布局" to "Theme & layout",
        "深浅色、气泡与侧边栏" to "Light/dark, bubbles, sidebar",
        "个性化" to "Personalize",
        "主题色与界面基调" to "Accent color and look",
        "语言" to "Language",
        "界面显示语言" to "UI display language",
        "字体与输入" to "Fonts & input",
        "正文字号、行距、输入提示" to "Body size, line height, input hint",
        "Markdown" to "Markdown",
        "代码字号与整段预览" to "Code size and live preview",
        // 二级条目 · 其它
        "用量统计" to "Usage stats",
        "会话、token、按模型、按天" to "Sessions, tokens, per model, per day",
        "工具权限" to "Tool access",
        "智能体" to "Agent",
        "角色与系统提示" to "Role & system prompt",
        "编译工具链" to "Toolchains",
        "10 个工具开关与命令超时" to "10 tool switches and command timeout",
        "沙箱与确认" to "Sandbox & approval",
        "文件沙箱、工具确认、危险命令拦截" to "File sandbox, tool approval, command guard",
        "联网搜索" to "Web search",
        "新会话默认开原生搜索" to "Native search on by default",
        "数据与备份" to "Data & backup",
        "导出、打开目录、清空" to "Export, open folders, wipe",
        "使用上报" to "Usage report",
        "设备码、接口与开关" to "Device code, endpoint, toggle",
        "版本与更新" to "Version & update",
        "版本、路径、重置" to "Version, install path, reset",
        "快捷键与操作" to "Shortcuts",
        "按键、右键、页面入口" to "Keys, right-click, page entries",
        "GitHub 账号" to "GitHub account",
        "登录与凭据" to "Sign-in and credentials",
        // 设置壳
        "从左边选分类，再选条目" to "Pick a category, then an entry",
        "还没有选择分类" to "No category selected",
        "设置" to "Settings",
        // 语言页
        "界面语言" to "Interface language",
        "切完立即生效，不用重启" to "Applies immediately, no restart needed",
        "跟随系统" to "Follow system",
        "翻译目前覆盖设置导航与新增页面，深层内容会随版本逐步补齐。" to
            "Translations currently cover settings navigation and newer pages; deeper content lands release by release.",
    )

    private val zhTw: Map<String, String> = mapOf(
        "设置" to "設定",
        "先选左边的分类" to "先點左邊的分類",
        "模型" to "模型",
        "连接、参数、思考、语音" to "連線、參數、思考、語音",
        "对话" to "對話",
        "上下文、注入、会话、优化、收藏" to "上下文、注入、會話、最佳化、收藏",
        "外观" to "外觀",
        "主题、布局、字体、Markdown" to "主題、版面、字體、Markdown",
        "统计" to "統計",
        "用量与分布" to "用量與分布",
        "扩展" to "擴充",
        "工具权限、联网、数据" to "工具權限、連網、資料",
        "关于" to "關於",
        "版本、快捷键、账号" to "版本、快速鍵、帳號",
        "模型配置" to "模型設定",
        "提供商 + Key + 模型，可存多套" to "供應商 + Key + 模型，可存多組",
        "生成参数" to "生成參數",
        "温度、top_p、输出上限、超时" to "溫度、top_p、輸出上限、逾時",
        "思考默认" to "思考預設",
        "新会话是否先推理、默认档位" to "新會話是否先推理、預設檔位",
        "语音朗读" to "語音朗讀",
        "自动朗读与系统语速" to "自動朗讀與系統語速",
        "长期记忆" to "長期記憶",
        "跨会话保留的偏好与事实" to "跨會話保留的偏好與事實",
        "余额" to "餘額",
        "查询来源与低余额提醒" to "查詢來源與低餘額提醒",
        "上下文" to "上下文",
        "预算环与历史条数" to "預算環與歷史條數",
        "注入与输出" to "注入與輸出",
        "运行环境、文风、回复信息、草稿" to "執行環境、文風、回覆資訊、草稿",
        "会话" to "會話",
        "自动命名、删除确认、条数" to "自動命名、刪除確認、條數",
        "提示词优化" to "提示詞最佳化",
        "用哪个连接改写提示词" to "用哪個連線改寫提示詞",
        "收藏夹" to "收藏夾",
        "跨会话的收藏消息" to "跨會話的收藏訊息",
        "主题与布局" to "主題與版面",
        "深浅色、气泡与侧边栏" to "深淺色、氣泡與側邊欄",
        "个性化" to "個人化",
        "主题色与界面基调" to "主題色與介面基調",
        "语言" to "語言",
        "界面显示语言" to "介面顯示語言",
        "字体与输入" to "字體與輸入",
        "正文字号、行距、输入提示" to "正文字號、行距、輸入提示",
        "代码字号与整段预览" to "程式碼字號與整段預覽",
        "用量统计" to "用量統計",
        "会话、token、按模型、按天" to "會話、token、按模型、按天",
        "工具权限" to "工具權限",
        "10 个工具开关与命令超时" to "10 個工具開關與命令逾時",
        "沙箱与确认" to "沙箱與確認",
        "文件沙箱、工具确认、危险命令拦截" to "檔案沙箱、工具確認、危險命令攔截",
        "联网搜索" to "連網搜尋",
        "新会话默认开原生搜索" to "新會話預設開原生搜尋",
        "数据与备份" to "資料與備份",
        "导出、打开目录、清空" to "匯出、開啟目錄、清空",
        "使用上报" to "使用回報",
        "设备码、接口与开关" to "設備碼、介面與開關",
        "版本与更新" to "版本與更新",
        "版本、路径、重置" to "版本、路徑、重設",
        "快捷键与操作" to "快速鍵與操作",
        "按键、右键、页面入口" to "按鍵、右鍵、頁面入口",
        "GitHub 账号" to "GitHub 帳號",
        "登录与凭据" to "登入與憑證",
        "从左边选分类，再选条目" to "從左邊選分類，再選條目",
        "还没有选择分类" to "還沒有選擇分類",
        "界面语言" to "介面語言",
        "切完立即生效，不用重启" to "切完立即生效，不用重啟",
        "跟随系统" to "跟隨系統",
        "翻译目前覆盖设置导航与新增页面，深层内容会随版本逐步补齐。" to
            "翻譯目前覆蓋設定導覽與新增頁面，深層內容會隨版本逐步補齊。",
    )

    private val ja: Map<String, String> = mapOf(
        "设置" to "設定",
        "先选左边的分类" to "左のカテゴリを選んでください",
        "模型" to "モデル",
        "连接、参数、思考、语音" to "接続、パラメータ、思考、音声",
        "对话" to "チャット",
        "上下文、注入、会话、优化、收藏" to "コンテキスト、注入、セッション、最適化、スター",
        "外观" to "外観",
        "主题、布局、字体、Markdown" to "テーマ、レイアウト、フォント、Markdown",
        "统计" to "統計",
        "用量与分布" to "使用量と内訳",
        "扩展" to "拡張",
        "工具权限、联网、数据" to "ツール権限、検索、データ",
        "关于" to "情報",
        "版本、快捷键、账号" to "バージョン、ショートカット、アカウント",
        "模型配置" to "モデル設定",
        "提供商 + Key + 模型，可存多套" to "プロバイダ + Key + モデル、複数保存可",
        "生成参数" to "生成パラメータ",
        "温度、top_p、输出上限、超时" to "温度、top_p、出力上限、タイムアウト",
        "思考默认" to "思考の既定",
        "新会话是否先推理、默认档位" to "新セッションの推論と既定レベル",
        "语音朗读" to "音声読み上げ",
        "自动朗读与系统语速" to "自動読み上げと音声速度",
        "长期记忆" to "長期メモリ",
        "跨会话保留的偏好与事实" to "セッションを跨ぐ設定と事実",
        "余额" to "残高",
        "查询来源与低余额提醒" to "残高ソースと低残高通知",
        "上下文" to "コンテキスト",
        "预算环与历史条数" to "予算リングと履歴上限",
        "注入与输出" to "注入と出力",
        "运行环境、文风、回复信息、草稿" to "環境、文体、メタ表示、下書き",
        "会话" to "セッション",
        "自动命名、删除确认、条数" to "自動タイトル、削除確認、上限",
        "提示词优化" to "プロンプト最適化",
        "用哪个连接改写提示词" to "書き直すのに使う接続",
        "收藏夹" to "スター",
        "跨会话的收藏消息" to "セッションを跨ぐスター済みメッセージ",
        "主题与布局" to "テーマとレイアウト",
        "深浅色、气泡与侧边栏" to "ライト/ダーク、バブル、サイドバー",
        "个性化" to "個人設定",
        "主题色与界面基调" to "アクセントカラーと外観",
        "语言" to "言語",
        "界面显示语言" to "表示言語",
        "字体与输入" to "フォントと入力",
        "正文字号、行距、输入提示" to "本文サイズ、行間、入力ヒント",
        "代码字号与整段预览" to "コードサイズとプレビュー",
        "用量统计" to "使用統計",
        "会话、token、按模型、按天" to "セッション、token、モデル別、日別",
        "工具权限" to "ツール権限",
        "智能体" to "エージェント",
        "角色与系统提示" to "役割とシステムプロンプト",
        "编译工具链" to "ツールチェーン",
        "10 个工具开关与命令超时" to "10 個のツール切替とコマンドタイムアウト",
        "沙箱与确认" to "サンドボックスと承認",
        "文件沙箱、工具确认、危险命令拦截" to "ファイル沙箱、ツール承認、危険コマンド遮断",
        "联网搜索" to "Web 検索",
        "新会话默认开原生搜索" to "新セッションで既定オン",
        "数据与备份" to "データとバックアップ",
        "导出、打开目录、清空" to "書き出し、フォルダを開く、消去",
        "使用上报" to "使用レポート",
        "设备码、接口与开关" to "デバイスコード、エンドポイント、切替",
        "版本与更新" to "バージョンと更新",
        "版本、路径、重置" to "バージョン、パス、リセット",
        "快捷键与操作" to "ショートカット",
        "按键、右键、页面入口" to "キー、右クリック、画面入口",
        "GitHub 账号" to "GitHub アカウント",
        "登录与凭据" to "ログインと認証情報",
        "从左边选分类，再选条目" to "カテゴリを選び、項目を選択",
        "还没有选择分类" to "カテゴリ未選択",
        "界面语言" to "表示言語",
        "切完立即生效，不用重启" to "即時反映、再起動不要",
        "跟随系统" to "システムに従う",
        "翻译目前覆盖设置导航与新增页面，深层内容会随版本逐步补齐。" to
            "翻訳は現在設定のナビゲーションと新規ページをカバーし、詳細は順次対応します。",
    )

    private val fr: Map<String, String> = mapOf(
        "设置" to "Paramètres",
        "先选左边的分类" to "Choisissez une catégorie à gauche",
        "模型" to "Modèle",
        "连接、参数、思考、语音" to "Connexions, échantillonnage, réflexion, voix",
        "对话" to "Discussion",
        "上下文、注入、会话、优化、收藏" to "Contexte, injection, sessions, optimiseur, favoris",
        "外观" to "Apparence",
        "主题、布局、字体、Markdown" to "Thème, disposition, polices, Markdown",
        "统计" to "Statistiques",
        "用量与分布" to "Utilisation et répartition",
        "扩展" to "Extensions",
        "工具权限、联网、数据" to "Outils, recherche, données",
        "关于" to "À propos",
        "版本、快捷键、账号" to "Version, raccourcis, compte",
        "模型配置" to "Profils de modèle",
        "提供商 + Key + 模型，可存多套" to "Fournisseur + clé + modèle, plusieurs profils",
        "生成参数" to "Échantillonnage",
        "温度、top_p、输出上限、超时" to "Température, top_p, limite, délai",
        "思考默认" to "Réflexion par défaut",
        "新会话是否先推理、默认档位" to "Raisonnement par défaut et niveau",
        "语音朗读" to "Lecture vocale",
        "自动朗读与系统语速" to "Lecture auto et débit système",
        "长期记忆" to "Mémoire long terme",
        "跨会话保留的偏好与事实" to "Préférences gardées entre sessions",
        "余额" to "Solde",
        "查询来源与低余额提醒" to "Source du solde et alerte",
        "上下文" to "Contexte",
        "预算环与历史条数" to "Anneau de budget et historique",
        "注入与输出" to "Injection et sortie",
        "运行环境、文风、回复信息、草稿" to "Environnement, style, méta, brouillons",
        "会话" to "Sessions",
        "自动命名、删除确认、条数" to "Titre auto, confirmation, limites",
        "提示词优化" to "Optimiseur de prompt",
        "用哪个连接改写提示词" to "Connexion qui réécrit les prompts",
        "收藏夹" to "Favoris",
        "跨会话的收藏消息" to "Messages favoris de toutes les sessions",
        "主题与布局" to "Thème et disposition",
        "深浅色、气泡与侧边栏" to "Clair/sombre, bulles, barre latérale",
        "个性化" to "Personnalisation",
        "主题色与界面基调" to "Couleur d'accent et style",
        "语言" to "Langue",
        "界面显示语言" to "Langue de l'interface",
        "字体与输入" to "Polices et saisie",
        "正文字号、行距、输入提示" to "Taille du texte, interligne, indication",
        "代码字号与整段预览" to "Taille du code et aperçu",
        "用量统计" to "Statistiques d'usage",
        "会话、token、按模型、按天" to "Sessions, tokens, par modèle, par jour",
        "工具权限" to "Accès aux outils",
        "智能体" to "Agent",
        "角色与系统提示" to "Rôle et prompt système",
        "编译工具链" to "Chaînes d'outils",
        "10 个工具开关与命令超时" to "10 interrupteurs et délai de commande",
        "沙箱与确认" to "Bac à sable et approbation",
        "文件沙箱、工具确认、危险命令拦截" to "Sandbox fichiers, approbation, garde-fous",
        "联网搜索" to "Recherche web",
        "新会话默认开原生搜索" to "Recherche native par défaut",
        "数据与备份" to "Données et sauvegarde",
        "导出、打开目录、清空" to "Exporter, dossiers, effacer",
        "使用上报" to "Rapport d'usage",
        "设备码、接口与开关" to "Code appareil, endpoint, interrupteur",
        "版本与更新" to "Version et mise à jour",
        "版本、路径、重置" to "Version, chemin, réinitialiser",
        "快捷键与操作" to "Raccourcis",
        "按键、右键、页面入口" to "Touches, clic droit, entrées",
        "GitHub 账号" to "Compte GitHub",
        "登录与凭据" to "Connexion et identifiants",
        "从左边选分类，再选条目" to "Choisissez une catégorie puis un point",
        "还没有选择分类" to "Aucune catégorie choisie",
        "界面语言" to "Langue de l'interface",
        "切完立即生效，不用重启" to "Effet immédiat, sans redémarrage",
        "跟随系统" to "Suivre le système",
        "翻译目前覆盖设置导航与新增页面，深层内容会随版本逐步补齐。" to
            "La traduction couvre la navigation des paramètres et les nouvelles pages ; le reste suivra.",
    )

    private val tables: Map<String, Map<String, String>> = mapOf(
        "en" to en,
        "zh-TW" to zhTw,
        "ja" to ja,
        "fr" to fr,
    )
}

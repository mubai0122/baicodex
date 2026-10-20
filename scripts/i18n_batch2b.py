#!/usr/bin/env python3
#第二批 B：工作方式（普通用户 / Shizuku / root）、权限路径、权限名、受限目录通道、Shizuku 探测文案本地化
import io
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import i18n_res

SRC = '/storage/emulated/0/AndroidCSProjects/BaiAgent/app/src/main/kotlin/b/ai/'

ROWS = [
    #工作方式三档
    ('wm_normal', '普通用户', '一般使用者', 'Normal user', '一般ユーザー', 'Utilisateur standard'),
    ('wm_normal_d', 'App 沙箱权限，可选零宽路径拓展', 'App 沙箱權限，可選零寬路徑擴充',
     'App sandbox permissions, with optional zero-width path extension',
     'アプリのサンドボックス権限。ゼロ幅パス拡張も選択可',
     "Permissions du bac à sable de l'app, extension zéro-largeur en option"),
    ('wm_shizuku', 'Shizuku', 'Shizuku', 'Shizuku', 'Shizuku', 'Shizuku'),
    ('wm_shizuku_d', 'Binder 桥接，得到 shell(UID 2000) 能力', 'Binder 橋接，得到 shell(UID 2000) 能力',
     'Binder bridge, grants shell (UID 2000) power', 'Binder 経由で shell(UID 2000) 権限',
     'Pont Binder, donne les droits shell (UID 2000)'),
    ('wm_root', 'Root', 'Root', 'Root', 'Root', 'Root'),
    ('wm_root_d', 'su -c 直接执行，无限制', 'su -c 直接執行，無限制',
     'Runs directly via su -c, no restrictions', 'su -c で直接実行、制限なし',
     'Exécution directe via su -c, sans restriction'),
    ('wm_cur_fmt', '当前：%1$s', '目前：%1$s', 'Current: %1$s', '現在：%1$s', 'Actuel : %1$s'),
    #权限路径标注
    ('rt_sandbox', '沙箱', '沙箱', 'Sandbox', 'サンドボックス', 'Bac à sable'),
    ('rt_all', '全文件', '全檔案', 'All files', '全ファイル', 'Tous les fichiers'),
    ('rt_zero', '零宽', '零寬', 'Zero-width', 'ゼロ幅', 'Zéro-largeur'),
    ('rt_shizuku', 'Shizuku', 'Shizuku', 'Shizuku', 'Shizuku', 'Shizuku'),
    ('rt_root', 'root', 'root', 'root', 'root', 'root'),
    #运行期权限名
    ('pr_mic', '麦克风', '麥克風', 'Microphone', 'マイク', 'Micro'),
    ('pr_notify', '通知', '通知', 'Notifications', '通知', 'Notifications'),
    ('pr_images', '图片', '圖片', 'Photos', '画像', 'Photos'),
    ('pr_videos', '视频', '影片', 'Videos', '動画', 'Vidéos'),
    ('pr_audio', '音频', '音訊', 'Audio', '音声', 'Audio'),
    ('pr_audio_sel', '部分图片', '部分圖片', 'Selected photos', '選択した画像', 'Photos sélectionnées'),
    ('pr_read_store', '读取存储', '讀取儲存空間', 'Read storage', 'ストレージ読み取り', 'Lecture du stockage'),
    ('pr_write_store', '写入存储', '寫入儲存空間', 'Write storage', 'ストレージ書き込み', 'Écriture du stockage'),
    #零宽候选路径名
    ('rz_orig', '原路径', '原路徑', 'Original path', '元のパス', "Chemin d'origine"),
    ('rz_dot', '尾随点', '尾隨點', 'Trailing dot', '末尾ドット', 'Point final'),
    ('rz_slash', '双斜杠', '雙斜線', 'Double slash', '二重スラッシュ', 'Double barre'),
    ('rz_in', '段内零宽', '段內零寬', 'In-segment zero-width', 'セグメント内ゼロ幅', 'Zéro-largeur interne'),
    ('rz_tail', '尾随零宽', '尾隨零寬', 'Trailing zero-width', '末尾ゼロ幅', 'Zéro-largeur finale'),
    ('rz_post', '段后零宽', '段後零寬', 'Post-segment zero-width', 'セグメント後ゼロ幅', 'Zéro-largeur après segment'),
    ('rz_unknown', '未知', '未知', 'Unknown', '不明', 'Inconnu'),
    ('rz_unavail', '不可用', '不可用', 'Unavailable', '利用不可', 'Indisponible'),
    #Android/data 与 obb 的实际通道
    ('dz_checking', '检测中…', '檢測中…', 'Checking…', '検出中…', 'Vérification…'),
    ('dz_all', '可直接读写（所有文件访问）', '可直接讀寫（所有檔案存取）',
     'Read-write (all-files access)', '読み書き可（全ファイルアクセス）', 'Lecture-écriture (accès tous fichiers)'),
    ('dz_native', '可直接读写（原路径）', '可直接讀寫（原路徑）',
     'Read-write (native path)', '読み書き可（元のパス）', 'Lecture-écriture (chemin natif)'),
    ('dz_zero', '零宽路径（%1$s）', '零寬路徑（%1$s）', 'Zero-width path (%1$s)', 'ゼロ幅パス（%1$s）', 'Chemin zéro-largeur (%1$s)'),
    ('dz_unsupported', '零宽已开启但本机不支持', '零寬已開啟但本機不支援',
     'Zero-width enabled but unsupported on this device', 'ゼロ幅は有効だが本機は非対応',
     'Zéro-largeur activé mais non pris en charge'),
    ('dz_locked', '不可访问（需要 Shizuku / root 或零宽）', '不可存取（需要 Shizuku / root 或零寬）',
     'Not accessible (needs Shizuku / root or zero-width)', 'アクセス不可（Shizuku / root かゼロ幅が必要）',
     'Inaccessible (Shizuku / root ou zéro-largeur requis)'),
    ('se_zone_cur', '当前检测：%1$s', '目前檢測：%1$s', 'Current: %1$s', '現在の検出：%1$s', 'Détection actuelle : %1$s'),
    #Shizuku 探测结果
    ('shiz_not_run', '已安装 Shizuku，但服务未运行：先打开 Shizuku App 启动服务',
     '已安裝 Shizuku，但服務未執行：先開啟 Shizuku App 啟動服務',
     'Shizuku is installed but its service is not running: open the Shizuku app and start the service',
     'Shizuku はインストール済みですがサービスが動いていません。Shizuku アプリでサービスを開始してください',
     "Shizuku est installé mais son service ne tourne pas : ouvrez l'app Shizuku et démarrez le service"),
    ('shiz_not_installed', '未安装 Shizuku：需先安装 Shizuku，再用 adb 或 root 启动服务',
     '未安裝 Shizuku：需先安裝 Shizuku，再用 adb 或 root 啟動服務',
     'Shizuku is not installed: install it first, then start the service with adb or root',
     'Shizuku が未インストールです。先にインストールし、adb か root でサービスを開始してください',
     "Shizuku n'est pas installé : installez-le, puis démarrez le service via adb ou root"),
    ('shiz_old', 'Shizuku 服务版本过旧（v%1$d < 11），请升级 Shizuku',
     'Shizuku 服務版本過舊（v%1$d < 11），請升級 Shizuku',
     'Shizuku service is too old (v%1$d < 11), please upgrade Shizuku',
     'Shizuku サービスのバージョンが古すぎます（v%1$d < 11）。アップグレードしてください',
     'Le service Shizuku est trop ancien (v%1$d < 11), veuillez le mettre à jour'),
    ('shiz_back_root', 'root 后端', 'root 後端', 'root backend', 'root バックエンド', 'backend root'),
    ('shiz_back_adb', 'adb 后端', 'adb 後端', 'adb backend', 'adb バックエンド', 'backend adb'),
    ('shiz_ready', '已授权（服务 v%1$d，UID %2$d · %3$s）', '已授權（服務 v%1$d，UID %2$d · %3$s）',
     'Authorized (service v%1$d, UID %2$d · %3$s)', '認可済み（サービス v%1$d、UID %2$d · %3$s）',
     'Autorisé (service v%1$d, UID %2$d · %3$s)'),
    ('shiz_no_perm', 'Shizuku 已运行但本应用未授权：点「申请 Shizuku 授权」',
     'Shizuku 已執行但本應用未授權：點「申請 Shizuku 授權」',
     'Shizuku is running but this app is not authorized: tap "Request Shizuku permission"',
     'Shizuku は動作中ですが本アプリが未認可です。「Shizuku 権限を要求」を押してください',
     "Shizuku tourne mais cette app n'est pas autorisée : appuyez sur « Demander l'autorisation Shizuku »"),
]


def patch(rel, pairs):
    path = SRC + rel
    src = io.open(path, encoding='utf-8').read()
    for old, new in pairs:
        n = src.count(old)
        if n != 1:
            print('!! %s 命中 %d 次：%s' % (rel, n, old[:60].replace('\n', '\\n')))
            continue
        src = src.replace(old, new)
    io.open(path, 'w', encoding='utf-8').write(src)
    print('patched', rel)


def main():
    i18n_res.add(ROWS)

    #Model.kt：工作方式与权限路径加资源 id（label/desc 仍留中文给提示词用）
    patch('core/Model.kt', [
        ('''enum class WorkMode(val id: String, val label: String, val desc: String) {
    NORMAL("normal", "普通用户", "App 沙箱权限，可选零宽路径拓展"),
    SHIZUKU("shizuku", "Shizuku", "Binder 桥接，得到 shell(UID 2000) 能力"),
    ROOT("root", "Root", "su -c 直接执行，无限制");''',
         '''enum class WorkMode(
    val id: String,
    val label: String,
    val desc: String,
    val labelRes: Int,
    val descRes: Int
) {
    NORMAL("normal", "普通用户", "App 沙箱权限，可选零宽路径拓展", R.string.wm_normal, R.string.wm_normal_d),
    SHIZUKU("shizuku", "Shizuku", "Binder 桥接，得到 shell(UID 2000) 能力", R.string.wm_shizuku, R.string.wm_shizuku_d),
    ROOT("root", "Root", "su -c 直接执行，无限制", R.string.wm_root, R.string.wm_root_d);'''),
        ('''enum class Route(val label: String) {
    SANDBOX("沙箱"),
    ALL_FILES("全文件"),
    ZERO_WIDTH("零宽"),
    SHIZUKU("Shizuku"),
    ROOT("root")
}''',
         '''enum class Route(val label: String, val labelRes: Int) {
    SANDBOX("沙箱", R.string.rt_sandbox),
    ALL_FILES("全文件", R.string.rt_all),
    ZERO_WIDTH("零宽", R.string.rt_zero),
    SHIZUKU("Shizuku", R.string.rt_shizuku),
    ROOT("root", R.string.rt_root)
}'''),
        ('package b.ai.core\n', 'package b.ai.core\n\nimport b.ai.R\n'),
    ])
    #Perms.kt：权限名走资源，未知权限退回类名
    perms = io.open(SRC + 'core/Perms.kt', encoding='utf-8').read()
    old_label = '''    fun label(p: String): String = when (p) {
        Manifest.permission.RECORD_AUDIO -> "麦克风"
        Manifest.permission.POST_NOTIFICATIONS -> "通知"
        Manifest.permission.READ_MEDIA_IMAGES -> "图片"
        Manifest.permission.READ_MEDIA_VIDEO -> "视频"
        Manifest.permission.READ_MEDIA_AUDIO -> "音频"
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED -> "部分图片"
        Manifest.permission.READ_EXTERNAL_STORAGE -> "读取存储"
        Manifest.permission.WRITE_EXTERNAL_STORAGE -> "写入存储"
        else -> p.substringAfterLast('.')
    }'''
    assert old_label in perms, 'Perms.label 没匹配上'
    perms = perms.replace(old_label, '''    fun label(p: String): String = when (p) {
        Manifest.permission.RECORD_AUDIO -> "麦克风"
        Manifest.permission.POST_NOTIFICATIONS -> "通知"
        Manifest.permission.READ_MEDIA_IMAGES -> "图片"
        Manifest.permission.READ_MEDIA_VIDEO -> "视频"
        Manifest.permission.READ_MEDIA_AUDIO -> "音频"
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED -> "部分图片"
        Manifest.permission.READ_EXTERNAL_STORAGE -> "读取存储"
        Manifest.permission.WRITE_EXTERNAL_STORAGE -> "写入存储"
        else -> p.substringAfterLast('.')
    } //中文名（拼进系统提示用）

    /**权限名的资源 id：未知权限返回 0 */
    fun labelRes(p: String): Int = when (p) {
        Manifest.permission.RECORD_AUDIO -> R.string.pr_mic
        Manifest.permission.POST_NOTIFICATIONS -> R.string.pr_notify
        Manifest.permission.READ_MEDIA_IMAGES -> R.string.pr_images
        Manifest.permission.READ_MEDIA_VIDEO -> R.string.pr_videos
        Manifest.permission.READ_MEDIA_AUDIO -> R.string.pr_audio
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED -> R.string.pr_audio_sel
        Manifest.permission.READ_EXTERNAL_STORAGE -> R.string.pr_read_store
        Manifest.permission.WRITE_EXTERNAL_STORAGE -> R.string.pr_write_store
        else -> 0
    } //界面用资源

    /**界面用：按当前语言取权限名，未知权限退回类名 */
    fun label(c: Context, p: String): String {
        val r = labelRes(p)
        return if (r == 0) p.substringAfterLast('.') else c.getString(r)
    } //本地化权限名''')
    perms = perms.replace('package b.ai.core\n', 'package b.ai.core\n\nimport b.ai.R\n', 1)
    io.open(SRC + 'core/Perms.kt', 'w', encoding='utf-8').write(perms)
    print('patched core/Perms.kt')

    #Router.kt：候选路径名与受限目录通道本地化
    router = io.open(SRC + 'exec/Router.kt', encoding='utf-8').read()
    old_var = '''    private val variants: List<Pair<String, (String) -> String>> = listOf(
        "原路径" to { p: String -> p },
        "尾随点" to { p: String -> "$p/." },
        "双斜杠" to { p: String -> p.replace("/Android/", "//Android/") },
        "段内零宽" to { p: String -> p.replace("/Android/", "/Android\\u200b/") },
        "尾随零宽" to { p: String -> "$p\\u200b" },
        "段后零宽" to { p: String -> p.replace("/data", "/data\\u200b").replace("/obb", "/obb\\u200b") }
    )'''
    assert old_var in router, 'variants 没匹配上'
    router = router.replace(old_var, '''    /**一种零宽候选：名称资源 + 路径变换 */
    private data class Variant(val nameRes: Int, val map: (String) -> String)

    private val variants: List<Variant> = listOf(
        Variant(R.string.rz_orig) { p -> p },
        Variant(R.string.rz_dot) { p -> "$p/." },
        Variant(R.string.rz_slash) { p -> p.replace("/Android/", "//Android/") },
        Variant(R.string.rz_in) { p -> p.replace("/Android/", "/Android\\u200b/") },
        Variant(R.string.rz_tail) { p -> "$p\\u200b" },
        Variant(R.string.rz_post) { p -> p.replace("/data", "/data\\u200b").replace("/obb", "/obb\\u200b") }
    )''')

    router = router.replace('    @Volatile private var picked: Pair<String, (String) -> String>? = null',
                            '    @Volatile private var picked: Variant? = null')
    router = router.replace('    fun probeName(): String = if (picked != null) (picked?.first ?: "未知") else "不可用"',
'''    /**零宽命中的那条路径叫什么 */
    fun probeName(c: Context): String = picked?.let { c.getString(it.nameRes) } ?: c.getString(R.string.rz_unavail)''')
    router = router.replace('''        for (v in variants) {
            if (canList(v.second(DATA_ROOT))) {
                picked = v
                return true
            }
        }''',
'''        for (v in variants) {
            if (canList(v.map(DATA_ROOT))) {
                picked = v
                return true
            }
        }''')
    router = router.replace('    fun map(path: String): String = picked?.second?.invoke(path) ?: path',
                            '    fun map(path: String): String = picked?.map?.invoke(path) ?: path')

    old_zone = '''    /** Android/data 与 Android/obb 现在实际靠什么访问，给 UI 与系统提示用 */
    fun dataZone(): String {
        if (known == null) return "检测中…"
        if (nativeOk) {
            return if (Perms.allFiles()) "可直接读写（所有文件访问）" else "可直接读写（原路径）"
        }
        if (zeroWidthUsable) return "零宽路径（${probeName()}）"
        return if (Cfg.zeroWidth) "零宽已开启但本机不支持" else "不可访问（需要 Shizuku / root 或零宽）"
    } //受限目录的实际通道'''
    assert old_zone in router, 'dataZone 没匹配上'
    router = router.replace(old_zone, '''    /**受限目录当前实际走哪条通道 */
    enum class Zone { CHECKING, ALL_FILES, NATIVE, ZERO_WIDTH, UNSUPPORTED, LOCKED }

    /**不带文案的状态：给 UI 上色用 */
    fun zone(): Zone {
        if (known == null) return Zone.CHECKING
        if (nativeOk) return if (Perms.allFiles()) Zone.ALL_FILES else Zone.NATIVE
        if (zeroWidthUsable) return Zone.ZERO_WIDTH
        return if (Cfg.zeroWidth) Zone.UNSUPPORTED else Zone.LOCKED
    } //通道状态

    /**通道能用（读写受限目录没问题） */
    fun zoneOk(): Boolean = zone() == Zone.ALL_FILES || zone() == Zone.NATIVE || zone() == Zone.ZERO_WIDTH

    /** Android/data 与 Android/obb 现在实际靠什么访问，给 UI 与系统提示用 */
    fun dataZone(c: Context): String = when (zone()) {
        Zone.CHECKING -> c.getString(R.string.dz_checking)
        Zone.ALL_FILES -> c.getString(R.string.dz_all)
        Zone.NATIVE -> c.getString(R.string.dz_native)
        Zone.ZERO_WIDTH -> c.getString(R.string.dz_zero, probeName(c))
        Zone.UNSUPPORTED -> c.getString(R.string.dz_unsupported)
        Zone.LOCKED -> c.getString(R.string.dz_locked)
    } //受限目录的实际通道''')
    router = router.replace('package b.ai.exec\n', 'package b.ai.exec\n\nimport android.content.Context\nimport b.ai.R\n', 1)
    io.open(SRC + 'exec/Router.kt', 'w', encoding='utf-8').write(router)
    print('patched exec/Router.kt')

    #Shiz.kt：探测结果文案
    patch('exec/Shiz.kt', [
        ('                Probe(State.NOT_RUNNING, "已安装 Shizuku，但服务未运行：先打开 Shizuku App 启动服务")',
         '                Probe(State.NOT_RUNNING, ctx.getString(R.string.shiz_not_run))'),
        ('                Probe(State.NOT_INSTALLED, "未安装 Shizuku：需先安装 Shizuku，再用 adb 或 root 启动服务")',
         '                Probe(State.NOT_INSTALLED, ctx.getString(R.string.shiz_not_installed))'),
        ('            return Probe(State.NO_PERMISSION, "Shizuku 服务版本过旧（v$ver < 11），请升级 Shizuku", uid)',
         '            return Probe(State.NO_PERMISSION, ctx.getString(R.string.shiz_old, ver), uid)'),
        ('            val backend = if (uid == 0) "root 后端" else "adb 后端"',
         '            val backend = ctx.getString(if (uid == 0) R.string.shiz_back_root else R.string.shiz_back_adb)'),
        ('            return Probe(State.READY, "已授权（服务 v$ver，UID $uid · $backend）", uid)',
         '            return Probe(State.READY, ctx.getString(R.string.shiz_ready, ver, uid, backend), uid)'),
        ('        return Probe(State.NO_PERMISSION, "Shizuku 已运行但本应用未授权：点「申请 Shizuku 授权」", uid)',
         '        return Probe(State.NO_PERMISSION, ctx.getString(R.string.shiz_no_perm), uid)'),
    ])

    #UI 侧调用点
    patch('ui/SettingsScreen.kt', [
        ('''        WorkMode.entries.forEach { m ->
            ChoiceRow(m.label, m.desc, Cfg.workMode == m) { Cfg.workMode = m }
        }''',
         '''        WorkMode.entries.forEach { m ->
            ChoiceRow(stringResource(m.labelRes), stringResource(m.descRes), Cfg.workMode == m) { Cfg.workMode = m }
        }'''),
        ('            else stringResource(R.string.se_150) + missing.joinToString("、") { Perms.label(it) },',
         '            else stringResource(R.string.se_150) + missing.joinToString("、") { Perms.label(ctx, it) },'),
        ('''                //探测在后台线程跑：每秒把结果刷一次，否则 UI 会一直停在「检测中…」
                var zone by remember { mutableStateOf(Router.dataZone()) }
                LaunchedEffect(Unit) {
                    while (true) {
                        zone = Router.dataZone()
                        delay(1000)
                    }
                }
                Text(
                    "当前检测：$zone",
                    fontSize = 11.sp,
                    color = if (zone.startsWith(stringResource(R.string.se_159)) || zone.startsWith(stringResource(R.string.se_157))) OkGreen else TextSecondary
                )''',
         '''                //探测在后台线程跑：每秒把结果刷一次，否则 UI 会一直停在「检测中…」
                var zone by remember { mutableStateOf(Router.dataZone(ctx)) }
                var zoneOk by remember { mutableStateOf(Router.zoneOk()) }
                LaunchedEffect(Unit) {
                    while (true) {
                        zone = Router.dataZone(ctx)
                        zoneOk = Router.zoneOk()
                        delay(1000)
                    }
                }
                Text(
                    stringResource(R.string.se_zone_cur, zone),
                    fontSize = 11.sp,
                    color = if (zoneOk) OkGreen else TextSecondary
                )'''),
        ('''                    "当前：${mode.label}",''', '''                    stringResource(R.string.wm_cur_fmt, stringResource(mode.labelRes)),'''),
    ])

    patch('ui/Onboarding.kt', [
        ('''        WorkMode.entries.forEach { m ->
            ChoiceRow(m.label, m.desc, Cfg.workMode == m) { Cfg.workMode = m }''',
         '''        WorkMode.entries.forEach { m ->
            ChoiceRow(stringResource(m.labelRes), stringResource(m.descRes), Cfg.workMode == m) { Cfg.workMode = m }'''),
        ('stringResource(R.string.ob2_mode_fmt, Cfg.workMode.label)',
         'stringResource(R.string.ob2_mode_fmt, stringResource(Cfg.workMode.labelRes))'),
        ('stringResource(R.string.ob2_linux_warn, Cfg.workMode.label)',
         'stringResource(R.string.ob2_linux_warn, stringResource(Cfg.workMode.labelRes))'),
        ('                    probeName = Router.probeName(),', '                    probeName = Router.probeName(ctx),'),
        ('true -> stringResource(R.string.ob2_zero_yes, Router.probeName())',
         'true -> stringResource(R.string.ob2_zero_yes, Router.probeName(ctx))'),
    ])

    patch('ui/ChatScreen.kt', [
        ('"${Cfg.model.ifBlank { stringResource(R.string.ms_no_model) }} · ${Cfg.workMode.label} · " +',
         '"${Cfg.model.ifBlank { stringResource(R.string.ms_no_model) }} · ${stringResource(Cfg.workMode.labelRes)} · " +'),
    ])

    patch('ui/DialogHost.kt', [
        ('            Tag(p.route.label)', '            Tag(stringResource(p.route.labelRes))'),
        ('                    Text("· ${Perms.label(p)}", fontSize = 12.sp, color = TextPrimary)',
         '                    Text("· " + Perms.label(ctx, p), fontSize = 12.sp, color = TextPrimary)'),
    ])

    patch('agent/Agent.kt', [
        ('append("；Android/data 与 obb：").append(Router.dataZone())',
         'append("；Android/data 与 obb：").append(Router.dataZone(App.self))'),
    ])


main()
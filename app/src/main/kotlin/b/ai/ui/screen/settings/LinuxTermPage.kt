package b.ai.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.core.cfg.app.Cfg
import b.ai.device.shell.local.Linux
import b.ai.device.shell.local.LinuxTerm
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.BorderGray
import b.ai.ui.theme.DangerRed
import b.ai.ui.theme.OkGreen
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import b.ai.ui.widget.CardBox
import b.ai.ui.widget.GhostButton
import b.ai.ui.widget.SectionTitle
import b.ai.ui.widget.SelectField
import b.ai.ui.widget.tap
import b.ai.ui.theme.FontStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Linux 沙箱子页：一个整页的大终端 + 一键装开发环境。
 * 页面只管三件事：环境在不在、常用工具装没装、把命令丢进终端并让用户看得见输出。
 * 所有耗时动作都走 [LinuxTerm] 的后台线程，UI 只订阅 onDirty / onState 两个回调刷新。
 */
@Composable
fun LinuxTermPage() {
    val scope = rememberCoroutineScope()
    //终端行数版本号：LinuxTerm 每吐一行就 +1，用它触发重组
    var ver by remember { mutableIntStateOf(0) }
    //状态版本号：running / ready 变化才 +1，工具安装状态只跟着它重算，别每行都查盘
    var stateVer by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(LinuxTerm.running) }
    var ready by remember { mutableStateOf(LinuxTerm.ready) }
    var envOk by remember { mutableStateOf(Linux.installed()) }
    var input by remember { mutableStateOf("") }

    //订阅终端回调：进页面挂上、退页面摘掉，避免页面销毁后还在写已释放的 state
    DisposableEffect(Unit) {
        LinuxTerm.onDirty = { ver = ver + 1 }
        LinuxTerm.onState = {
            running = LinuxTerm.running
            ready = LinuxTerm.ready
            envOk = Linux.installed()
            stateVer = stateVer + 1
        }
        onDispose {
            LinuxTerm.onDirty = null
            LinuxTerm.onState = null
        }
    } //订阅回调

    //进页面就把会话开起来：装好环境的机器直接能敲命令，没装的会在终端里留一句提示
    LaunchedEffect(stateVer) {
        if (!LinuxTerm.ready) withContext(Dispatchers.IO) { LinuxTerm.start() }
    }

    val text = remember(ver) { LinuxTerm.lines().takeLast(400).joinToString("\n") }
    val present = remember(stateVer) { LinuxTerm.TOOLS.associate { it.id to it.present() } }

    /** 把所有重活丢到 IO 线程：开进程、写 stdin 都可能短暂阻塞 */
    fun io(block: () -> Unit) {
        scope.launch { withContext(Dispatchers.IO) { block() } }
    } //切 IO

    SectionTitle("Linux 沙箱", "内置 Ubuntu（proot）· 终端直连 · 一键装开发环境")

    CardBox {
        //第一行：环境状态 + 重开终端
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    envOk && ready -> "● 环境已就绪"
                    envOk -> "● 环境已装，终端待连接"
                    else -> "● 还没装 Ubuntu"
                },
                fontSize = 12.sp,
                color = if (envOk) OkGreen else TextSecondary,
                modifier = Modifier.weight(1f)
            )
            Text("重开终端", fontSize = 11.sp, color = AccentBlue, modifier = Modifier.tap {
                io {
                    LinuxTerm.stop()
                    LinuxTerm.start()
                }
            })
        }
        Spacer(Modifier.height(10.dp))
        //第二行：环境级操作
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton(if (envOk) "重装环境" else "安装环境", modifier = Modifier.height(38.dp)) {
                LinuxTerm.installEnv(force = envOk)
            }
            GhostButton("一键拓展环境", modifier = Modifier.height(38.dp)) {
                if (!envOk) {
                    LinuxTerm.append("[先装 Ubuntu 环境]点左边「安装环境」")
                } else io { LinuxTerm.submit(LinuxTerm.BASE_ENV) }
            }
            GhostButton("刷新索引", modifier = Modifier.height(38.dp)) {
                if (!envOk) {
                    LinuxTerm.append("[先装 Ubuntu 环境]点左边「安装环境」")
                } else io { LinuxTerm.submit(LinuxTerm.APT_UPDATE) }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("常用开发环境", fontSize = 12.sp, color = TextSecondary)
        //两列网格：点一下就把安装命令丢进终端，输出实时刷在下面
        LinuxTerm.TOOLS.chunked(2).forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { tool ->
                    ToolChip(
                        name = tool.name,
                        hint = tool.hint,
                        done = present[tool.id] == true,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (!envOk) {
                            LinuxTerm.append("[先装 Ubuntu 环境]点上面「安装环境」")
                        } else io { LinuxTerm.submit(tool.cmd) }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(12.dp))
        //环境信息：一眼看出这个「Ubuntu」是什么版本、apt 索引备没备好、装了几个工具
        val osName = remember(stateVer) {
            runCatching {
                File(Linux.rootfs, "etc/os-release").takeIf { it.isFile }?.readLines()
                    ?.firstOrNull { it.startsWith("PRETTY_NAME=") }
                    ?.substringAfter("=")?.trim('"')
            }.getOrNull().orEmpty().ifBlank { "Ubuntu (proot)" }
        } //发行版名
        val arch = remember { android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64" }
        val idxReady = remember(stateVer) { File(Linux.rootfs, "var/lib/apt/.bai_stamp").isFile }
        val doneCount = present.count { it.value }
        Text(
            osName + " · " + arch +
                " · apt 索引" + (if (idxReady) "已备" else "未备") +
                " · 已装 " + doneCount + "/" + LinuxTerm.TOOLS.size,
            fontSize = 10.sp,
            color = TextSecondary
        )

        Spacer(Modifier.height(10.dp))
        Text("快捷命令", fontSize = 12.sp, color = TextSecondary)
        //常用诊断命令：点一下直接进终端跑，省得手打
        val quick = listOf(
            "系统信息" to "uname -a; cat /etc/os-release 2>/dev/null | head -3",
            "磁盘占用" to "df -h / /opt 2>/dev/null",
            "待更新" to "apt list --upgradable 2>/dev/null | head -20",
            "清缓存" to "apt clean; rm -rf /tmp/* 2>/dev/null; echo 缓存已清理",
            "占用排行" to "du -xh / --max-depth=1 2>/dev/null | sort -rh | head -10",
            "网络诊断" to "getent hosts dl.google.com; ip route 2>/dev/null | head -3"
        )
        quick.chunked(3).forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { (label, cmd) ->
                    KeyChip(label, if (envOk) TextPrimary else TextSecondary) {
                        if (!envOk) {
                            LinuxTerm.append("[先装 Ubuntu 环境]点上面「安装环境」")
                        } else io { LinuxTerm.submit(cmd) }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        Spacer(Modifier.height(10.dp))
        //终端字体：配了字体文件就走它，没配仍是内置等宽字
        val termFf = FontStore.family(Cfg.termFontFile) ?: FontFamily.Monospace
        TermPanel(text = text, running = running, fontSp = Cfg.termFont, ff = termFf)
        Spacer(Modifier.height(8.dp))

        //输入行：没命令在跑 → 当新命令执行；有命令在跑 → 当作 stdin 喂给当前命令（apt 的 y/n）
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.weight(1f).height(40.dp)
                    .background(Color(Cfg.termBg), RoundedCornerShape(10.dp))
                    .border(1.dp, Color(Cfg.termFg).copy(alpha = 0.16f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (input.isEmpty()) {
                    Text(
                        if (running) "给当前命令输入内容，回车发送" else "输入命令，回车执行",
                        fontSize = 11.sp,
                        color = Color(Cfg.termFg).copy(alpha = 0.45f)
                    )
                }
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color(Cfg.termFg),
                        fontSize = Cfg.termFont.sp,
                        fontFamily = termFf
                    ),
                    cursorBrush = SolidColor(AccentBlue),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.width(8.dp))
            GhostButton("发送", modifier = Modifier.height(40.dp)) {
                val line = input.trim()
                input = ""
                if (line.isNotEmpty()) {
                    io { if (LinuxTerm.running) LinuxTerm.input(line) else LinuxTerm.submit(line) }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        //功能键：打断 / Tab / 方向键 / Esc / EOF / apt 确认键，直接写进会话 stdin
        //一行放不下就横向滑，别把按钮挤成竖排
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            KeyChip("^C 打断", AccentBlue) { io { LinuxTerm.interrupt() } }
            KeyChip("Tab") { io { LinuxTerm.key("tab") } }
            KeyChip("↑") { io { LinuxTerm.key("up") } }
            KeyChip("↓") { io { LinuxTerm.key("down") } }
            KeyChip("Esc") { io { LinuxTerm.key("esc") } }
            KeyChip("^D") { io { LinuxTerm.key("ctrl-d") } }
            //apt / 脚本的确认键：y 回车确认、n 回车拒绝，直接写进 stdin
            KeyChip("y", OkGreen) { io { LinuxTerm.input("y") } }
            KeyChip("n", DangerRed) { io { LinuxTerm.input("n") } }
        }
        Spacer(Modifier.height(8.dp))
        //终端字号：小屏想看一屏更多行就调小，字太小看着累就调大
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("终端字号", fontSize = 11.sp, color = TextSecondary)
            Spacer(Modifier.width(8.dp))
            KeyChip("－") { Cfg.termFont = Cfg.termFont - 1 }
            Spacer(Modifier.width(6.dp))
            Text("${Cfg.termFont} sp", fontSize = 11.sp, color = TextPrimary)
            Spacer(Modifier.width(6.dp))
            KeyChip("＋") { Cfg.termFont = Cfg.termFont + 1 }
        }
        Spacer(Modifier.height(6.dp))
        //终端字体：候选来自字体库（设置 → 外观 → 字体 里导入的那些）
        val fontOpts = remember { FontStore.options() }
        SelectField(
            "终端字体",
            if (Cfg.termFontFile.isEmpty()) "等宽默认" else FontStore.label(Cfg.termFontFile),
            fontOpts,
            optionLabel = { if (it.isEmpty()) "等宽默认" else FontStore.label(it) },
            isSelected = { Cfg.termFontFile == it },
            onSelect = { Cfg.termFontFile = it }
        )
        Spacer(Modifier.height(6.dp))
        //终端配色：整组换（前景 + 背景），比单独挑两个颜色省事
        Text("终端配色", fontSize = 11.sp, color = TextSecondary)
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TERM_PRESETS.forEach { p ->
                val on = Cfg.termBg == p.bg && Cfg.termFg == p.fg
                Box(
                    Modifier.size(38.dp)
                        .background(Color(p.bg), RoundedCornerShape(10.dp))
                        .border(
                            if (on) 2.dp else 1.dp,
                            if (on) AccentBlue else BorderGray,
                            RoundedCornerShape(10.dp)
                        )
                        .tap { Cfg.termBg = p.bg; Cfg.termFg = p.fg },
                    contentAlignment = Alignment.Center
                ) { Text("Aa", fontSize = 11.sp, color = Color(p.fg)) }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("包管理", fontSize = 12.sp, color = TextSecondary)
        //包名输入：完整包名直接装 / 卸，关键词走 apt-cache 模糊搜索
        var pkQuery by remember { mutableStateOf("") }
        var pkBusy by remember { mutableStateOf(false) }
        var pkRows by remember { mutableStateOf<List<PkgHit>>(emptyList()) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.weight(1f).height(38.dp)
                    .background(SurfaceMuted, RoundedCornerShape(10.dp))
                    .border(1.dp, BorderGray, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (pkQuery.isEmpty()) {
                    Text("输入包名，如 python3 / vim", fontSize = 11.sp, color = TextSecondary)
                }
                BasicTextField(
                    value = pkQuery,
                    onValueChange = { pkQuery = it },
                    singleLine = true,
                    textStyle = TextStyle(color = TextPrimary, fontSize = 12.sp),
                    cursorBrush = SolidColor(AccentBlue),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.width(8.dp))
            GhostButton(if (pkBusy) "搜索中" else "搜索", modifier = Modifier.height(38.dp)) {
                if (!envOk) {
                    LinuxTerm.append("[先装 Ubuntu 环境]点上面「安装环境」")
                } else if (pkQuery.isNotBlank()) io {
                    pkBusy = true
                    pkRows = searchPkg(pkQuery)
                    pkBusy = false
                }
            }
            Spacer(Modifier.width(6.dp))
            //输入项直接当完整包名装：不用先搜
            GhostButton("安装", modifier = Modifier.height(38.dp)) {
                val n = pkQuery.trim()
                when {
                    !envOk -> LinuxTerm.append("[先装 Ubuntu 环境]点上面「安装环境」")
                    !pkgSafe(n) -> LinuxTerm.append("[包名不合法]只允许字母、数字与 . - + _")
                    else -> io { LinuxTerm.submit("bai_apt $n") }
                }
            }
        }
        //搜索结果：已装的给「卸载」，没装的给「安装」；一次最多 60 条
        pkRows.forEach { hit ->
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(hit.name, fontSize = 12.sp, color = TextPrimary)
                    Text(
                        if (hit.installed) "已安装" + (if (hit.ver.isBlank()) "" else " · ${hit.ver}") else "未安装",
                        fontSize = 9.sp,
                        color = if (hit.installed) OkGreen else TextSecondary
                    )
                }
                GhostButton(
                    if (hit.installed) "卸载" else "安装",
                    modifier = Modifier.height(32.dp)
                ) {
                    if (!pkgSafe(hit.name)) {
                        LinuxTerm.append("[包名不合法] $hit.name")
                    } else io {
                        LinuxTerm.submit(
                            if (hit.installed) "export DEBIAN_FRONTEND=noninteractive; apt-get remove -y --purge ${hit.name}"
                            else "bai_apt ${hit.name}"
                        )
                    }
                }
            }
        }
        if (pkBusy) Text("正在查询 apt 索引…", fontSize = 10.sp, color = TextSecondary)

        Spacer(Modifier.height(8.dp))
        Text(
            "非 PTY 会话：命令输出、打断、stdin 输入都可靠；Tab 补全与方向键在需要行编辑的程序里可能只作普通字符。"
                + "首次装包会先更新 apt 索引（约 1-2 分钟，终端里有进度），之后秒开。",
            fontSize = 10.sp,
            color = TextSecondary
        )
    }
} //Linux 沙箱终端页

/** 单个开发环境格子：未装 → 点击安装；已装 → 灰显只做标记 */
@Composable
private fun ToolChip(
    name: String,
    hint: String,
    done: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .height(46.dp)
            .background(
                if (done) OkGreen.copy(alpha = 0.08f) else Color.Transparent,
                RoundedCornerShape(10.dp)
            )
            .border(1.dp, BorderGray, RoundedCornerShape(10.dp))
            .tap { if (!done) onClick() }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(4.dp))
                Text(
                    if (done) "已装" else "安装",
                    fontSize = 9.sp,
                    color = if (done) OkGreen else AccentBlue
                )
            }
            Text(hint, fontSize = 9.sp, color = TextSecondary)
        }
    }
} //开发环境格子

/** 功能键小按钮：比 GhostButton 更窄，一行放得下六个 */
@Composable
private fun KeyChip(label: String, color: Color = TextPrimary, onClick: () -> Unit) {
    Box(
        Modifier
            .height(30.dp)
            .background(Color.Transparent, RoundedCornerShape(8.dp))
            .border(1.dp, BorderGray, RoundedCornerShape(8.dp))
            .tap(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = 11.sp, color = color) }
} //功能键

/** 终端面板：底色 / 正文色 / 字体都跟设置走，行数一多就自动贴底 */
@Composable
private fun TermPanel(text: String, running: Boolean, fontSp: Int, ff: FontFamily) {
    val scroll = rememberScrollState()
    LaunchedEffect(text) { scroll.scrollTo(scroll.maxValue) }
    val bg = Color(Cfg.termBg)
    val fg = Color(Cfg.termFg)
    Column(
        Modifier.fillMaxWidth().height(420.dp)
            .background(bg, RoundedCornerShape(14.dp))
            .border(1.dp, fg.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (running) "● 运行中" else "● 空闲",
                fontSize = 10.sp,
                color = if (running) OkGreen else fg.copy(alpha = 0.45f),
                modifier = Modifier.weight(1f)
            )
            Text("清屏", fontSize = 10.sp, color = AccentBlue, modifier = Modifier.tap {
                LinuxTerm.clear()
            })
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text.ifBlank { "(终端输出会显示在这里)" },
            fontSize = fontSp.sp,
            lineHeight = (fontSp + 3.5f).sp,
            fontFamily = ff,
            color = fg,
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)
        )
    }
} //终端面板

/** 终端配色预设：前景 + 背景整组换 */
private class TermColors(val bg: Int, val fg: Int)

private val TERM_PRESETS = listOf(
    TermColors(0xFF0E1116.toInt(), 0xFFD5DBE5.toInt()), //默认深灰
    TermColors(0xFF0B1512.toInt(), 0xFF9BE8B4.toInt()), //墨绿
    TermColors(0xFF14101F.toInt(), 0xFFC9BEF5.toInt()), //暗紫
    TermColors(0xFF000000.toInt(), 0xFFE6E6E6.toInt()), //纯黑
    TermColors(0xFFF7F8FA.toInt(), 0xFF1B1F26.toInt()) //浅色
)

/** 包搜索命中的一行 */
private class PkgHit(val name: String, val ver: String, val installed: Boolean)

/** 包名白名单：挡住空格、分号、$() 这类能拼进 shell 的字符 */
private fun pkgSafe(n: String): Boolean =
    n.isNotBlank() && n.all { it.isLetterOrDigit() || it == '.' || it == '-' || it == '+' || it == '_' }

/**
 * 模糊搜包：已装列表（dpkg-query）与可装列表（apt-cache search --names-only）各取前 40 条，
 * 合并去重后按名字排序。索引没备时 apt 段为空，只回已装列表。
 */
private fun searchPkg(kw: String): List<PkgHit> {
    val k = kw.trim()
    if (k.isEmpty()) return emptyList()
    val cmd = buildString {
        append("echo ===INST===\n")
        //\${Package} 这层 $ 要转义，否则会被 Kotlin 当模板
        append("dpkg-query -W -f='\${Package}\\t\${Version}\\t\${Status}\\n' 2>/dev/null | grep -i -- '")
        append(k).append("' | head -40\n")
        append("echo ===AVAIL===\n")
        append("apt-cache search --names-only -- '").append(k).append("' 2>/dev/null | sed 's/ - .*//' | head -40\n")
    }
    val out = Linux.run(cmd, 60_000L).out
    val inst = LinkedHashMap<String, PkgHit>()
    val avail = ArrayList<PkgHit>()
    var mode = ""
    for (line in out.lineSequence()) {
        val s = line.trim()
        when {
            s == "===INST===" -> mode = "i"
            s == "===AVAIL===" -> mode = "a"
            s.isEmpty() -> {}
            //已装：名字 \t 版本 \t 状态
            mode == "i" -> {
                val f = s.split("\t")
                if (f.size >= 3) inst[f[0]] = PkgHit(f[0], f[1], true)
            }
            //可装：apt-cache 的「名字 - 描述」已经被 sed 切掉描述，只取第一列
            mode == "a" -> {
                val name = s.substringBefore(' ').trim()
                if (name.isNotEmpty() && name !in inst) avail.add(PkgHit(name, "", false))
            }
        }
    }
    return (inst.values + avail).distinctBy { it.name }.sortedBy { it.name }.take(60)
} //模糊搜包
/**
 * DevPanel.kt — 开发者模式
 *
 * 结构：
 * 1) Dev（单例）：关于页连点版本号 5 次解锁；
 * 2) DevSection：设置里只露一张入口卡（开关 + 一个「打开开发者工具」按钮）；
 * 3) DevToolsPage：点进去后的分类测试页 —— 按「对话与模型 / 权限与命令 / 沙箱与镜像」分组，
 *    每新增一个功能就在这里加一条测试项，不再把按钮铺在设置页上。
 */
package b.ai.ui.screen.settings

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.App
import b.ai.agent.tool.Tools
import b.ai.core.cfg.app.Bai
import b.ai.core.cfg.app.Cfg
import b.ai.core.data.io.Layout
import b.ai.core.plugin.skill.Providers
import b.ai.device.shell.local.Linux
import b.ai.device.shell.local.LinuxMirror
import b.ai.device.shell.local.LinuxTerm
import b.ai.device.shell.local.Shell
import b.ai.device.shell.root.Root
import b.ai.device.shell.root.Shiz
import b.ai.net.api.llm.LlmClient
import b.ai.net.api.update.UpdateMirror
import b.ai.ui.glass.liquidBg
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.DangerRed
import b.ai.ui.theme.OkGreen
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import b.ai.ui.widget.CardBox
import b.ai.ui.widget.GhostButton
import b.ai.ui.widget.tap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 开发者模式的解锁计数。
 * 计数必须活在单例里：关于页每个块是一个独立 item，滚出屏幕就被回收，放在 remember 里连点两下就清零。
 */
object Dev {
    private var taps = 0
    private var lastAt = 0L

    /** 点一下版本号：3 秒内连点 5 次就算解锁，返回这次是否刚好解开 */
    fun tap(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastAt > 3000) taps = 0
        lastAt = now
        taps++
        if (taps < 5) return false
        taps = 0
        return true
    } //连点解锁
} //开发者模式

/** 设置页里的开发者卡：只有开关和一个入口，具体测试都在 DevToolsPage 里 */
@Composable
fun DevSection() {
    var open by remember { mutableStateOf(false) }

    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("开发者选项", fontSize = 14.sp, color = TextPrimary)
                Text(
                    if (open) "按分类自检，每加一个功能就多一条测试" else "关掉后这张卡与入口一起收起，开关状态保留",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
            Switch(
                checked = Cfg.devOn,
                onCheckedChange = { v ->
                    Cfg.devOn = v
                    if (!v) open = false //关掉总开关时退出测试页，避免下次进来停在内页
                },
                colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
            )
        }
        if (!Cfg.devOn) return@CardBox

        Spacer(Modifier.height(10.dp))
        if (!open) {
            //入口：整行可点，比一排小按钮好点也好找
            Row(
                Modifier
                    .fillMaxWidth()
                    .liquidBg(RoundedCornerShape(10.dp), tint = SurfaceMuted)
                    .tap { open = true }
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("打开开发者工具", fontSize = 12.sp, color = AccentBlue, modifier = Modifier.weight(1f))
                Text("▸", fontSize = 12.sp, color = AccentBlue)
            }
        } else {
            DevToolsPage(onBack = { open = false })
        }
    }
} //开发者卡

/** 一条测试：key 用于防重入，run 返回要贴在结果区里的文本 */
private class DevItem(
    val key: String,
    val label: String,
    val run: suspend (append: (String) -> Unit) -> String
)

/** 测试分类：新增功能时在对应分类里加一条，别再加到设置页上 */
private fun devGroups(): List<Pair<String, List<DevItem>>> = listOf(
    "对话与模型" to listOf(
        DevItem("chat", "测试对话") { _ -> testChat() }
    ),
    "权限与命令" to listOf(
        DevItem("policy", "工具权限") { _ -> toolPolicy() },
        DevItem("shell", "命令挂载") { _ -> withContext(Dispatchers.IO) { testShell() } },
        DevItem("device", "设备信息") { _ -> withContext(Dispatchers.IO) { deviceInfo() } }
    ),
    "沙箱与镜像" to listOf(
        DevItem("manifest", "镜像清单") { _ -> withContext(Dispatchers.IO) { testManifest() } },
        DevItem("mirror", "镜像测速") { _ -> withContext(Dispatchers.IO) { testMirror() } },
        //更新包镜像：清单 + 测速 + 择优，排查「更新弹窗里下载不动」先看这条
        DevItem("upmirror", "更新镜像") { _ -> withContext(Dispatchers.IO) { testUpdateMirror() } },
        DevItem("selftest", "环境自检") { _ -> withContext(Dispatchers.IO) { Linux.selfTest().out } },
        //一键卸载重装：走的就是设置里「重装环境」那条 install(force=true)，日志边跑边贴
        DevItem("reset", "一键卸载重装") { append -> withContext(Dispatchers.IO) { resetSandbox(append) } }
    )
)

/** 分类测试页：分组按钮 + 结果区（可复制） */
@Composable
private fun DevToolsPage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var log by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf("") }

    /** 日志追加：跑在后台线程的回调也能安全刷新 UI */
    fun append(s: String) {
        scope.launch { log = (log + s + "\n").takeLast(6000) }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("← 返回", fontSize = 12.sp, color = AccentBlue, modifier = Modifier.tap { onBack() })
        Spacer(Modifier.height(0.dp))
        Text(
            if (busy.isBlank()) "开发者工具" else "执行中：$busy",
            fontSize = 12.sp,
            color = TextSecondary,
            modifier = Modifier.padding(start = 10.dp)
        )
    }

    devGroups().forEach { (group, items) ->
        Spacer(Modifier.height(12.dp))
        Text(group, fontSize = 11.sp, color = TextSecondary)
        items.chunked(2).forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { item ->
                    GhostButton(
                        if (busy == item.key) "执行中…" else item.label,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (busy.isNotBlank()) return@GhostButton
                        busy = item.key
                        scope.launch {
                            val text = runCatching { item.run(::append) }
                                .getOrElse { "失败：${it.message ?: it.javaClass.simpleName}" }
                            log = (log + "===== ${item.label} =====\n" + text + "\n").takeLast(6000)
                            busy = ""
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }

    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GhostButton("复制结果", modifier = Modifier.height(38.dp)) {
            if (log.isBlank()) return@GhostButton
            copyText(ctx, log)
        }
        GhostButton("清空", modifier = Modifier.height(38.dp)) { log = "" }
    }
    if (log.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(
            log,
            fontSize = 10.sp,
            color = TextPrimary,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxWidth()
                .liquidBg(RoundedCornerShape(8.dp), tint = SurfaceMuted)
                .padding(8.dp)
        )
    }
} //分类测试页

/** 单条 ping：只看通不通、回没回正文，不看内容质量 */
private fun testChat(): String = runCatching {
    val key = Cfg.apiKey
    if (Cfg.baseUrl.isBlank()) return "未配置 base_url"
    if (key.isBlank()) return "未配置 API Key"
    val m = LlmClient(Cfg.baseUrl, key, Providers.byId(Cfg.providerId).auth)
    val msgs = JSONArray().put(JSONObject().put("role", "user").put("content", "ping，只回 ok 两个字符"))
    val t0 = SystemClock.elapsedRealtime()
    val msg = m.chat(Cfg.model, msgs, think = Cfg.think, thinkEffort = Cfg.thinkEffort)
    val cost = SystemClock.elapsedRealtime() - t0
    val body = msg.optString("content").ifBlank { msg.toString() }
    "模型 ${Cfg.model} 通 / 耗时 ${cost}ms\n返回：${body.take(200)}"
}.getOrElse { "失败：${it.message?.take(300) ?: it.javaClass.simpleName}" } //测试对话

/** 工具权限：按当前策略分三类，模型一旦被判死就是这里配错了 */
private fun toolPolicy(): String {
    val all = Tools.all
    val allow = ArrayList<String>()
    val ask = ArrayList<String>()
    val deny = ArrayList<String>()
    all.forEach { n ->
        when (Cfg.policy(n)) {
            "allow" -> allow.add(n)
            "deny" -> deny.add(n)
            else -> ask.add(n)
        }
    }
    val off = all.filter { !Tools.allowed(it) }
    return "共 ${all.size} 个工具\n放行 ${allow.size}：${allow.take(12).joinToString(", ")}\n" +
        "每次确认 ${ask.size}\n禁止 ${deny.size}：${deny.take(12).joinToString(", ")}\n" +
        "当前模式不可用 ${off.size}：${off.take(12).joinToString(", ")}"
} //工具权限

/** 三条通道各跑一条 id：哪条通、跑出来是谁，一眼看明白 */
private fun testShell(): String {
    val sb = StringBuilder()
    val local = Shell.run("id", timeoutMs = 15_000)
    sb.append("应用内命令 code=${local.code}：" + local.out.trim().take(120) + "\n")
    val su = Root.run("id", timeoutMs = 20_000)
    sb.append("root code=${su.code}：" + su.out.trim().take(120) + "\n")
    val shiz = Shiz.run("id", timeoutMs = 20_000)
    sb.append("Shizuku code=${shiz.code}：" + shiz.out.trim().take(120))
    return sb.toString()
} //命令挂载

/** 设备与目录：报问题时这几个数最有用 */
private fun deviceInfo(): String {
    val sb = StringBuilder()
    sb.append("设备：${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n")
    sb.append("系统：Android ${android.os.Build.VERSION.RELEASE} / SDK ${android.os.Build.VERSION.SDK_INT}\n")
    sb.append("ABI：${android.os.Build.SUPPORTED_ABIS.joinToString(", ")}\n")
    sb.append("应用目录：${App.self.filesDir.absolutePath}\n")
    sb.append("数据根：${runCatching { Layout.dir(App.self, Layout.USER).absolutePath }.getOrElse { "?" }}\n")
    val df = File(App.self.filesDir, "").usableSpace / 1024 / 1024
    sb.append("可用空间：${df} MB\n")
    sb.append("Linux：proot=${Linux.prootReady()} rootfs=${Linux.installed()}")
    return sb.toString()
} //设备信息

/**
 * 镜像清单：拉服务端 nodes.php，把文件元信息与节点列表逐条打出来。
 * 排查「装不上环境」先看这个：清单 404 / 字段缺失 / 节点为空都会体现在这里。
 */
private fun testManifest(): String = buildString {
    appendLine("=== 镜像清单 ===")
    appendLine("接口：${Bai.api("/api/linux/nodes.php")}")
    val m = LinuxMirror.fetchManifest()
    if (m == null) {
        appendLine("拉取失败：${LinuxMirror.lastError.ifBlank { "接口不可达，或返回的不是合法 JSON" }}")
        appendLine("内置兜底节点 ${LinuxMirror.FALLBACK_NODES.size} 个仍可用")
        return@buildString
    }
    appendLine("文件：${m.meta.name} / ${m.meta.size} B / sha256 ${m.meta.sha256.take(16)}…")
    appendLine("达标阈值：TTFB ≤ ${LinuxMirror.fastTtfbMs}ms")
    m.nodes.forEachIndexed { i, n ->
        appendLine("%d. %s (%s) %s".format(i + 1, n.name, n.id, n.url))
    }
} //镜像清单

/**
 * 镜像测速：并行探测全部节点，打印 TTFB / 速度 / 是否达标，并给出自动择优结果。
 * 手动节点（设置里选过的）单独标注一行，方便确认「手动优先」是否被正确识别。
 */
private fun testMirror(): String = buildString {
    appendLine("=== 镜像测速 ===")
    val m = LinuxMirror.fetchManifest()
    val nodes = m?.nodes ?: LinuxMirror.FALLBACK_NODES
    appendLine("节点来源：${if (m != null) "服务端清单" else "内置兜底（${LinuxMirror.lastError}）"}，共 ${nodes.size} 个")
    val manual = Cfg.linuxMirrorNode
    appendLine("手动节点：${manual.ifBlank { "未指定（自动择优）" }}")
    val probes = LinuxMirror.probeAll(nodes)
    LinuxMirror.order(probes).forEachIndexed { i, p ->
        val head = "%d. %s (%s)".format(i + 1, p.node.name, p.node.id)
        if (!p.ok) appendLine("$head → 不可用（${p.err}）")
        else appendLine("$head → TTFB ${p.ttfbMs}ms / ${p.speedText()}${if (p.fast()) " 达标" else ""}")
    }
    val best = LinuxMirror.pick(probes)
    appendLine("择优：${best?.node?.name ?: "无可用节点"}")
} //镜像测速

/**
 * 更新包镜像：拉 /api/update/nodes.php，打印包元信息、节点清单、逐节点测速与择优结果。
 * 「更新弹窗里点直接下载没反应」先看这条：清单 404 / 节点为空 / 全不可用都能一眼看出。
 */
private fun testUpdateMirror(): String = buildString {
    appendLine("=== 更新镜像 ===")
    appendLine("接口：${UpdateMirror.endpoint()}")
    val m = UpdateMirror.fetchManifest()
    if (m == null) {
        appendLine("拉取失败：${UpdateMirror.lastError.ifBlank { "接口不可达，或返回的不是合法 JSON" }}")
        appendLine("此时更新弹窗会退回 get.php 下发的 downloadDirectUrl 直链")
        return@buildString
    }
    appendLine("包：${m.meta.name} / ${m.meta.size} B / sha256 ${m.meta.sha256.take(16)}…")
    appendLine("达标阈值：TTFB ≤ ${UpdateMirror.fastTtfbMs}ms")
    appendLine("节点 ${m.nodes.size} 个，开始测速…")
    val probes = UpdateMirror.probeAll(m.nodes)
    UpdateMirror.order(probes).forEachIndexed { i, p ->
        val head = "%d. %s (%s)".format(i + 1, p.node.name, p.node.id)
        if (!p.ok) appendLine("$head → 不可用（${p.err}）")
        else appendLine("$head → TTFB ${p.ttfbMs}ms / ${p.speedText()}${if (p.fast()) " 达标" else ""}")
    }
    val best = UpdateMirror.pick(probes)
    appendLine("择优：${best?.node?.name ?: "无可用节点"}")
} //更新镜像

/**
 * 一键卸载重装沙箱：先收掉终端会话，再走 install(force=true) 整棵替换 rootfs。
 * 与设置页「重装环境」是同一套逻辑，这里只是把日志流到测试页里。
 */
private fun resetSandbox(append: (String) -> Unit): String {
    append("[卸载重装] 收掉现有终端会话…")
    runCatching { LinuxTerm.stop() }
    val ok = Linux.install(
        force = true,
        onStep = { c, t, s -> append("[$c/$t] $s") },
        onLog = { line -> append(line) }
    )
    if (!ok) return "重装失败：看上面日志，确认网络与镜像源后重试"
    val self = Linux.selfTest().out
    return "重装完成\n$self"
} //一键卸载重装

/** 复制到剪贴板：反馈问题时直接把日志贴出去 */
private fun copyText(ctx: android.content.Context, text: String) {
    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        ?: return
    cm.setPrimaryClip(android.content.ClipData.newPlainText("bai-dev", text))
} //复制结果
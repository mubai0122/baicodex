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
import b.ai.agent.tool.Tools
import b.ai.core.cfg.app.Cfg
import b.ai.core.data.io.Layout
import b.ai.core.plugin.skill.Providers
import b.ai.device.shell.local.Shell
import b.ai.device.shell.root.Root
import b.ai.device.shell.root.Shiz
import b.ai.net.api.llm.LlmClient
import b.ai.ui.glass.liquidBg
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import b.ai.ui.widget.CardBox
import b.ai.ui.widget.GhostButton
import b.ai.ui.widget.tap
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

/** 开发者工具：四项自检，结果直接贴在这张卡里，也能一键复制去反馈 */
@Composable
fun DevSection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var log by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf("") }

    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("开发者选项", fontSize = 14.sp, color = TextPrimary)
                Text("关掉后这张卡与工具入口一起收起，开关状态保留", fontSize = 11.sp, color = TextSecondary)
            }
            Switch(
                checked = Cfg.devOn,
                onCheckedChange = { Cfg.devOn = it },
                colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
            )
        }
        if (!Cfg.devOn) return@CardBox
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton(if (busy == "chat") "测试中…" else "测试对话", modifier = Modifier.height(38.dp)) {
                if (busy.isNotEmpty()) return@GhostButton
                busy = "chat"
                scope.launch {
                    log = withContext(Dispatchers.IO) { testChat() }
                    busy = ""
                }
            }
            GhostButton("工具权限", modifier = Modifier.height(38.dp)) {
                if (busy.isNotEmpty()) return@GhostButton
                log = toolPolicy()
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton(if (busy == "cmd") "执行中…" else "命令挂载", modifier = Modifier.height(38.dp)) {
                if (busy.isNotEmpty()) return@GhostButton
                busy = "cmd"
                scope.launch {
                    log = withContext(Dispatchers.IO) { testShell(ctx) }
                    busy = ""
                }
            }
            GhostButton("设备信息", modifier = Modifier.height(38.dp)) {
                if (busy.isNotEmpty()) return@GhostButton
                log = deviceInfo(ctx)
            }
        }
        Spacer(Modifier.height(8.dp))
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
    }
} //开发者工具

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
    return "共 ${all.size} 个工具\n放行 ${allow.size}：${allow.take(12).joinToString(", ")}\n每次确认 ${ask.size}\n禁止 ${deny.size}：${deny.take(12).joinToString(", ")}\n当前模式不可用 ${off.size}：${off.take(12).joinToString(", ")}"
} //工具权限

/** 三条通道各跑一条 id：哪条通、跑出来是谁，一眼看明白 */
private fun testShell(ctx: android.content.Context): String {
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
private fun deviceInfo(ctx: android.content.Context): String {
    //同包里有别的 Build 类型，这里必须写全限定名，不然 android.os.Build 会被遮住
    val abi = android.os.Build.SUPPORTED_ABIS.joinToString(",")
    val roots = listOf(Layout.DATA, Layout.IMG, Layout.CACHE)
        .joinToString("  ") { d -> "$d=${sizeOf(Layout.dir(ctx, d)) / 1024 / 1024}M" }
    return "机型 ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n系统 ${android.os.Build.VERSION.RELEASE} / SDK ${android.os.Build.VERSION.SDK_INT}\nABI $abi\n应用 ${b.ai.net.api.update.Update.clientVersion()}\n目录 $roots"
} //设备信息

/** 目录总占用：递归累加，读不到的文件按 0 算 */
private fun sizeOf(f: File): Long = runCatching {
    if (f.isFile) f.length() else f.listFiles()?.sumOf { sizeOf(it) } ?: 0L
}.getOrDefault(0L) //目录体积

/** 复制到剪贴板：反馈时直接贴出去 */
private fun copyText(ctx: android.content.Context, text: String) {
    runCatching {
        val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        cm?.setPrimaryClip(android.content.ClipData.newPlainText("bai-dev", text))
    }
} //复制结果
/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:22
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * DevPanel.kt — 开发者模式
 *
 * 结构：
 * 1) Dev（单例）：关于页连点版本号 5 次解锁；
 * 2) DevSection：设置里只露一张入口卡（开关 + 一个「打开开发者工具」按钮）；
 * 3) DevToolsPage：点进去后的分类测试页 —— 按「对话与模型 / 权限与命令 / 沙箱与镜像」分组，
 *    每新增一个功能就在这里加一条测试项，不再把按钮铺在设置页上。
 */
package b.ai.ui.screen.settings.agent
import b.ai.ui.screen.settings.about.*
import b.ai.ui.screen.settings.agent.*
import b.ai.ui.screen.settings.core.*
import b.ai.ui.screen.settings.ia.*
import b.ai.ui.screen.settings.model.*
import b.ai.ui.screen.settings.perm.*
import b.ai.ui.screen.settings.ui.*
import b.ai.ui.screen.settings.chat.*
import b.ai.ui.screen.settings.kit.*

import androidx.compose.ui.res.stringResource
import b.ai.R
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
import b.ai.device.fs.Workspace
import b.ai.device.shell.local.Linux
import b.ai.device.shell.local.LinuxMirror
import b.ai.device.shell.local.LinuxTerm
import b.ai.device.shell.local.Shell
import b.ai.device.shell.root.Root
import b.ai.device.shell.root.Shiz
import b.ai.net.api.llm.LlmClient
import b.ai.net.api.llm.NativeSearch
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
                Text(stringResource(R.string.tx_1b6949f5), fontSize = 14.sp, color = TextPrimary)
                Text(
                    if (open) stringResource(R.string.tx_da59f6c4) else stringResource(R.string.tx_0ac3aa0a),
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
                Text(stringResource(R.string.tx_98260c63), fontSize = 12.sp, color = AccentBlue, modifier = Modifier.weight(1f))
                Text("▸", fontSize = 12.sp, color = AccentBlue)
            }
        } else {
            DevToolsPage(onBack = { open = false })
        }
    }
} //开发者卡

/** 一条测试：key 用于防重入，run 返回要贴在结果区里的文本 */
internal class DevItem(
    val key: String,
    val label: String,
    val run: suspend (append: (String) -> Unit) -> String
)

/** 测试分类：新增功能时在对应分类里加一条，别再加到设置页上 */
internal fun devGroups(): List<Pair<String, List<DevItem>>> = listOf(
    "对话与模型" to listOf(
        DevItem("chat", b.ai.App.self.getString(R.string.tx_b52f2284)) { _ -> testChat() },
        //原生联网搜索：逐厂商打印注入后的请求体片段，排查「勾了没生效 / 勾了报 400」
        DevItem("nsearch", b.ai.App.self.getString(R.string.tx_5c362d71)) { _ -> withContext(Dispatchers.IO) { nativeSearchDump() } },
        //工作区模式：关自动绑定应落在同一个 default 目录，开则每会话一个哈希目录
        DevItem("wsmode", b.ai.App.self.getString(R.string.tx_2acb7b31)) { _ -> withContext(Dispatchers.IO) { workspaceDump() } },
        //思考规则：当前模型是否被判定为支持思考、命中哪条规则、最终往 body 里写了什么
        DevItem("thinkrule", b.ai.App.self.getString(R.string.tx_6e081357)) { _ -> thinkDump() },
        //花括号参数：直接打印渲染结果，排查「发出去还是 {time}」这类问题
        DevItem("vars", b.ai.App.self.getString(R.string.tx_c4952839)) { _ -> varsDump() },
        //提示词优化：连接用的是哪份配置、有没有 Key（不真发请求，避免白烧 token）
        DevItem("promptopt", b.ai.App.self.getString(R.string.tx_a2d86e87)) { _ -> promptOptDump() },
        //草稿：目录里有几份、当前会话有没有草稿、过期策略是否生效
        DevItem("draft", b.ai.App.self.getString(R.string.tx_5614002d)) { _ -> withContext(Dispatchers.IO) { draftDump() } },
        //聊天记录检索：库里有几条消息、能搜到几条，验证 chat_search 的落库与 SQL 都通
        DevItem("chatsearch", b.ai.App.self.getString(R.string.tx_f348d6c3)) { _ -> withContext(Dispatchers.IO) { chatSearchDump() } },
        //技能 @：技能包数量与 @token 解析结果，验证「蓝色标签 + 整段删除」认得出来
        DevItem("skillat", b.ai.App.self.getString(R.string.tx_efcc3f48)) { _ -> skillAtDump() },
        //记忆整理：条数、整理模型、可用性（不真发请求）
        DevItem("memorg", b.ai.App.self.getString(R.string.tx_f65125e4)) { _ -> memOrgDump() },
        //STT 引擎：当前引擎与各引擎的必备配置是否齐（不录音）
        DevItem("stt", b.ai.App.self.getString(R.string.tx_2604bcce)) { _ -> sttDump() },
        //提示词种子：21 条系统提示词逐条标出「构建期种子 / 代码兜底」，验证 local.properties 是否真的注入了
        DevItem("seedprompt", b.ai.App.self.getString(R.string.tx_9f3c1a7e)) { _ -> b.ai.agent.run.PromptStore.diagnose() },
        //接口协议：当前走 /responses 还是 /chat/completions，并按该协议真发一条（Responses 相关问题的第一手现场）
        DevItem("protocol", b.ai.App.self.getString(R.string.tx_2b7d4e10)) { _ -> withContext(Dispatchers.IO) { protocolDump() } },
        //桌宠峰谷：当前算高峰还是错峰、今天什么日子、节假日表有没有拉到（口径：工作日 9–12 / 14–18）
        DevItem("peakhours", "桌宠峰谷时段") { _ -> withContext(Dispatchers.IO) { b.ai.core.pet.peak.PeakHours.describe() } },
        //节假日表：手动联网刷一份当年数据（断网就继续按周一至周五兜底）
        DevItem("peakrefresh", "刷新节假日表") { _ ->
            withContext(Dispatchers.IO) {
                b.ai.core.pet.peak.PeakHours.refresh(java.util.Calendar.getInstance().get(java.util.Calendar.YEAR))
            }
        }
    ),
    "权限与命令" to listOf(
        DevItem("policy", b.ai.App.self.getString(R.string.tx_7e50d092)) { _ -> toolPolicy() },
        DevItem("shell", b.ai.App.self.getString(R.string.tx_ecfc4318)) { _ -> withContext(Dispatchers.IO) { testShell() } },
        DevItem("device", b.ai.App.self.getString(R.string.tx_b967fdef)) { _ -> withContext(Dispatchers.IO) { deviceInfo() } }
    ),
    "沙箱与镜像" to listOf(
        DevItem("manifest", b.ai.App.self.getString(R.string.tx_bd5451bc)) { _ -> withContext(Dispatchers.IO) { testManifest() } },
        DevItem("mirror", b.ai.App.self.getString(R.string.tx_e22c257e)) { _ -> withContext(Dispatchers.IO) { testMirror() } },
        //更新包镜像：清单 + 测速 + 择优，排查「更新弹窗里下载不动」先看这条
        DevItem("upmirror", b.ai.App.self.getString(R.string.tx_159a3a80)) { _ -> withContext(Dispatchers.IO) { testUpdateMirror() } },
        DevItem("selftest", b.ai.App.self.getString(R.string.tx_c5d9c84c)) { _ -> withContext(Dispatchers.IO) { Linux.selfTest().out } },
        //一键卸载重装：走的就是设置里「重装环境」那条 install(force=true)，日志边跑边贴
        DevItem("reset", b.ai.App.self.getString(R.string.tx_cb201444)) { append -> withContext(Dispatchers.IO) { resetSandbox(append) } },
        DevItem("dsh", b.ai.App.self.getString(R.string.dh_dev_selftest)) { append -> withContext(Dispatchers.IO) { dshSelfTest(append) } },
        // SDK 直连自检：无 Web UI 的 dsh 运行时 + 一条真实提问，验证插件/工具链路
        DevItem("dsksdk", b.ai.App.self.getString(R.string.dh_dev_sdk_selftest)) { append -> withContext(Dispatchers.IO) { dshSdkSelfTest(append) } }
    )
)

/** 分类测试页：分组按钮 + 结果区（可复制） */
@Composable
internal fun DevToolsPage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var log by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf("") }
    var showDsh by remember { mutableStateOf(false) } //dsh 测试页开关（内嵌 WebView）

    /** 日志追加：跑在后台线程的回调也能安全刷新 UI */
    fun append(s: String) {
        scope.launch { log = (log + s + "\n").takeLast(6000) }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.tx_ba769ad1), fontSize = 12.sp, color = AccentBlue, modifier = Modifier.tap { onBack() })
        Spacer(Modifier.height(0.dp))
        Text(
            if (busy.isBlank()) stringResource(R.string.tx_ed275d6f) else "执行中：$busy",
            fontSize = 12.sp,
            color = TextSecondary,
            modifier = Modifier.padding(start = 10.dp).weight(1f)
        )
        GhostButton(if (showDsh) stringResource(R.string.dh_dev_back_to_items) else stringResource(R.string.dh_dev_page), modifier = Modifier.height(32.dp)) {
            showDsh = !showDsh
        }
    }

    //dsh 测试页：内嵌官方 Web UI，只在这一层出现，不进生产路径
    if (showDsh) {
        DshPage()
        return
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
                        if (busy == item.key) stringResource(R.string.tx_bef6b130) else item.label,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (busy.isNotBlank()) return@GhostButton
                        busy = item.key
                        scope.launch {
                            val text = runCatching { item.run(::append) }
                                .getOrElse { b.ai.App.self.getString(R.string.tx_d8f20845, it.message ?: it.javaClass.simpleName) }
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
        GhostButton(stringResource(R.string.tx_cde0ad30), modifier = Modifier.height(38.dp)) {
            if (log.isBlank()) return@GhostButton
            copyText(ctx, log)
        }
        GhostButton(stringResource(R.string.tx_288f0c40), modifier = Modifier.height(38.dp)) { log = "" }
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

/** 思考规则：把「是否支持思考 → 命中哪条规则 → 实际写入字段」一条链打出来 */
internal fun thinkDump(): String {
    val model = Cfg.model
    val ok = b.ai.core.model.chat.Thinking.supported(Cfg.providerId, model)
    val hit = b.ai.net.api.llm.ThinkRules.hit(model)
    val sb = StringBuilder()
    sb.append(b.ai.App.self.getString(R.string.tx_cbb1ac45, Cfg.providerId))
    sb.append("名单参考（不参与门控）：$ok\n")
    sb.append("实际下发判据：modelReasoning=${Cfg.modelReasoning} think=${Cfg.think}\n")
    sb.append("命中规则：${hit?.let { "${it.id} pattern=${it.pattern} match=${it.match}" } ?: "无（走内置默认）"}\n")
    sb.append("档位表：${b.ai.core.model.chat.Effort.levels(Cfg.providerId, model, Cfg.profThinkLevels).joinToString("/")}\n")
    //模拟一次下发：看规则到底往 body 里写了什么
    val body = org.json.JSONObject()
    val wrote = b.ai.net.api.llm.ThinkRules.apply(body, model, true, Cfg.thinkLevel)
    sb.append("规则写入：wrote=$wrote body=$body\n")
    sb.append(b.ai.App.self.getString(R.string.tx_803ae150, Cfg.think, Cfg.thinkEffort, Cfg.modelReasoning))
    return sb.toString()
} //思考规则

/** 花括号参数：渲染一段包含全部占位符的样例文本 */
internal fun varsDump(): String {
    val sample = b.ai.core.cfg.env.PromptVars.ALL.joinToString(" ") { it.token }
    return b.ai.App.self.getString(R.string.tx_2b10449e, b.ai.core.cfg.env.PromptVars.fill(sample)) +
        b.ai.App.self.getString(R.string.tx_430495aa, b.ai.core.cfg.env.PromptVars.ALL.size) +
        b.ai.core.cfg.env.PromptVars.ALL.joinToString(", ") { it.token + "(" + it.label + ")" }
} //花括号参数

/** 提示词优化：只看连接与配置，不发请求 */
internal fun promptOptDump(): String {
    val prof = Cfg.promptOptProfileId
    val label = b.ai.net.api.llm.PromptOptimizer.label()
    return "优化用配置：${prof.ifBlank { "跟随当前聊天" }}\n" +
        "实际连接：$label\n" +
        b.ai.App.self.getString(R.string.tx_12a1e1ea, Cfg.promptOptKeep) +
        b.ai.App.self.getString(R.string.tx_cf954d3a, Cfg.promptOptStyle.length) +
        b.ai.App.self.getString(R.string.tx_238b0ba6, Cfg.replyStyle.length)
} //提示词优化

/** 草稿：目录统计 + 当前会话草稿 */
internal fun draftDump(): String {
    val dir = java.io.File(b.ai.core.cfg.io.Paths.base(), "drafts")
    val files: List<java.io.File> = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
    val sb = StringBuilder()
    sb.append(b.ai.App.self.getString(R.string.tx_c8ad176b, dir.absolutePath))
    sb.append(b.ai.App.self.getString(R.string.tx_215a9629, files.size))
    sb.append(b.ai.App.self.getString(R.string.tx_354d5f73, Cfg.draftKeepImg, Cfg.draftDays))
    val sid = b.ai.agent.run.Agent.sessionId.orEmpty()
    val cur = b.ai.core.cfg.io.Drafts.load(sid)
    sb.append(b.ai.App.self.getString(R.string.tx_86c68249, cur.empty(), cur.text.length, cur.images.size, cur.files.size))
    files.take(6).forEach { f ->
        sb.append("· ${f.name} ${f.length()} B ${java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(f.lastModified()))}\n")
    }
    return sb.toString()
} //草稿存储

/** 单条 ping：只看通不通、回没回正文，不看内容质量 */
internal fun testChat(): String = runCatching {
    val key = Cfg.apiKey
    //本地后端不碰 base_url / Key：键为空是正常的，别在这里误报「未配置」
    val local = b.ai.core.llm.LocalEngine.route()
    if (!local) {
        if (Cfg.baseUrl.isBlank()) return b.ai.App.self.getString(R.string.tx_695609ba)
        if (key.isBlank()) return b.ai.App.self.getString(R.string.tx_2603ac37)
    }
    val m = LlmClient(Cfg.baseUrl, key, Providers.byId(Cfg.providerId).auth, protocol = Cfg.modelProtocol)
    val msgs = JSONArray().put(JSONObject().put("role", "user").put("content", "ping，只回 ok 两个字符"))
    val t0 = SystemClock.elapsedRealtime()
    val msg = m.chat(Cfg.model, msgs, think = Cfg.think, thinkEffort = Cfg.thinkEffort)
    val cost = SystemClock.elapsedRealtime() - t0
    val body = msg.optString("content").ifBlank { msg.toString() }
    b.ai.App.self.getString(R.string.tx_c3af4af2, Cfg.model, cost, body.take(200))
}.getOrElse { b.ai.App.self.getString(R.string.tx_d8f20845, it.message?.take(300) ?: it.javaClass.simpleName) } //测试对话

/**
 * 接口协议自检：打印当前配置的协议与端点、各功能模型各自走的协议，
 * 再按该协议真发一条最小请求，验证 /responses 与 /chat/completions 两条通道都能解出正文。
 */
internal fun protocolDump(): String = runCatching {
    val proto = LlmClient.protocolOf(Cfg.modelProtocol)
    val path = if (proto == LlmClient.PROTO_RESPONSES) "/responses" else "/chat/completions"
    val sb = StringBuilder()
    sb.append("当前配置：").append(b.ai.core.data.profile.Profiles.selected?.label ?: "?")
        .append(" / ").append(Cfg.model.ifBlank { "(未填)" }).append("\n")
    sb.append("协议：").append(proto).append(" → POST {base}").append(path).append("\n")
    sb.append("base_url：").append(Cfg.baseUrl.ifBlank { "(空)" }).append("\n")
    //辅助任务（标题/总结/优化/记忆）各自跟着自己那条配置的协议走，这里一并打出来
    val fn = listOf(
        b.ai.core.model.chat.FnModel.TITLE,
        b.ai.core.model.chat.FnModel.SUMMARY,
        b.ai.core.model.chat.FnModel.PROMPT_OPT,
        b.ai.core.model.chat.FnModel.MEM
    ).joinToString(" ") { k ->
        k + "=" + LlmClient.protocolOf(b.ai.core.model.chat.FnModel.conn(k)[4])
    }
    sb.append("功能模型：").append(fn).append("\n")
    //真发一条：整包不走流式，只看端点通不通、能不能解出正文
    val msgs = JSONArray().put(JSONObject().put("role", "user").put("content", "ping，只回 ok 两个字符"))
    val t0 = SystemClock.elapsedRealtime()
    val msg = LlmClient(
        Cfg.baseUrl, Cfg.apiKey, Providers.byId(Cfg.providerId).auth,
        protocol = Cfg.modelProtocol
    ).chat(Cfg.model, msgs, think = false, tools = JSONArray())
    val cost = SystemClock.elapsedRealtime() - t0
    val body = msg.optString("content")
    sb.append("实测：").append(cost).append("ms ").append(body.take(80).ifBlank { "(空)" })
    val u = msg.optJSONObject("usage")
    if (u != null) {
        sb.append("\n用量：in=").append(u.optInt("prompt_tokens", 0))
            .append(" out=").append(u.optInt("completion_tokens", 0))
    }
    val tc = msg.optJSONArray("tool_calls")
    if (tc != null && tc.length() > 0) sb.append("\ntool_calls=").append(tc.length())
    sb.toString()
}.getOrElse { "协议自检失败：" + (it.message?.take(300) ?: it.javaClass.simpleName) } //接口协议自检

/** 工具权限：按当前策略分三类，模型一旦被判死就是这里配错了 */
internal fun toolPolicy(): String {
    val all = Tools.all
    val allow = ArrayList<String>()
    val ask = ArrayList<String>()
    val deny = ArrayList<String>()
    //统计口径与执行侧一致：走 policyEff，全局主控为 allow/deny 时全部归到同一类
    all.forEach { n ->
        when (Cfg.policyEff(n)) {
            "allow" -> allow.add(n)
            "deny" -> deny.add(n)
            else -> ask.add(n)
        }
    }
    val off = all.filter { !Tools.allowed(it) }
    val g = Cfg.policyAll
    val gText = when (g) {
        "allow" -> "允许（覆盖单工具）"
        "deny" -> "禁止（覆盖单工具）"
        else -> "询问（按单工具设置）"
    }
    return "全局主控：$gText\n共 ${all.size} 个工具\n放行 ${allow.size}：${allow.take(12).joinToString(", ")}\n" +
        "每次确认 ${ask.size}\n禁止 ${deny.size}：${deny.take(12).joinToString(", ")}\n" +
        "当前模式不可用 ${off.size}：${off.take(12).joinToString(", ")}"
} //工具权限

/** 三条通道各跑一条 id：哪条通、跑出来是谁，一眼看明白 */
internal fun testShell(): String {
    val sb = StringBuilder()
    val local = Shell.run("id", timeoutMs = 15_000)
    sb.append(b.ai.App.self.getString(R.string.tx_136b30f6, local.code) + local.out.trim().take(120) + "\n")
    val su = Root.run("id", timeoutMs = 20_000)
    sb.append("root code=${su.code}：" + su.out.trim().take(120) + "\n")
    val shiz = Shiz.run("id", timeoutMs = 20_000)
    sb.append("Shizuku code=${shiz.code}：" + shiz.out.trim().take(120))
    return sb.toString()
} //命令挂载

/** 设备与目录：报问题时这几个数最有用 */
internal fun deviceInfo(): String {
    val sb = StringBuilder()
    sb.append(b.ai.App.self.getString(R.string.tx_c83a29fe, android.os.Build.MANUFACTURER, android.os.Build.MODEL))
    sb.append(b.ai.App.self.getString(R.string.tx_70e28d57, android.os.Build.VERSION.RELEASE, android.os.Build.VERSION.SDK_INT))
    sb.append("ABI：${android.os.Build.SUPPORTED_ABIS.joinToString(", ")}\n")
    sb.append(b.ai.App.self.getString(R.string.tx_375ef055, App.self.filesDir.absolutePath))
    sb.append("数据根：${runCatching { Layout.dir(App.self, Layout.USER).absolutePath }.getOrElse { "?" }}\n")
    val df = File(App.self.filesDir, "").usableSpace / 1024 / 1024
    sb.append(b.ai.App.self.getString(R.string.tx_2a8058ac, df))
    sb.append("Linux：proot=${Linux.prootReady()} rootfs=${Linux.installed()}")
    return sb.toString()
} //设备信息

/**
 * 镜像清单：拉服务端 nodes.php，把文件元信息与节点列表逐条打出来。
 * 排查「装不上环境」先看这个：清单 404 / 字段缺失 / 节点为空都会体现在这里。
 */
internal fun testManifest(): String = buildString {
    appendLine("=== 镜像清单 ===")
    appendLine("接口：${Bai.api("/api/linux/nodes.php")}")
    val m = LinuxMirror.fetchManifest()
    if (m == null) {
        appendLine("拉取失败：${LinuxMirror.lastError.ifBlank { "接口不可达，或返回的不是合法 JSON" }}")
        appendLine(b.ai.App.self.getString(R.string.tx_d9366fc9, LinuxMirror.FALLBACK_NODES.size))
        return@buildString
    }
    appendLine(b.ai.App.self.getString(R.string.tx_b82d7fd4, m.meta.name, m.meta.size, m.meta.sha256.take(16)))
    appendLine(b.ai.App.self.getString(R.string.tx_82a30a52, LinuxMirror.fastTtfbMs))
    m.nodes.forEachIndexed { i, n ->
        appendLine("%d. %s (%s) %s".format(i + 1, n.name, n.id, n.url))
    }
} //镜像清单

/**
 * 镜像测速：并行探测全部节点，打印 TTFB / 速度 / 是否达标，并给出自动择优结果。
 * 手动节点（设置里选过的）单独标注一行，方便确认「手动优先」是否被正确识别。
 */
internal fun testMirror(): String = buildString {
    appendLine("=== 镜像测速 ===")
    val m = LinuxMirror.fetchManifest()
    val nodes = m?.nodes ?: LinuxMirror.FALLBACK_NODES
    appendLine("节点来源：${if (m != null) "服务端清单" else "内置兜底（${LinuxMirror.lastError}）"}，共 ${nodes.size} 个")
    val manual = Cfg.linuxMirrorNode
    appendLine("手动节点：${manual.ifBlank { "未指定（自动择优）" }}")
    val probes = LinuxMirror.probeAll(nodes)
    LinuxMirror.order(probes).forEachIndexed { i, p ->
        val head = "%d. %s (%s)".format(i + 1, p.node.name, p.node.id)
        if (!p.ok) appendLine(b.ai.App.self.getString(R.string.tx_7a43344e, p.err, p.node.name))
        else appendLine("$head → TTFB ${p.ttfbMs}ms / ${p.speedText()}${if (p.fast()) " 达标" else ""}")
    }
    val best = LinuxMirror.pick(probes)
    appendLine("择优：${best?.node?.name ?: "无可用节点"}")
} //镜像测速

/**
 * 更新包镜像：拉 /api/update/nodes.php，打印包元信息、节点清单、逐节点测速与择优结果。
 * 「更新弹窗里点直接下载没反应」先看这条：清单 404 / 节点为空 / 全不可用都能一眼看出。
 */
internal fun testUpdateMirror(): String = buildString {
    appendLine("=== 更新镜像 ===")
    appendLine(b.ai.App.self.getString(R.string.tx_9e349f3d, UpdateMirror.endpoint()))
    val m = UpdateMirror.fetchManifest()
    if (m == null) {
        appendLine("拉取失败：${UpdateMirror.lastError.ifBlank { "接口不可达，或返回的不是合法 JSON" }}")
        appendLine("此时更新弹窗会退回 get.php 下发的 downloadDirectUrl 直链")
        return@buildString
    }
    appendLine(b.ai.App.self.getString(R.string.tx_d1977545, m.meta.name, m.meta.size, m.meta.sha256.take(16)))
    appendLine(b.ai.App.self.getString(R.string.tx_82a30a52, UpdateMirror.fastTtfbMs))
    appendLine(b.ai.App.self.getString(R.string.tx_3ee9b024, m.nodes.size))
    val probes = UpdateMirror.probeAll(m.nodes)
    UpdateMirror.order(probes).forEachIndexed { i, p ->
        val head = "%d. %s (%s)".format(i + 1, p.node.name, p.node.id)
        if (!p.ok) appendLine(b.ai.App.self.getString(R.string.tx_7a43344e, p.err, p.node.name))
        else appendLine("$head → TTFB ${p.ttfbMs}ms / ${p.speedText()}${if (p.fast()) " 达标" else ""}")
    }
    val best = UpdateMirror.pick(probes)
    appendLine("择优：${best?.node?.name ?: "无可用节点"}")
} //更新镜像

/**
 * 一键卸载重装沙箱：先收掉终端会话，再走 install(force=true) 整棵替换 rootfs。
 * 与设置页「重装环境」是同一套逻辑，这里只是把日志流到测试页里。
 */
internal fun resetSandbox(append: (String) -> Unit): String {
    append("[卸载重装] 收掉现有终端会话…")
    runCatching { LinuxTerm.stop() }
    val ok = Linux.install(
        force = true,
        onStep = { c, t, s -> append("[$c/$t] $s") },
        onLog = { line -> append(line) }
    )
    if (!ok) return b.ai.App.self.getString(R.string.tx_a2830a88)
    val self = Linux.selfTest().out
    return "重装完成\n$self"
} //一键卸载重装

/**
 * 原生联网搜索注入自检：把当前开关与各厂商的注入结果逐条打出来。
 * 只拼请求体、不发网络请求，所以随时可跑。
 */
internal fun nativeSearchDump(): String {
    val sb = StringBuilder()
    val saved = Cfg.modelNativeSearch
    try {
        sb.appendLine("当前开关 model_native_search = $saved")
        sb.appendLine(b.ai.App.self.getString(R.string.tx_cb625f26, Cfg.providerId))
        sb.appendLine(b.ai.App.self.getString(R.string.tx_2408091c, NativeSearch.supported(Cfg.providerId, Cfg.baseUrl)))
        sb.appendLine("—— 逐厂商注入结果（已临时打开开关）——")
        Cfg.modelNativeSearch = true
        //内置厂商 + 两个典型自定义，看 providerId / base_url 双判定是否都命中
        val cases = Providers.builtin.filter { it.id != Providers.LOCAL }.map { it.id to it.baseUrl } +
            listOf("custom" to "https://api.deepseek.com/v1", "custom" to "https://example.com/v1")
        cases.forEach { (id, url) ->
            val body = JSONObject()
            NativeSearch.inject(body, id, url)
            val kind = NativeSearch.kindOf(id, url)
            sb.appendLine("$id @ $url")
            sb.appendLine("  kind=$kind body=${body.toString()}")
        }
    } finally {
        Cfg.modelNativeSearch = saved  //无论成败都还原，避免自检顺手改用户配置
    }
    return sb.toString()
} //原生联网搜索自检

/**
 * 工作区模式自检：打印开关、默认工作区、当前会话实际落点与判定结果。
 * 关模式必须所有会话都命中同一个 default 目录；开模式应各自一个哈希目录。
 */
internal fun workspaceDump(): String {
    val sb = StringBuilder()
    sb.appendLine("auto_bind_workspace = ${Cfg.autoBindWorkspace}")
    sb.appendLine("session_ws_manual = ${Cfg.sessionWsManual}")
    sb.appendLine("Cfg.defaultWorkspace = ${Cfg.defaultWorkspace.ifBlank { "(空)" }}")
    val def = Workspace.defaultWs()
    sb.appendLine("defaultWs() = $def")
    sb.appendLine(b.ai.App.self.getString(R.string.tx_e03b4047, File(def).isDirectory))
    val cur = Workspace.path()
    sb.appendLine("当前会话 path() = ${cur.ifBlank { "(空)" }}")
    sb.appendLine(b.ai.App.self.getString(R.string.tx_aab79711, Workspace.custom()))
    sb.appendLine("当前目录是否自动目录 = ${if (cur.isBlank()) "—" else Workspace.isAutoDir(cur)}")
    //关模式下把两个不同会话都算一次，验证确实共用同一目录（只读，不落库）
    if (!Cfg.autoBindWorkspace) {
        sb.appendLine("—— 关模式：两次 path() 应完全相同 ——")
        sb.appendLine("path()#1 = ${Workspace.path()}")
        sb.appendLine("path()#2 = ${Workspace.path()}")
    } else {
        sb.appendLine("—— 开模式：新会话应拿到新的哈希目录 ——")
        sb.appendLine("newSessionWorkspace() = ${Workspace.newSessionWorkspace()}")
    }
    return sb.toString()
} //工作区模式自检

/** dsh 自检：自举 Node → 安装/启动 dsh → 打印状态与日志尾部（阶段日志边跑边贴） */
internal fun dshSelfTest(append: (String) -> Unit): String = runCatching {
    kotlinx.coroutines.runBlocking {
        val sb = StringBuilder()
        sb.appendLine("=== dsh 自检 ===")

        //1. 启动（含 Node 自举 / dsh 安装 / 端口探测），阶段日志实时回贴
        val ok = b.ai.core.dsh.DshCore.start { append("[dsh] $it") }
        sb.appendLine("启动结果: $ok")

        //2. 状态
        sb.appendLine("状态: ${b.ai.core.dsh.DshCore.status()}")
        sb.appendLine("入口 URL: ${b.ai.core.dsh.DshCore.webUrl()}")

        //3. 日志尾部
        sb.appendLine("日志（最后 50 行）:")
        sb.appendLine(b.ai.core.dsh.DshCore.logs().takeLast(2000))

        //4. 提示：UI 需在 DshPage 的 WebView 里手动验证
        sb.appendLine("下一步: 点顶部「dsh 测试页」，在 WebView 里发一条消息验证流式")
        sb.toString()
    }
}.getOrElse { "dsh 自检失败: ${it.message}" }

/**
 * dsh SDK 直连自检（P0 验收）：起无界面的 dsh 运行时，发一条真实提问，
 * 把事件流拼出来，验证「我们的 App 能直接驱动 dsh 的插件/工具能力」。
 */
internal fun dshSdkSelfTest(append: (String) -> Unit): String = runCatching {
    kotlinx.coroutines.runBlocking {
        val sb = StringBuilder()
        //log：同时写结果缓冲与 UI 日志区，跑的过程中就能看到
        fun log(s: String) { sb.appendLine(s); append(s) }

        log("=== dsh SDK 自检 ===")
        //模型 id 用 dsh 内置 catalog（deepseek-flash / deepseek-v4-pro），配置里的 id 不在 catalog 会被拒
        val modelId = b.ai.core.dsh.DshSdk.MODEL_FALLBACK
        val key = b.ai.core.cfg.app.Cfg.apiKey
        log("provider=${b.ai.core.dsh.DshSdk.PROVIDER}")
        log("baseUrl=${b.ai.core.cfg.app.Cfg.baseUrl}")
        log("model=$modelId（dsh catalog：deepseek-flash / deepseek-v4-pro；App 配置里的是 ${b.ai.core.cfg.app.Cfg.model}）")
        log("apiKey=${if (key.isBlank()) "缺失（去设置里填）" else "已配置 ${key.length} 位"}")

        //1. 起运行时 + initialize（显式传 catalog 模型，避免配置里的 id 在官方端点 404）
        val ok = b.ai.core.dsh.DshSdk.start(modelId) { log("[sdk] $it") }
        log("initialize=$ok err=${b.ai.core.dsh.DshSdk.error()}")
        if (!ok) {
            log("提示：initialize 失败多半是 key 未生效，或模型名不被该路由接受")
            return@runBlocking sb.toString()
        }

        //2. 挂事件：只累积助手正文；普通提示词没有 query.completed，收工看 running→idle
        val text = StringBuilder()
        val done = kotlinx.coroutines.CompletableDeferred<Unit>()
        var sawRunning = false
        b.ai.core.dsh.DshSdk.onNotify = { m, p ->
            when (m) {
                "session.event" -> {
                    val ev = p.optJSONObject("event")
                    val type = ev?.optString("type").orEmpty()
                    val data = ev?.optJSONObject("data") ?: org.json.JSONObject()
                    when (type) {
                        //正文只在 step 收尾时落在 assistant/message：形状是 message.content 的 block 数组
                        //（attempt.stream 的增量 chunk 收尾时被 settle 收走，所以只回贴它做诊断）
                        "assistant/message" -> {
                            val msg = data.optJSONObject("message")
                            val content = msg?.optJSONArray("content") ?: org.json.JSONArray()
                            val one = StringBuilder()
                            for (i in 0 until content.length()) {
                                val b = content.optJSONObject(i) ?: continue
                                when (b.optString("type")) {
                                    //助手正文
                                    "text" -> one.append(b.optString("text"))
                                    //工具调用：字段名未取证，整包回贴一次拿真实形状（P1 渲染卡片按这个来）
                                    "tool-call" -> one.append("\n[工具块] ").append(b.toString().take(300))
                                }
                            }
                            val out = one.toString().trim()
                            text.append(if (text.isEmpty()) out else "\n$out")
                            append("[消息] ${out.take(400)}")
                            val usage = data.optJSONObject("usage")
                            if (usage != null) append("[用量] ${usage.toString().take(160)}")
                        }
                        "assistant/attempt" -> {
                            //增量 chunk 只做诊断：失败原因（如 HTTP_404 / 无 key）在这里能直接看到
                            val stream = data.optJSONArray("stream") ?: org.json.JSONArray()
                            for (i in 0 until stream.length()) {
                                val ch = stream.optJSONObject(i)?.optJSONObject("chunk") ?: continue
                                if (ch.optString("type") == "finish") {
                                    val reason = ch.optJSONObject("reason")
                                    if (reason?.optString("kind") == "error") {
                                        log("[错误] " + reason.optJSONObject("failure")?.optString("message").orEmpty())
                                    }
                                }
                            }
                        }
                        "step/start", "step/end", "turn/start", "turn/end" -> append("[turn] $type")
                        //工具调用（bash/read/write…）：P1 渲染成「工具卡片」
                        "tool/call", "tool/call/end", "tool/result" -> append("[tool] $type ${data.toString().take(260)}")
                        //其余事件名原样回贴，别自己编字段
                        else -> append("[event] $type")
                    }
                }
                "session.query.completed" -> {
                    append("[completed] reason=${p.optString("finishReason")} text=${p.optString("finalResponse").take(300)}")
                    if (!done.isCompleted) done.complete(Unit)
                }
                "session.status" -> {
                    val st = p.optString("status")
                    append("[status] $st")
                    if (st == "running") sawRunning = true
                    if (st == "idle" && sawRunning && !done.isCompleted) done.complete(Unit)
                }
                "approval/request" -> append("[approval] ${p.toString().take(200)}")
            }
        }

        //3. 发一条真实 query，等完成信号（带工具的一轮不止 90s，给 120s）
        //刻意要求走工具：只有真触发 tool-call，才能拿到该 block 的真实字段名（P1 画工具卡片靠它）
        b.ai.core.dsh.DshSdk.query("请用 bash 工具执行 echo baicodex-tool-ok，把命令的原始输出原样告诉我，不要自己推断结果") { log("[sdk] $it") }
        kotlinx.coroutines.withTimeoutOrNull(120_000L) { done.await() }
            ?: log("（120s 未收到完成信号，可能仍在跑或已失败）")

        log("—— 助手输出 ——")
        log(if (text.isBlank()) "（空）" else text.toString().take(1200))
        //stdout 的 dsh 人类日志单独一行，别再和真错误混在一起
        log("stdout 噪音: ${b.ai.core.dsh.DshSdk.noise().ifBlank { "无" }}")
        log("最终错误: ${b.ai.core.dsh.DshSdk.error().ifBlank { "无" }}")
        //4. 停掉测试内核（App 正常使用时会由 chat 复用）
        b.ai.core.dsh.DshSdk.stop()
        sb.toString()
    }
}.getOrElse { "dsh SDK 自检失败: ${it.message}" } //SDK自检

internal fun copyText(ctx: android.content.Context, text: String) {
    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        ?: return
    cm.setPrimaryClip(android.content.ClipData.newPlainText("bai-dev", text))
} //复制结果
/** 聊天记录检索自检：消息总数 + 用最近一条消息里的词做一次真实 SQL 检索 */
private suspend fun chatSearchDump(): String = withContext(Dispatchers.IO) {
    val sb = StringBuilder()
    val sessions = runCatching { b.ai.core.data.db.Repo.sessions() }.getOrDefault(emptyList())
    val total = sessions.sumOf { runCatching { b.ai.core.data.db.Repo.stats(it.id).first }.getOrDefault(0) }
    sb.append("会话 ").append(sessions.size).append(" 个，消息约 ").append(total).append(" 条\n")
    val last = sessions.firstOrNull()
    if (last == null) {
        sb.append("库里还没有会话，先去聊两句再测")
    } else {
        val msg = runCatching { b.ai.core.data.db.Repo.messages(last.id) }.getOrDefault(emptyList())
        val sample = msg.lastOrNull { it.content.length >= 2 }?.content?.take(4).orEmpty()
        sb.append("样本关键词：").append(if (sample.isBlank()) b.ai.App.self.getString(R.string.tx_e2c0dc29) else sample).append('\n')
        if (sample.isNotBlank()) {
            val hits = runCatching { b.ai.core.data.db.Repo.searchMessages(sample, 5) }.getOrDefault(emptyList())
            sb.append("命中 ").append(hits.size).append(" 条\n")
            hits.take(3).forEach { sb.append("- ").append(it.title.ifBlank { it.sessionId }).append(" · ").append(it.role).append('\n') }
        }
        sb.append("工具已注册：").append(b.ai.agent.tool.Tools.CHAT_SEARCH in b.ai.agent.tool.Tools.all)
    }
    sb.toString()
}

/** 技能 @ 自检：包数量 + 正则解析 + 整段删除 */
internal fun skillAtDump(): String {
    val sb = StringBuilder()
    val packs = b.ai.core.plugin.skill.SkillPacks.packs
    sb.append("技能包 ").append(packs.size).append(" 个：\n")
    packs.take(8).forEach { sb.append("- ").append(it.name.ifBlank { it.id }).append(if (it.on) "" else b.ai.App.self.getString(R.string.tx_ae577ece)).append('\n') }
    val demo = "帮我看下 @猪肉的技能 这个"
    val m = b.ai.ui.screen.chat.at.MENTION_RE.findAll(demo).map { it.value }.toList()
    sb.append("\n解析示例：").append(demo).append("\n→ ").append(m.joinToString(" / "))
    sb.append("\n整段删除：").append(b.ai.ui.screen.chat.at.atomicMentionDelete("帮我看下 @猪肉的技能 这个", "帮我看下 @猪肉的技 这个"))
    return sb.toString()
}

/** 记忆整理自检：条数 + 整理模型 + 是否就绪 */
internal fun memOrgDump(): String {
    val rows = b.ai.agent.memory.Memory.rows
    val conn = b.ai.core.model.chat.FnModel.conn(b.ai.core.model.chat.FnModel.MEM)
    val sb = StringBuilder()
    sb.append("记忆 ").append(rows.size).append(" 条\n")
    sb.append("整理模型：").append(conn[3].ifBlank { "未配置" }).append(" @ ").append(conn[0].ifBlank { "无 baseUrl" }).append('\n')
    sb.append("可用：").append(conn[0].isNotBlank() && conn[3].isNotBlank())
    return sb.toString()
}

/** STT 自检：当前引擎 + 关键配置齐不齐 */
internal fun sttDump(): String {
    val e = b.ai.core.model.voice.Audio.engine()
    val sb = StringBuilder("当前引擎：").append(e).append('\n')
    sb.append("系统识别可用：").append(android.speech.SpeechRecognizer.isRecognitionAvailable(b.ai.App.self)).append('\n')
    sb.append("OpenAI 兼容 baseUrl：").append(b.ai.core.cfg.app.Cfg.asrBase.ifBlank { "（空，回落主配置）" }).append('\n')
    sb.append("Deepgram Key：").append(if (b.ai.core.cfg.app.Cfg.asrDgKey.isBlank()) "未填" else b.ai.App.self.getString(R.string.tx_09a56bd3)).append('\n')
    sb.append("Sherpa 模型目录：").append(b.ai.core.cfg.app.Cfg.asrSherpaDir.ifBlank { "未选" }).append('\n')
    sb.append("提醒：Sherpa 需要内置运行时，当前包未包含")
    return sb.toString()
}
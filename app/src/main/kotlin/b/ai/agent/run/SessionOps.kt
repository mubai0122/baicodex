/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-24 02:20:00
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package b.ai.agent.run

import b.ai.core.cfg.app.Cfg
import b.ai.core.data.db.Repo
import b.ai.core.data.db.Sums
import b.ai.core.model.chat.ChatMsg
import b.ai.core.model.chat.Sum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 会话级运维：对「任意一条历史会话」手动压缩 / 全库记忆整理。
 *
 * 和 Agent 里那套压缩的区别：这里不经过当前运行通道（Run），也不碰 busy 锁，
 * 所以设置页对某条后台会话点一下就能跑，不需要先把它切到前台再切回来。
 * 压缩口径与前台一致：库里只写 [[upto:id]] 标记，正文进加密桶（Sums.put）。
 */
object SessionOps {
    /** 单次送进压缩器的字符上限 */
    private const val RENDER_MAX = 12_000

    /** 单个会话抽进记忆整理的字符上限 */
    private const val MEM_PER_SESSION = 6_000

    /** 记忆整理单次送进模型的字符上限（超出就分批） */
    private const val MEM_BATCH = 18_000

    /** 记忆整理最多扫多少批，防止会话特别多时一口气跑太久 */
    private const val MEM_BATCH_MAX = 12

    /** 一次记忆整理的结果：新会话 + 提炼出的条目 */
    data class Org(val sessionId: String, val title: String, val lines: List<String>, val batches: Int)

    // ---------------------------------------------------------------- 压缩

    /**
     * 把一条会话里「还没被摘要覆盖」的旧消息压成一条摘要。
     * 返回中文结果文案（成功/跳过/失败都走这里，界面直接 toast）。
     */
    suspend fun compact(sid: String): String = withContext(Dispatchers.IO) {
        val list = runCatching { Repo.messages(sid) }.getOrDefault(emptyList())
        if (list.isEmpty()) return@withContext "这个会话还没有内容"
        //上一条摘要覆盖到的消息 id：只有它之后的消息才是「未压缩」
        val bound = markerOf(list.lastOrNull { it.role == "summary" }?.content)
        val fresh = list.filter { it.role != "summary" && it.id > bound }
        if (fresh.size <= 2) return@withContext "只有 ${fresh.size} 条未压缩消息，不用压"
        //保留最近 ctxKeep 条不动，剩下的才进摘要
        var cut = fresh.size - Cfg.ctxKeep.coerceAtLeast(2)
        if (cut <= 0) return@withContext "未压缩消息不够（保留最近 ${Cfg.ctxKeep} 条）"
        //切点不能落在工具回包上：tool 消息必须跟着它的 assistant 一起进摘要
        while (cut < fresh.size && fresh[cut].role == "tool") cut++
        if (cut >= fresh.size) return@withContext "可压缩段全是工具回包，跳过"
        val old = fresh.subList(0, cut)
        //二次压缩要把上一次摘要带进去，否则最早那段历史直接被忘掉
        val prev = list.lastOrNull { it.role == "summary" }?.let { strip(it.content) }
        val render = render(old, prev)
        if (render.isBlank()) return@withContext "渲染不出可压缩的历史"
        val stat = stat(old)
        val input = if (stat.isBlank()) render else render + "\n\n" + stat
        val text = Sum.run(tpl(), input) ?: return@withContext "总结模型没返回内容，先检查「总结模型」配置"
        val marker = "[[upto:" + old.last().id + "]]"
        val saved = Sums.put(sid, marker, text)
        Repo.append(sid, ChatMsg(role = "summary", content = saved))
        "已压缩 ${old.size} 条消息（覆盖到第 ${old.last().id} 条）"
    } //压缩指定会话

    // ------------------------------------------------------------ 记忆整理

    /**
     * 全库记忆整理：新建一个会话，扫描所有会话（已压缩摘要 + 未压缩正文）提炼长期记忆，
     * 结果写进新会话，条目由调用方落进永久记忆表。
     * 返回 (结果, 文案)；结果为空表示失败或没有内容。
     */
    suspend fun organizeMemory(): Pair<Org?, String> = withContext(Dispatchers.IO) {
        val all = runCatching { Repo.sessions() }.getOrDefault(emptyList())
        if (all.isEmpty()) return@withContext null to "还没有任何会话"
        //逐个会话抽成一段：标题 + 已压缩摘要 + 正文（工具回包不进，只留人说的话）
        val parts = ArrayList<String>()
        all.forEach { s ->
            val msgs = runCatching { Repo.messages(s.id) }.getOrDefault(emptyList())
            if (msgs.isEmpty()) return@forEach
            val sb = StringBuilder("## ").append(s.title.ifBlank { "未命名会话" }).append('\n')
            msgs.forEach { m ->
                when (m.role) {
                    "summary" -> sb.append("【已压缩摘要】").append(strip(m.content)).append('\n')
                    "user" -> sb.append("用户: ").append(oneLine(m.content, 240)).append('\n')
                    "assistant" -> if (m.content.isNotBlank()) {
                        sb.append("助手: ").append(oneLine(m.content, 240)).append('\n')
                    }
                    else -> Unit //tool / 其它内部角色一律不进整理
                }
            }
            parts.add(sb.toString().take(MEM_PER_SESSION))
        } //逐个会话抽片段
        if (parts.isEmpty()) return@withContext null to "所有会话都是空的，没有可整理的记忆"
        //按字符上限分批，一次批太多模型记不住也容易超窗口
        val batches = ArrayList<String>()
        val cur = StringBuilder()
        parts.forEach { p ->
            if (cur.isNotEmpty() && cur.length + p.length > MEM_BATCH) {
                batches.add(cur.toString())
                cur.setLength(0)
            }
            cur.append(p).append('\n')
        }
        if (cur.isNotEmpty()) batches.add(cur.toString())
        val use = batches.take(MEM_BATCH_MAX)
        //逐批提炼，失败的批跳过（不因为一批失败就整单作废）
        val out = StringBuilder()
        var ok = 0
        use.forEach { b ->
            val r = Sum.run(MEM_PROMPT, b)
            if (!r.isNullOrBlank()) {
                out.append(r.trim()).append('\n')
                ok++
            }
        }
        if (out.isBlank()) return@withContext null to "整理失败：总结模型没有返回内容"
        //开一个新会话装整理结果：用户在会话列表里能直接看到这次整理干了什么
        val title = "记忆整理 " + SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date())
        val row = Repo.newSession(title, "默认")
        Repo.append(
            row.id,
            ChatMsg(
                role = "assistant",
                content = "扫描全部会话后整理出的长期记忆（共 ${use.size} 批，成功 $ok 批）：\n\n" + out.toString().trim()
            )
        )
        //把「- 条目」拆成纯文本行，去重与入库交给调用方
        val lines = out.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("- ") || it.startsWith("* ") || it.startsWith("• ") }
            .map { it.drop(2).trim() }
            .filter { it.length >= 4 }
            .distinct()
            .take(200)
            .toList()
        Org(row.id, title, lines, use.size) to "已整理 ${use.size} 批会话，新增 ${lines.size} 条候选记忆，结果在新会话「$title」"
    } //全库记忆整理

    // ---------------------------------------------------------------- 工具

    /** 摘要里的覆盖上界标记，取不到返回 0 */
    private fun markerOf(content: String?): Long {
        if (content.isNullOrBlank()) return 0L
        return Regex("""\[\[upto:(\d+)]]""").find(content)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    } //覆盖上界

    private fun strip(text: String): String = text.replace(Regex("""\[\[upto:\d+]]"""), "").trim()

    /** 单条消息压成一行，供摘要渲染 */
    private fun oneLine(text: String, cap: Int): String =
        text.replace("\n", " ").trim().let { if (it.length > cap) it.take(cap) + "…" else it }

    /** 一段历史渲染成压缩器输入：超长就逐级压低单条上限，别一刀切断后半段 */
    private fun render(list: List<ChatMsg>, prev: String?): String {
        val head = if (prev.isNullOrBlank()) "" else "此前摘要：$prev\n"
        for (cap in intArrayOf(600, 300, 120)) {
            val s = head + list.joinToString("\n") { oneLine(it.content, cap) }
            if (s.length <= RENDER_MAX) return s
        }
        return (head + list.joinToString("\n") { oneLine(it.content, 120) }).take(RENDER_MAX)
    } //渲染待压缩历史

    /** 本段对话的工具用量：由程序数好，模型只许照抄 */
    private fun stat(list: List<ChatMsg>): String {
        val calls = list.sumOf { it.toolCalls.size }
        if (calls <= 0) return ""
        val turns = list.count { it.toolCalls.isNotEmpty() }
        val cnt = LinkedHashMap<String, Int>()
        list.forEach { m -> m.toolCalls.forEach { c -> cnt[c.name] = (cnt[c.name] ?: 0) + 1 } }
        val top = cnt.entries.sortedByDescending { it.value }.take(6)
            .joinToString("、") { "${it.key} ${it.value}次" }
        return "【本次统计（程序给出，数字不许改）】工具调用 $turns 轮 / $calls 次；最常用：$top"
    } //工具用量统计

    /** 压缩提示词：内置四段结构 + 设置里的补充要求，最后替换变量（与前台压缩同一套口径） */
    private fun tpl(): String {
        val w = Cfg.summaryWords
        val extra = Cfg.summaryPrompt.trim()
        val base = if (extra.isEmpty()) PromptStore.summaryTpl else PromptStore.summaryTpl +
            "\n\n用户的补充要求（上面的段落结构与标题照旧保留，其余按这些要求来；与上面的写法冲突时以这里为准）：\n" + extra
        return base
            .replace("{level}", b.ai.core.model.chat.Sum.levelName(Cfg.summaryLevel))
            .replace("{words}", w.coerceAtLeast(0).toString())
            .replace("{limit}", if (w > 0) "${w}字以内" else "不限，按内容需要充分展开")
            .replace("{keep}", Cfg.ctxKeep.toString())
            .replace("{budget}", Cfg.ctxTokenBudget.toString())
    } //压缩提示词

    /** 记忆整理提示词：只要条目，不要解释——结果会进永久记忆表，每条都会每轮下发 */
    private val MEM_PROMPT: String = "你是一个长期记忆整理器。下面是从用户与助手的全部历史对话里抽出的片段（" +
        "含已压缩摘要）。请提炼真正值得长期记住的内容：用户的身份、偏好、习惯、明确要求、正在进行的项目、" +
        "环境与约定、长期有效的结论。\n" +
        "只输出条目，每行一条，以「- 」开头，最多 40 条，按重要程度排序。\n" +
        "禁止输出：与用户无关的通用道理、一次性的临时细节、对对话的复述、编号、解释、客套话。" +
        "没有值得记的就只回一行「- 无」"
} //会话级运维

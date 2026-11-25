/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-24 21:40:00
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package b.ai.agent.tool
import b.ai.device.fs.Workspace
import b.ai.device.shell.local.Linux
import b.ai.device.shell.local.Shell
import b.ai.device.fs.Diff
import b.ai.core.task.alarm.Schedules
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 补位工具：git 版本控制 / todo 跨轮任务清单 / time 时间换算 / cron 定时任务 / diff 文本对比。
 * 全部纯本地，不需要 root；git 优先用宿主 sh，没装 git 再退到内置 Ubuntu。
 */
object Extra {
    data class Res(val ok: Boolean, val text: String)

    // ---------- git ----------

    /**
     * git：action=status（默认）/ diff / log / commit / branch / add
     * path 是仓库目录（留空 = 当前工作区）；message 仅 commit 用；limit 控制 log 条数。
     */
    fun git(path: String, action: String, message: String, limit: Int): Res {
        val dir = if (path.isBlank()) runCatching { Workspace.path() }.getOrDefault("") else Workspace.resolve(path)
        if (dir.isBlank()) return Res(false, "git 需要一个仓库目录：传 path，或先绑定工作区")
        val repo = dir.trimEnd('/')
        val max = limit.coerceIn(1, 200)
        val cmd = when (action.trim().lowercase().ifEmpty { "status" }) {
            "status" -> "git -C '$repo' status --short --branch"
            "diff" -> "git -C '$repo' --no-pager diff --stat; echo '---'; git -C '$repo' --no-pager diff | head -n 400"
            "log" -> "git -C '$repo' --no-pager log --oneline -n $max"
            "branch" -> "git -C '$repo' branch -a -v"
            "add" -> "git -C '$repo' add -A && git -C '$repo' status --short"
            "commit" -> {
                if (message.isBlank()) return Res(false, "commit 需要 message：写清这次改了什么")
                val msg = message.replace("'", "'\\''")
                "git -C '$repo' add -A && git -C '$repo' commit -m '$msg'"
            }
            else -> return Res(false, "不认识的 action：$action（status / diff / log / branch / add / commit）")
        }
        //先试宿主 sh：多数设备装了 git；没有就退到内置 Ubuntu
        var r = Shell.run(cmd, timeoutMs = 120_000L)
        var tag = "沙箱"
        if (!r.ok && (r.out.contains("not found") || r.out.contains("inaccessible"))) {
            if (Linux.installed()) {
                r = Linux.run(cmd, timeoutMs = 120_000L)
                tag = "Ubuntu"
            } else {
                return Res(
                    false,
                    "这台设备没有 git，也没装内置 Ubuntu。二选一：在设置 → 扩展 里装 Ubuntu（含 git），" +
                        "或先抛开版本控制用 read_file / edit_file 直接改。"
                )
            }
        }
        val body = r.out.trim().ifEmpty { "（无输出）" }
        return Res(r.ok, "[$tag · git ${action.ifBlank { "status" }} · $repo] exit=${r.code}\n$body")
    } //git 只读与提交

    // ---------- todo ----------

    private fun todoFile(): File = File(b.ai.App.self.filesDir, "todo.json")

    private fun todoLoad(): JSONArray = runCatching {
        val f = todoFile()
        if (f.exists()) JSONArray(f.readText()) else JSONArray()
    }.getOrElse { JSONArray() }

    private fun todoSave(arr: JSONArray) {
        runCatching { todoFile().writeText(arr.toString()) }
    } //落盘

    /**
     * todo：跨轮持久任务清单。action=list（默认）/ add / done / undone / del / clear
     * 同一件事跨多轮时用它记着，避免每轮重新问用户「还剩哪些没做」。
     */
    fun todo(action: String, text: String, id: String): Res {
        val arr = todoLoad()
        val act = action.trim().lowercase().ifEmpty { "list" }
        val now = System.currentTimeMillis()
        when (act) {
            "list" -> {
                if (arr.length() == 0) return Res(true, "任务清单是空的。要记一条就 action=add 带 text。")
                val sb = StringBuilder("共 ${arr.length()} 条：\n")
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val done = o.optBoolean("done", false)
                    sb.append(if (done) "- [x] " else "- [ ] ").append(o.optString("id")).append("  ")
                        .append(o.optString("text"))
                    if (done) sb.append("（已完成）")
                    sb.append('\n')
                }
                return Res(true, sb.toString().trimEnd())
            }
            "add" -> {
                if (text.isBlank()) return Res(false, "add 需要 text：要记的事写一句话")
                val id = "t" + (arr.length() + 1) + "-" + (now % 100000)
                arr.put(
                    JSONObject().put("id", id).put("text", text.trim())
                        .put("done", false).put("at", now)
                )
                todoSave(arr)
                return Res(true, "已加入任务清单：$id  $text")
            }
            "done", "undone" -> {
                if (id.isBlank()) return Res(false, "$act 需要 id（先用 action=list 看）")
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    if (o.optString("id") != id) continue
                    o.put("done", act == "done")
                    todoSave(arr)
                    return Res(true, "已标记${if (act == "done") "完成" else "未完成"}：$id  ${o.optString("text")}")
                }
                return Res(false, "没有这个 id：$id")
            }
            "del" -> {
                if (id.isBlank()) return Res(false, "del 需要 id")
                val keep = JSONArray()
                var hit = ""
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    if (o.optString("id") == id) hit = o.optString("text") else keep.put(o)
                }
                if (hit.isEmpty()) return Res(false, "没有这个 id：$id")
                todoSave(keep)
                return Res(true, "已删除：$id  $hit")
            }
            "clear" -> {
                todoSave(JSONArray())
                return Res(true, "任务清单已清空")
            }
            else -> return Res(false, "不认识的 action：$action（list / add / done / undone / del / clear）")
        }
    } //跨轮任务清单

    // ---------- time ----------

    private val FMT_FULL = "yyyy-MM-dd HH:mm:ss"
    private val FMT_MIN = "yyyy-MM-dd HH:mm"

    /**
     * time：当前时间 / 时间戳换算 / 日期转时间戳。
     * action=now（默认）给本地与 UTC 时间、秒与毫秒时间戳、时区、星期；
     * action=parse 把日期或时间戳解释成完整时间；action=diff 算两个时间差多少。
     */
    fun time(action: String, value: String, to: String): Res {
        val act = action.trim().lowercase().ifEmpty { "now" }
        val now = System.currentTimeMillis()
        return when (act) {
            "now" -> {
                val c = Calendar.getInstance()
                val week = listOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")[c.get(Calendar.DAY_OF_WEEK) - 1]
                val zone = java.util.TimeZone.getDefault()
                Res(
                    true,
                    "现在：" + SimpleDateFormat(FMT_FULL, Locale.CHINA).format(Date(now)) + "（$week）\n" +
                        "UTC：" + utc(now) + "\n" +
                        "毫秒时间戳：$now\n" +
                        "秒时间戳：${now / 1000}\n" +
                        "时区：${zone.id}（UTC${offsetText(zone.getOffset(now))}）"
                )
            }
            "parse", "stamp" -> {
                val v = value.trim()
                if (v.isEmpty()) return Res(false, "parse 需要 value：日期（2026-01-01 08:30）或时间戳（13 位毫秒/10 位秒）")
                val ms = parseMs(v) ?: return Res(false, "看不懂这个时间：$v（支持 yyyy-MM-dd HH:mm、yyyy/MM/dd HH:mm、HH:mm、纯数字时间戳）")
                Res(
                    true,
                    "本地：" + SimpleDateFormat(FMT_FULL, Locale.CHINA).format(Date(ms)) + "\n" +
                        "UTC：" + utc(ms) + "\n" +
                        "毫秒时间戳：$ms\n" +
                        "秒时间戳：${ms / 1000}\n" +
                        "距今：" + ago(now, ms)
                )
            }
            "diff" -> {
                val a = parseMs(value.trim()) ?: return Res(false, "value 不是合法时间：$value")
                val b = parseMs(to.trim()) ?: return Res(false, "to 不是合法时间：$to")
                val d = b - a
                val abs = kotlin.math.abs(d)
                val days = abs / 86_400_000L
                val hours = (abs % 86_400_000L) / 3_600_000L
                val mins = (abs % 3_600_000L) / 60_000L
                Res(true, "从 ${fmtMin(a)} 到 ${fmtMin(b)}：${if (d < 0) "早" else "晚"} $days 天 $hours 小时 $mins 分（共 ${d / 1000} 秒）")
            }
            else -> Res(false, "不认识的 action：$action（now / parse / diff）")
        }
    } //时间换算

    private fun fmtMin(ms: Long): String = SimpleDateFormat(FMT_MIN, Locale.CHINA).format(Date(ms))

    private fun utc(ms: Long): String {
        val f = SimpleDateFormat(FMT_FULL, Locale.CHINA)
        f.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return f.format(Date(ms))
    } //UTC 时间

    private fun offsetText(raw: Int): String {
        val sign = if (raw < 0) "-" else "+"
        val m = kotlin.math.abs(raw) / 60_000
        return sign + (m / 60).toString().padStart(2, '0') + ":" + (m % 60).toString().padStart(2, '0')
    } //时区偏移

    /** 时间解析：纯数字按秒/毫秒时间戳，否则按几种常见格式解析（HH:mm 落到今天） */
    private fun parseMs(raw: String): Long? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        if (s.all { it.isDigit() }) {
            val n = s.toLongOrNull() ?: return null
            return if (s.length <= 10) n * 1000 else n
        }
        for (pat in listOf(FMT_FULL, FMT_MIN, "yyyy/MM/dd HH:mm", "yyyy-MM-dd", "HH:mm")) {
            val f = SimpleDateFormat(pat, Locale.CHINA)
            f.isLenient = false
            val d = runCatching { f.parse(s) }.getOrNull() ?: continue
            if (pat != "HH:mm") return d.time
            //只有时分：拼到今天的这个点
            val src = Calendar.getInstance().apply { time = d }
            val c = Calendar.getInstance()
            c.set(Calendar.HOUR_OF_DAY, src.get(Calendar.HOUR_OF_DAY))
            c.set(Calendar.MINUTE, src.get(Calendar.MINUTE))
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            return c.timeInMillis
        }
        return null
    } //时间解析

    private fun ago(now: Long, at: Long): String {
        val d = now - at
        val abs = kotlin.math.abs(d)
        val unit = when {
            abs < 60_000L -> "${abs / 1000} 秒"
            abs < 3_600_000L -> "${abs / 60_000} 分"
            abs < 86_400_000L -> "${abs / 3_600_000} 小时"
            else -> "${abs / 86_400_000} 天"
        }
        return if (d >= 0) "$unit 前" else "$unit 后"
    } //相对时间

    // ---------- cron ----------

    /**
     * cron：定时任务（到点让 Agent 跑一段 prompt）。
     * action=list（默认）/ add / del / on / off；at 支持 HH:mm 与 yyyy-MM-dd HH:mm；
     * auto=true 到点自动跑，false 只发通知提醒。
     */
    fun cron(action: String, prompt: String, at: String, daily: Boolean, auto: Boolean, id: String): Res {
        val act = action.trim().lowercase().ifEmpty { "list" }
        return when (act) {
            "list" -> {
                val l = Schedules.list
                if (l.isEmpty()) return Res(true, "还没有定时任务。action=add 带 prompt 与 at 建一条。")
                val f = SimpleDateFormat(FMT_MIN, Locale.CHINA)
                val sb = StringBuilder("共 ${l.size} 条定时任务：\n")
                l.forEach { t ->
                    sb.append("- ").append(t.id).append("  ")
                        .append(if (t.daily) "每天 " else "单次 ").append(f.format(Date(t.at)))
                    sb.append(if (t.on) " 开" else " 关")
                    sb.append(if (t.auto) "（自动执行）" else "（只提醒）")
                    sb.append("\n    ").append(t.prompt.replace("\n", " ").take(80)).append('\n')
                }
                Res(true, sb.toString().trimEnd())
            }
            "add" -> {
                if (prompt.isBlank()) return Res(false, "add 需要 prompt：到点要执行什么")
                val ms = parseMs(at)
                    ?: return Res(false, "at 不合法：$at（用 HH:mm 或 yyyy-MM-dd HH:mm）")
                val now = System.currentTimeMillis()
                val when0 = if (ms > now) ms else if (daily) Schedules.rollForward(ms, true, now) else 0L
                if (when0 <= 0L) return Res(false, "这个时间已经过去了：$at（要每天跑就加 daily=true）")
                val t = Schedules.add(prompt.trim(), when0, daily, auto)
                Res(
                    true,
                    "已建定时任务 ${t.id}：" + SimpleDateFormat(FMT_MIN, Locale.CHINA).format(Date(t.at)) +
                        if (daily) "（每天）" else "" 
                )
            }
            "del" -> {
                val t = Schedules.byId(id.trim()) ?: return Res(false, "没有这个任务：$id（先 action=list）")
                Schedules.remove(t.id)
                Res(true, "已删除定时任务 ${t.id}")
            }
            "on", "off" -> {
                val t = Schedules.byId(id.trim()) ?: return Res(false, "没有这个任务：$id（先 action=list）")
                Schedules.toggle(t.id, act == "on")
                Res(true, "任务 ${t.id} 已" + if (act == "on") "开启" else "关闭")
            }
            else -> Res(false, "不认识的 action：$action（list / add / del / on / off）")
        }
    } //定时任务

    // ---------- diff ----------

    /**
     * diff：两段文本或两个文件比差异。
     * 传 a / b 就是文本；传 file_a / file_b 就读文件内容再比。
     */
    fun diff(a: String, b: String, fileA: String, fileB: String): Res {
        val left = when {
            fileA.isNotBlank() -> readFile(fileA) ?: return Res(false, "读不到文件：$fileA")
            else -> a
        }
        val right = when {
            fileB.isNotBlank() -> readFile(fileB) ?: return Res(false, "读不到文件：$fileB")
            else -> b
        }
        if (left.isEmpty() && right.isEmpty()) return Res(false, "两边都是空的：给 a / b 文本，或 file_a / file_b 路径")
        val d = Diff.unified(left, right)
        if (d.isBlank()) return Res(true, "两边完全一致，没有差异")
        return Res(true, d)
    } //文本 / 文件比对

    private fun readFile(raw: String): String? = runCatching {
        File(Workspace.resolve(raw)).takeIf { it.isFile }?.readText()
    }.getOrNull()
} //补位工具集合

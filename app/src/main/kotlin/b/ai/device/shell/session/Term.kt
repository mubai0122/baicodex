/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:12
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package b.ai.device.shell.session

import b.ai.core.cfg.app.Cfg
import b.ai.core.model.chat.WorkMode
import b.ai.device.fs.Workspace
import b.ai.device.shell.local.Shell
import b.ai.device.shell.local.ShellResult
import b.ai.device.shell.root.Root
import b.ai.device.shell.root.Shiz
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.Writer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 终端会话池：一个会话 = 一个常驻 sh 进程，cd / export 在会话内保持，
 * 不同 session 名各跑各的、互不干扰。命令用「后台起 + wait」包一层，
 * 所以能拿到真实子进程 pid，Ctrl-C 能精确打断当前那条命令。
 */
object Term {
    /** 命令结束哨兵：回退出码与真实 cwd */
    private const val DONE = "__BAI_DONE__"

    /** 后台子进程 pid 回显：Ctrl-C 按它打 */
    private const val PID = "__BAI_PID__"

    /** 单个会话最多留多少行，超了从头部丢 */
    private const val MAX_LINES = 800

    /** 同时最多几个会话：每次会话都是一个真进程，别让它无限开 */
    const val MAX_SESS = 6

    private class Cmd(val token: Long) {
        val lines = ArrayList<String>()
        val done = CountDownLatch(1)

        @Volatile
        var code = -1

        @Volatile
        var pid = -1
    }

    private class Sess(
        val name: String,
        val tag: String,
        val proc: Process,
        val w: Writer,
        var cwd: String
    ) {
        val lock = Any()
        val backlog = ArrayList<String>()
        /** 滚动回显：所有输出行（含已被消费的）都留一份，供 term_view 随时查看「画面」 */
        val recent = ArrayList<String>()

        @Volatile
        var cur: Cmd? = null

        @Volatile
        var alive = true
        private var seq = 0L

        fun next(): Long = ++seq

        fun write(s: String) {
            w.write(s)
            w.flush()
        }

        fun close() {
            alive = false
            runCatching { proc.destroy() }
        }
    }

    private val pool = LinkedHashMap<String, Sess>()

    /** 现在开着哪些会话，给结果头展示用 */
    fun names(): List<String> = synchronized(pool) { pool.keys.toList() }

    /** 会话当前工作目录；会话不存在返回空 */
    fun cwdOf(name: String): String = synchronized(pool) { pool[name]?.cwd ?: "" }

    fun exec(cmd: String, path: String, session: String, timeoutMs: Long): ShellResult {
        val name = session.ifBlank { "main" }
        var s = synchronized(pool) { pool[name] }
        if (s != null && !s.alive) {
            synchronized(pool) { pool.remove(name) }
            s = null
        }
        val live = s ?: runCatching { sess(name) }.getOrNull()
        if (live == null) {
            // 通道不支持常驻 shell（su 进不去、Shizuku 没 newProcess）→ 退回一次性，别把调用卡死
            val r = once(cmd, path)
            return ShellResult(r.code, "[当前通道没有常驻会话，本条按一次性执行]\n" + r.out)
        }
        synchronized(live.lock) {
            if (path.isNotBlank()) live.write("cd " + q(path) + "\n")
            val t = live.next()
            val c = Cmd(t)
            live.cur = c
            val pre = synchronized(live.backlog) {
                val h = ArrayList(live.backlog)
                live.backlog.clear()
                h
            }
            val one = cmd.replace("\r", "")
            // 后台起 + wait：$! 就是这条命令的真实 pid，Ctrl-C 才有东西可打
            live.write("{\n" + one + "\n} &\n")
            live.write("echo \"" + PID + t + "__\$!\"\n")
            live.write("wait \$!\n")
            live.write("echo \"" + DONE + t + "__\$?|\$(pwd)\"\n")
            val finished = c.done.await(timeoutMs, TimeUnit.MILLISECONDS)
            live.cur = null
            val body = synchronized(c.lines) { ArrayList(c.lines) }.joinToString("\n").trim()
            val head = pre.joinToString("\n").trim()
            val text = when {
                head.isEmpty() -> body
                body.isEmpty() -> head
                else -> head + "\n" + body
            }
            if (!finished) {
                return ShellResult(
                    -1,
                    text + "\n[超时 ${timeoutMs}ms：命令还在会话「$name」里跑，会话保留；要停它用 keys=\"ctrl-c\"]"
                )
            }
            return ShellResult(c.code, text)
        }
    } //在会话里跑一条命令并等结果

    fun keys(keys: String, session: String): ShellResult {
        val name = session.ifBlank { "main" }
        val s = synchronized(pool) { pool[name] }
            ?: return ShellResult(-1, "会话「$name」不存在：先在同一个 session 里跑一条命令把它开起来")
        val act = keys.trim().lowercase().replace("+", "-")
        val pid = s.cur?.pid ?: -1
        return when (act) {
            "ctrl-c", "ctrlc", "c-c", "int", "interrupt" -> {
                if (pid <= 0) return ShellResult(-1, "会话「$name」现在没有正在跑的命令")
                val r = killPid(s.tag, pid)
                if (r.ok) ShellResult(0, "已向会话「$name」的前台命令（pid=$pid）发 SIGINT")
                else ShellResult(-1, "打断失败：" + r.out.ifBlank { "退出码 ${r.code}" })
            }

            "ctrl-d", "ctrld", "eof" -> {
                runCatching { s.write("\u0004") }
                ShellResult(0, "已向会话「$name」写 EOF")
            }

            "enter", "cr" -> {
                runCatching { s.write("\n") }
                ShellResult(0, "已向会话「$name」回车")
            }

            else -> ShellResult(
                -1,
                "键「$act」不支持：当前是非 PTY 会话，只有 ctrl-c / ctrl-d / enter 真正有效。" +
                    "Esc、Tab 补全、方向键要等真 PTY（plan S6.6）。"
            )
        }
    } //向会话发功能键

    /**
     * 查看会话最近的回显「画面」：命令卡住、看不到进度时先调它。
     * tail 控制返回最后多少行（默认 80）；会话不存在直接报错，提示先开会话。
     */
    fun view(session: String, tail: Int): ShellResult {
        val name = session.ifBlank { "main" }
        val s = synchronized(pool) { pool[name] }
            ?: return ShellResult(-1, "会话「$name」不存在：先在同一个 session 里跑过命令把它开起来")
        val n = tail.coerceIn(1, MAX_LINES)
        val snap = synchronized(s.recent) {
            when {
                s.recent.size <= n -> ArrayList(s.recent)
                else -> ArrayList(s.recent.subList(s.recent.size - n, s.recent.size))
            }
        }
        val state = if (s.alive) "存活" else "已退出"
        val head = "[会话 " + name + " · " + state + " · cwd=" + s.cwd.ifBlank { "?" } + "]\n"
        val body = if (snap.isEmpty()) "(暂无输出)" else snap.joinToString("\n")
        return ShellResult(0, head + body)
    } //查看终端内容

    /**
     * 向会话 stdin 写文本：回答 y/n、敲 REPL、给分页器发 q 都用它。
     * enter=true 时自动补一个回车。会话不存在会新开一个。
     * 注意：非 PTY 会话，写入的是 sh 的 stdin；命令若在后台子进程里自己读 stdin 未必生效，
     * 这种场景只能看 term_view 的回显 + 用 keys=ctrl-c 结束。
     */
    fun send(session: String, text: String, enter: Boolean): ShellResult {
        val name = session.ifBlank { "main" }
        var s = synchronized(pool) { pool[name] }
        if (s == null || !s.alive) {
            synchronized(pool) { pool.remove(name) }
            s = sess(name) ?: return ShellResult(-1, "开不了会话「$name」（当前通道不支持常驻 shell）")
        }
        val payload = if (enter) text + "\n" else text
        if (payload.isEmpty()) return ShellResult(-1, "要写入的内容为空")
        runCatching { s.write(payload) }.onFailure {
            return ShellResult(-1, "写入失败：" + (it.message ?: "会话可能已退出"))
        }
        return ShellResult(0, "已向会话「$name」写入：" + payload.replace("\n", "\\n"))
    } //向终端输入

    fun close(name: String): Boolean {
        val s = synchronized(pool) { pool.remove(name) } ?: return false
        s.close()
        return true
    } //关掉一个会话

    fun closeAll() {
        synchronized(pool) {
            pool.values.forEach { it.close() }
            pool.clear()
        }
    } //全部关掉

    private fun sess(name: String): Sess? {
        synchronized(pool) {
            pool[name]?.let { if (it.alive) return it }
            if (pool.size >= MAX_SESS) return null
        }
        val s = spawn(name) ?: return null
        synchronized(pool) { pool[name] = s }
        return s
    } //取会话，没有就开一个

    private fun spawn(name: String): Sess? {
        val dir = File(Workspace.path()).takeIf { it.isDirectory } ?: File("/sdcard")
        return runCatching {
            val tag: String
            val p: Process
            when (Cfg.workMode) {
                WorkMode.ROOT -> {
                    val su = Root.suPath() ?: return null
                    tag = "root"
                    p = ProcessBuilder(su).start()
                }

                WorkMode.SHIZUKU -> {
                    tag = "Shizuku"
                    p = Shiz.openShell() ?: return null
                }

                else -> {
                    tag = "沙箱"
                    p = ProcessBuilder("/system/bin/sh").start()
                }
            }
            Sess(name, tag, p, OutputStreamWriter(p.outputStream, Charsets.UTF_8), dir.absolutePath).also {
                pump(it)
            }
        }.getOrNull()
    } //开一个常驻 shell

    private fun pump(s: Sess) {
        fun one(ins: InputStream) {
            Thread {
                runCatching {
                    InputStreamReader(ins, Charsets.UTF_8).buffered().use { r ->
                        while (true) {
                            val line = r.readLine() ?: break
                            eat(s, line)
                        }
                    }
                }
                s.alive = false
            }.apply { isDaemon = true; name = "term-" + s.name }.start()
        }
        one(s.proc.inputStream)
        one(s.proc.errorStream)
    } //两路输出都收

    private fun eat(s: Sess, line: String) {
        val c = s.cur
        if (c != null) {
            val pidHead = PID + c.token + "__"
            if (line.startsWith(pidHead)) {
                c.pid = line.removePrefix(pidHead).trim().toIntOrNull() ?: -1
                return
            }
            val doneHead = DONE + c.token + "__"
            if (line.startsWith(doneHead)) {
                val body = line.removePrefix(doneHead)
                val bar = body.indexOf('|')
                c.code = body.take(if (bar < 0) body.length else bar).trim().toIntOrNull() ?: -1
                if (bar >= 0) body.substring(bar + 1).trim().let { if (it.isNotEmpty()) s.cwd = it }
                c.done.countDown()
                return
            }
            synchronized(c.lines) { if (c.lines.size < MAX_LINES) c.lines.add(line) }
            keep(s, line)
            return
        }
        synchronized(s.backlog) { if (s.backlog.size < MAX_LINES) s.backlog.add(line) }
        keep(s, line)
    } //一行输出归到当前命令

    /** 把输出行追加进滚动回显，超 MAX_LINES 从头部丢；sentinel 行不会走到这里 */
    private fun keep(s: Sess, line: String) {
        synchronized(s.recent) {
            if (s.recent.size >= MAX_LINES) s.recent.removeAt(0)
            s.recent.add(line)
        }
    } //记一份回显

    private fun killPid(tag: String, pid: Int): ShellResult = when (tag) {
        "root" -> Root.run("kill -INT $pid", 15_000L)
        "Shizuku" -> Shiz.run("kill -INT $pid", timeoutMs = 15_000L)
        else -> Shell.run("kill -INT $pid", timeoutMs = 15_000L)
    } //按会话所属通道打断，别的身份杀不动

    private fun once(cmd: String, path: String): ShellResult {
        val full = if (path.isBlank()) cmd else "cd " + q(path) + " && " + cmd
        return when (Cfg.workMode) {
            WorkMode.ROOT -> Root.run(full)
            WorkMode.SHIZUKU -> Shiz.run(full, timeoutMs = 120_000L)
            else -> Shell.run(full, timeoutMs = 120_000L)
        }
    } //一次性兜底：和原有 exec_shell 行为一致

    private fun q(p: String) = "'" + p.replace("'", "'\\''") + "'"
} //终端会话池
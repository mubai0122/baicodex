/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:11
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package b.ai.device.shell.local

import b.ai.core.cfg.app.Cfg
import b.ai.core.model.chat.WorkMode
import b.ai.device.shell.root.Root
import java.io.File
import java.io.OutputStreamWriter
import java.io.Writer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 内嵌 Linux 终端：一个常驻 proot bash 进程 + 行式输出缓冲。
 * 命令统一用「后台起 + wait」包一层，所以能拿到当前那条命令的真实 pid，
 * Ctrl-C 只打这一条、会话本身不丢（cwd / export 全程保留）。
 *
 * 与 [b.ai.device.shell.session.Term] 的区别：那个跑在宿主 sh 里，这个跑在 proot 的 Ubuntu 里。
 */
object LinuxTerm {
    /** 命令结束哨兵：回退出码 */
    private const val DONE = "__BXL_DONE__"

    /** 后台子进程 pid 回显：Ctrl-C 按它打 */
    private const val PID = "__BXL_PID__"

    /** 终端最多留多少行，超了从头部丢 */
    private const val MAX = 2000

    /** 一行输出 → UI 标脏回调（IO 线程上调用，UI 侧自己投递到主线程） */
    @Volatile
    var onDirty: (() -> Unit)? = null

    /** running / ready 变化回调 */
    @Volatile
    var onState: (() -> Unit)? = null

    /** 会话是否活着（proot 里的 bash 还在） */
    @Volatile
    var ready = false
        private set

    /** 是否有一条命令正在跑（决定「打断」能不能点） */
    @Volatile
    var running = false
        private set

    private val lock = Any()
    private val buf = ArrayList<String>()
    private var proc: Process? = null
    private var sink: Writer? = null
    private var cur = 0L
    private var curPid = -1
    private var latch: CountDownLatch? = null

    /** 会话是不是 su 起的：打断时得用同一个身份去 kill */
    private var viaSu = false

    /** 换行符与 ANSI 转义：apt / curl 的进度条会刷一堆控制字符，UI 里得洗掉 */
    private val ansi = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]")

    //region 输出缓冲

    /** 当前缓冲快照（拷贝，UI 可安全遍历） */
    fun lines(): List<String> = synchronized(lock) { ArrayList(buf) }

    /** 外部往终端灌一行 / 多行（装环境解包日志走这里） */
    fun append(text: String) {
        text.split('\n').forEach { if (it.isNotBlank()) add(it) }
    } //灌日志

    fun clear() {
        synchronized(lock) { buf.clear() }
        onDirty?.invoke()
    } //清屏

    private fun add(raw: String) {
        val line = ansi.replace(raw, "").replace("\r", "").trimEnd()
        synchronized(lock) {
            if (buf.size >= MAX) buf.removeAt(0)
            buf.add(line)
        }
        onDirty?.invoke()
    } //入缓冲 + 标脏

    //endregion

    //region 会话

    /** 开会话：已开直接返回；rootfs / proot 没备好就报错并留一行提示 */
    fun start(): Boolean {
        if (ready && proc?.isAlive == true) return true
        if (!Linux.installed()) {
            add("[还没装 Ubuntu 环境]先点上面的「安装环境」")
            return false
        }
        //env -i 清掉宿主环境，PATH / HOME / locale 全按 guest 的来，apt 才不会读到宿主代理变量
        val inner = listOf(
            "/usr/bin/env", "-i", "HOME=/root",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "LANG=C.UTF-8", "TERM=dumb", "DEBIAN_FRONTEND=noninteractive", "PS1=",
            Linux.guestShell(), "--noprofile", "--norc"
        )
        val launch = Linux.launch(inner) ?: run {
            add("[缺少 proot]APK 里的 libproot.so 没解压出来，重装一次 APK")
            return false
        }
        val p = spawn(launch) ?: run {
            add("[起不来]proot 进程启动失败：检查 Root / Shizuku 通路，或先点「安装环境」")
            return false
        }
        proc = p
        sink = OutputStreamWriter(p.outputStream, Charsets.UTF_8)
        ready = true
        running = false
        add("[Ubuntu 终端已就绪]输入命令回车执行；apt 提示 y/n 时直接在下面输入行里回")
        //这里不往 stdin 写任何东西：bai_apt 的定义与 set -m 跟着每条命令一起下发（见 submit），
        //避免会话刚起时的裸写入丢失导致 command not found
        pump(p)
        onState?.invoke()
        return true
    } //开会话

    /**
     * 起 proot 进程：Root 通道直接用 su 起（untrusted_app 域里 proot 基本起不来）；
     * 其它通道按 App 身份直起，起不来（CANNOT LINK / proot error）再退回 su。
     */
    private fun spawn(launch: Linux.Launch): Process? {
        //Root 通道优先直接用 su：untrusted_app 域下 proot 基本起不来（ptrace / exec 限制，实测一进就退），
        //先试本地只会多花 1.2s 并刷一行无意义的失败提示
        if (Cfg.workMode == WorkMode.ROOT) {
            Root.suPath()?.let { su ->
                runCatching {
                    viaSu = true
                    return ProcessBuilder(su, "-c", launch.cmdline).redirectErrorStream(true).start()
                }
            }
        }
        val local = runCatching {
            ProcessBuilder(launch.argv).redirectErrorStream(true).apply {
                environment().putAll(launch.env)
            }.start()
        }.getOrNull()
        if (local != null) {
            //1.2s 内就退出 = 没起来；还活着说明 bash 已经在等了
            val dead = runCatching { local.waitFor(1200, TimeUnit.MILLISECONDS) }.getOrDefault(true)
            if (!dead) {
                viaSu = false
                return local
            }
            add("[App 身份起不来 proot]改走 Root 通道：" + localError(local))
        }
        if (Cfg.workMode == WorkMode.ROOT) return null
        val su = Root.suPath() ?: return null
        return runCatching {
            viaSu = true
            ProcessBuilder(su, "-c", launch.cmdline).redirectErrorStream(true).start()
        }.getOrNull()
    } //起进程

    /** 读一眼本地起不来时的报错首行（CANNOT LINK / proot error / 权限），终端里直接给用户看 */
    private fun localError(p: Process): String = runCatching {
        p.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            .lineSequence().map { it.trim() }.firstOrNull { it.isNotBlank() } ?: "无输出"
    }.getOrDefault("无输出")

    /** 关会话：整套 proot 进程树一起收掉 */
    fun stop() {
        ready = false
        running = false
        runCatching { sink?.close() }
        runCatching { proc?.destroy() }
        proc = null
        sink = null
        latch?.countDown()
        latch = null
        add("[会话已关闭]")
        onState?.invoke()
    } //关会话

    private fun pump(p: Process) {
        Thread {
            runCatching {
                p.inputStream.bufferedReader(Charsets.UTF_8).use { r ->
                    while (true) {
                        val line = r.readLine() ?: break
                        eat(line)
                    }
                }
            }
            //输出流断了 = 会话没了
            if (proc === p) {
                ready = false
                running = false
                latch?.countDown()
                add("[会话已退出]")
                onState?.invoke()
            }
        }.apply { isDaemon = true; name = "lterm" }.start()
    } //收输出

    private fun eat(line: String) {
        val pidHead = PID + cur + "__"
        if (line.startsWith(pidHead)) {
            curPid = line.removePrefix(pidHead).trim().toIntOrNull() ?: -1
            return
        }
        val doneHead = DONE + cur + "__"
        if (line.startsWith(doneHead)) {
            val code = line.removePrefix(doneHead).trim().toIntOrNull() ?: -1
            //退出码是 shell 内部约定，用户不需要看：0 一律说「完成」，非 0 才报错并带上码
            add(if (code == 0) "[完成]" else "[出错，退出码 $code]")
            running = false
            latch?.countDown()
            onState?.invoke()
            return
        }
        add(line)
    } //一行输出归位

    //endregion

    //region 执行 / 输入

    /** 跑一条命令（异步，输出直接进缓冲）。已有命令在跑就不排队，提示先打断 */
    fun submit(cmd: String): Boolean {
        val one = cmd.trim().replace("\r", "")
        if (one.isEmpty()) return false
        if (!start()) return false
        val w = sink ?: return false
        if (running) {
            add("[上一条命令还在跑]先点「打断」再执行")
            return false
        }
        val t = System.currentTimeMillis()
        cur = t
        curPid = -1
        latch = CountDownLatch(1)
        running = true
        add("root@bai:~# $one")
        onState?.invoke()
        return runCatching {
            //每条命令都带一遍 set -m 与 bai_apt 定义：幂等，且不依赖「第一条写进去了」这种假设
            //（实测过会话刚起时的裸写入会丢，导致后面全是 bai_apt: command not found）
            w.write("set -m\n")
            w.write(HELPER)
            w.write("\n")
            //后台起 + wait：$! 就是这条命令的真实 pid，Ctrl-C 才有东西可打
            w.write("{\n" + one + "\n} &\n")
            w.write("echo \"" + PID + t + "__\$!\"\n")
            w.write("wait \$!\n")
            w.write("echo \"" + DONE + t + "__\$?\"\n")
            w.flush()
            true
        }.getOrElse {
            running = false
            add("[写入失败]" + (it.message ?: "会话可能已退出"))
            onState?.invoke()
            false
        }
    } //跑命令

    /**
     * 打断当前命令：bash 开了 set -m，命令自成进程组，按 -pid 整组打。
     * 先试 INT（让 apt 这种程序自己收尾），600ms 还没死就 TERM，再不行 KILL。
     * 打断放在后台线程，UI 点完立刻返回。
     */
    fun interrupt(): Boolean {
        if (!running) {
            add("[当前没有正在跑的命令]")
            return false
        }
        val pid = curPid
        if (pid <= 0) {
            add("[取不到 pid]直接重开会话")
            stop()
            start()
            return true
        }
        Thread {
            add("[打断 pid=$pid]")
            killGroup("INT", pid)
            Thread.sleep(600)
            if (alive(pid)) killGroup("TERM", pid)
            Thread.sleep(400)
            if (alive(pid)) killGroup("KILL", pid)
        }.apply { isDaemon = true; name = "lterm-kill" }.start()
        return true
    } //打断

    /** 进程组还在不在：/proc 里有就是活的 */
    private fun alive(pid: Int): Boolean = File("/proc/$pid").exists()

    /**
     * 打信号：-pid 表示整组。App 身份起的会话用 toybox kill，
     * su 起的会话必须走 Root，否则打不动 root 的进程。
     */
    private fun killGroup(sig: String, pid: Int) = runCatching {
        val cmd = "kill -$sig -$pid 2>/dev/null || kill -$sig $pid 2>/dev/null"
        if (viaSu) Root.run(cmd, 15_000L)
        else ProcessBuilder("/system/bin/sh", "-c", cmd).redirectErrorStream(true).start()
            .also { it.waitFor(15, TimeUnit.SECONDS) }
        Unit
    }.getOrDefault(Unit)

    /** 功能键 / 裸字符直接写 stdin：给 REPL、分页器这类读 stdin 的场景用 */
    fun key(k: String): Boolean {
        if (k == "ctrl-c") return interrupt()
        if (!start()) return false
        val w = sink ?: return false
        val seq = when (k) {
            "ctrl-d" -> "\u0004"
            "tab" -> "\t"
            "up" -> "\u001B[A"
            "down" -> "\u001B[B"
            "esc" -> "\u001B"
            "enter" -> "\n"
            else -> return false
        }
        return runCatching {
            w.write(seq)
            w.flush()
            true
        }.getOrDefault(false)
    } //功能键

    /** 往当前命令的 stdin 写一行（apt 的 y/n、python 的输入都用它） */
    fun input(text: String, enter: Boolean = true): Boolean {
        if (!start()) return false
        val w = sink ?: return false
        return runCatching {
            w.write(if (enter) text + "\n" else text)
            w.flush()
            add("> $text")
            true
        }.getOrDefault(false)
    } //写 stdin

    //endregion

    //region 环境与开发工具

    /** 装 / 重装 Ubuntu 环境：解包日志实时灌进终端，跑在后台线程，不卡 UI */
    fun installEnv(force: Boolean = false, done: (Boolean) -> Unit = {}) {
        Thread {
            //先收掉现有会话：重装要整棵替换 rootfs，别让还跑着的 proot 占着它
            if (ready) stop()
            add("[开始安装 Ubuntu 环境]")
            running = true
            onState?.invoke()
            val ok = Linux.install(
                force = force,
                onStep = { c, t, s -> add("[$c/$t] $s") }
            ) { line -> add(line) }
            running = false
            //rootfs 换了，旧会话作废
            ready = false
            runCatching { proc?.destroy() }
            proc = null
            sink = null
            add(if (ok) "[环境已就绪]可以开终端装开发工具了" else "[安装失败]看上面日志，可再点一次")
            onState?.invoke()
            done(ok)
        }.apply { isDaemon = true; name = "lterm-env" }.start()
    } //装环境

    /** 一个可一键安装的开发环境 */
    class Tool(
        val id: String,
        val name: String,
        val hint: String,
        /** 判定是否已安装：rootfs 里任意一个探针路径存在即算装了 */
        val probes: List<String>,
        val cmd: String
    ) {
        /** 产物是否已落盘：每次现查，装完立刻能反映出来 */
        fun present(): Boolean = probes.any { File(Linux.rootfs, it).exists() }
    } //开发环境描述

    /**
     * 会话启动时注入的 shell 辅助函数。
     * 镜像解包后 /var/lib/apt/lists 是空的，直接 install 只会得到
     * "Unable to locate package" / "has no installation candidate"，所以：
     * 1) 没有 stamp 就说明索引没备过 → 先 apt-get update；
     * 2) install 失败 → 清 stamp、重刷索引、再试一次（覆盖索引过期 / 半截下载）。
     */
    private val HELPER = """
# bai_apt：装包统一入口，索引没备好会自己 update，装不上会自愈重刷一次
bai_apt() {
  export DEBIAN_FRONTEND=noninteractive
  if [ ! -f /etc/apt/apt.conf.d/99bai ]; then
    cat > /etc/apt/apt.conf.d/99bai <<'EOB'
Acquire::ForceIPv4 "true";
Acquire::http::Timeout "20";
Acquire::https::Timeout "20";
Acquire::Retries "1";
EOB
  fi
  if [ ! -f /var/lib/apt/.bai_stamp ]; then
    echo "[更新 apt 索引（首次约 15-60 秒，能看见进度）…]"
    apt-get update -y && touch /var/lib/apt/.bai_stamp
  fi
  apt-get install -y --no-install-recommends ${'$'}@ && return 0
  echo "[安装失败：重刷索引后再试一次]"
  rm -f /var/lib/apt/.bai_stamp
  apt-get update -y && touch /var/lib/apt/.bai_stamp
  apt-get install -y --no-install-recommends ${'$'}@
}
""".trimIndent()

    /** 常用开发环境清单：装什么、装完怎么判，都写在一处 */
    val TOOLS = listOf(
        Tool(
            "python", "Python3", "python3 / pip / venv", listOf("usr/bin/python3"),
            "bai_apt python3 python3-pip python3-venv"
        ),
        Tool(
            "node", "Node.js", "node / npm", listOf("usr/bin/node"),
            "bai_apt nodejs npm"
        ),
        Tool(
            "pnpm", "pnpm", "npm i -g pnpm", listOf("usr/local/bin/pnpm", "usr/bin/pnpm"),
            "bai_apt nodejs npm && npm install -g pnpm"
        ),
        Tool(
            "go", "Go", "golang-go", listOf("usr/bin/go", "usr/lib/go/bin/go"),
            "bai_apt golang-go"
        ),
        Tool(
            "rust", "Rust", "rustc / cargo", listOf("usr/bin/rustc"),
            "bai_apt rustc cargo"
        ),
        Tool(
            "git", "Git", "版本管理", listOf("usr/bin/git"),
            "bai_apt git"
        ),
        Tool(
            "cc", "GCC / Make", "编译链", listOf("usr/bin/gcc"),
            "bai_apt build-essential cmake make pkg-config"
        ),
        Tool(
            "java", "Java (JDK17)", "javac / java -version", listOf("usr/lib/jvm"),
            "bai_apt openjdk-17-jdk-headless"
        ),
        Tool(
            "ssl", "OpenSSL", "openssl / libssl-dev", listOf("usr/bin/openssl"),
            "bai_apt openssl libssl-dev"
        ),
        Tool(
            "ndk", "Android NDK", "下载约 600MB → /opt/android-ndk", listOf("opt/android-ndk/build/cmake/android.toolchain.cmake"),
            //NDK 不在 apt 源里，直接取 Google 官方 zip；先备齐 curl/unzip 再下
            "bai_apt curl unzip && mkdir -p /opt && " +
                "curl -fL --progress-bar -o /tmp/ndk.zip https://dl.google.com/android/repository/android-ndk-r26d-linux.zip && " +
                "rm -rf /opt/android-ndk /opt/android-ndk-r26d && unzip -q /tmp/ndk.zip -d /opt && " +
                "mv /opt/android-ndk-r26d /opt/android-ndk && rm -f /tmp/ndk.zip && " +
                "echo '[NDK 就绪] /opt/android-ndk/build/cmake/android.toolchain.cmake'"
        ),
        Tool(
            "net", "curl / unzip", "下载与解压", listOf("usr/bin/curl"),
            "bai_apt curl wget ca-certificates unzip zip xz-utils"
        )
    )

    /** 一键拓展环境：一把装上常用的一套（索引缺失 / 过期由 bai_apt 自己处理，已装的会自动跳过） */
    const val BASE_ENV =
        "bai_apt git curl wget unzip zip xz-utils ca-certificates ncurses-bin procps openssl " +
            "build-essential cmake make pkg-config " +
            "python3 python3-pip python3-venv nodejs npm golang-go rustc cargo vim nano tmux"

    /** 强制重刷 apt 索引：装包报 Package not found 时手动跑它 */
    const val APT_UPDATE = "rm -f /var/lib/apt/.bai_stamp; apt-get update -y && touch /var/lib/apt/.bai_stamp"

    //endregion
} //内嵌 Linux 终端
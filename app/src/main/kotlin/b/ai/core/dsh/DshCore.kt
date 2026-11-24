package b.ai.core.dsh

import b.ai.core.data.log.Logs
import b.ai.device.shell.local.Linux
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * DshCore.kt — dsh（DeepSeek Harness）内核启动与生命周期管理（阶段 0）
 *
 * 职责：
 * 1. 自举 Node.js 24（沙箱自带的 v18 不满足 dsh 的 engines 要求，官方 arm64 包解到 /opt/node24）
 * 2. 在 proot Ubuntu 内安装/启动 @deepseek-ai/dsh web（127.0.0.1:3080）
 * 3. 端口就绪探测（宿主侧 Socket 直连，不依赖 curl）、RSS 采样
 *
 * 说明：
 * - 所有容器内命令统一走 sh() 前缀，保证 PATH 里 /opt/node24/bin 优先
 * - 国内环境走 npmmirror，避免官方源长时间卡住
 * - 本文件不含 UI；WebView 页面由 DshPage 加载 http://127.0.0.1:3080，仅开发者模式可见
 */
object DshCore {
    private const val PORT = 3080
    private const val LOG = "/tmp/dsh-web.log"
    private const val DSH_BIN = "dsh"
    /** 日志仓 tag：排查时在 logs 里搜 dsh 即可 */
    private const val TAG = "dsh"

    /** Node 自举目录与下载源 */
    private const val NODE_DIR = "/opt/node24"
    private const val NODE_BASE = "https://registry.npmmirror.com/-/binary/node/latest-v24.x"
    private const val NPM_REG = "https://registry.npmmirror.com"

    /** dsh 要求的最小 Node 版本 */
    private const val MIN_MAJOR = 24
    private const val MIN_MINOR = 9

    /** 容器内命令统一前缀：PATH 前置 /opt/node24/bin，其余沿用系统默认 */
    private const val PREFIX = "export PATH=$NODE_DIR/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin;"

    @Volatile private var started = false
    @Volatile private var lastError = ""
    @Volatile private var startTimeMs = 0L

    /** dsh web 启动后打印的带 token 的完整 URL（认证入口），解析一次后缓存 */
    @Volatile private var cachedUrl = ""

    /** 去掉 ANSI 颜色码，日志里 URL 常被颜色包裹 */
    private val ANSI = Regex("\u001b\\[[0-9;]*[A-Za-z]")

    /**
     * 取 dsh web 的入口 URL：优先用日志里打印的带 token 地址（首次必须用它才能过认证），
     * 日志里没有就退回裸地址 http://127.0.0.1:3080。
     */
    suspend fun webUrl(): String = withContext(Dispatchers.IO) {
        if (cachedUrl.isNotBlank()) return@withContext cachedUrl
        val raw = sh("grep -oE 'http://[^ ]*:[0-9][0-9]*[^ ]*' $LOG 2>/dev/null | tail -1", 15_000L)
        val clean = ANSI.replace(raw, "").trim().lines().lastOrNull().orEmpty()
        cachedUrl = if (clean.startsWith("http")) clean else "http://127.0.0.1:$PORT"
        cachedUrl
    } //入口 URL

    /** 端口是否可连：宿主侧 TCP 探测，换掉「容器里跑 curl」这种依赖外部命令的判据 */
    private fun portOpen(port: Int = PORT, timeoutMs: Int = 400): Boolean = try {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), timeoutMs) }
        true
    } catch (e: Exception) {
        false
    } //端口探测

    /** 容器内执行命令，统一带 PATH 前缀；命令与输出尾巴一并进日志仓 */
    private suspend fun sh(cmd: String, timeoutMs: Long): String {
        val r = Linux.run("$PREFIX $cmd", timeoutMs)
        //日志仓只留首行命令与输出尾部：既够定位，也不会把一行撑爆
        Logs.d(
            TAG,
            "sh[${timeoutMs}ms] ${cmd.trim().lineSequence().first().take(160)} -> code=${r.code} " +
                r.out.trim().lines().takeLast(5).joinToString(" | ").take(500)
        )
        return r.out
    } //容器命令

    /** 解析 vX.Y.Z 并判 >= 24.9 */
    private fun verOk(out: String): Boolean {
        val m = Regex("v(\\d+)\\.(\\d+)").find(out) ?: return false
        val maj = m.groupValues[1].toIntOrNull() ?: return false
        val min = m.groupValues[2].toIntOrNull() ?: 0
        return maj > MIN_MAJOR || (maj == MIN_MAJOR && min >= MIN_MINOR)
    } //版本判断

    /**
     * 保证容器内有 >= 24.9 的 Node：不满足则从 npmmirror 下官方 arm64 包解到 /opt/node24。
     * 返回 null 表示就绪，否则返回错误描述。
     */
    private suspend fun ensureNode(log: (String) -> Unit): String? {
        val cur = sh("node -v 2>/dev/null || echo not_found", 30_000L).trim()
        if (verOk(cur)) {
            log("Node 就绪：$cur")
            return null
        }
        log("当前 Node：$cur（需 >= $MIN_MAJOR.$MIN_MINOR），开始自举 Node 24…")

        //依赖与下载：一次脚本内完成，避免多次起 proot 丢状态
        val out = sh(
            """
            set -u
            command -v curl >/dev/null 2>&1 || {
              apt-get update -qq >/dev/null 2>&1
              apt-get install -y -qq curl ca-certificates >/dev/null 2>&1
            }
            command -v xz >/dev/null 2>&1 || apt-get install -y -qq xz-utils >/dev/null 2>&1
            command -v curl >/dev/null 2>&1 || { echo "ERR:nocurl"; exit 11; }
            mkdir -p /tmp/ndl $NODE_DIR || { echo "ERR:mkdir"; exit 12; }
            F=$(curl -fsSL --max-time 60 $NODE_BASE/SHASUMS256.txt | grep -o 'node-v[0-9.]*-linux-arm64.tar.xz' | head -1)
            [ -n "${'$'}F" ] || { echo "ERR:nolist"; exit 13; }
            echo "GET ${'$'}F"
            curl -fsSL --max-time 900 -o /tmp/ndl/${'$'}F $NODE_BASE/${'$'}F || { echo "ERR:download"; exit 14; }
            tar -xJf /tmp/ndl/${'$'}F -C $NODE_DIR --strip-components=1 || { echo "ERR:untar"; exit 15; }
            ln -sf $NODE_DIR/bin/node /usr/local/bin/node
            ln -sf $NODE_DIR/bin/npm  /usr/local/bin/npm
            ln -sf $NODE_DIR/bin/npx  /usr/local/bin/npx
            echo "OK $($NODE_DIR/bin/node -v)"
            """.trimIndent(),
            1_200_000L
        )
        log(out.trim().lines().takeLast(6).joinToString("\n"))
        if (!out.contains("OK v")) return "Node 自举失败：${out.trim().takeLast(200)}"
        return null
    } //自举 Node

    /**
     * 补「系统文档目录」这一环：dsh 首次启动会调 xdg-user-dir DOCUMENTS 去定位
     * `<Documents>/deepseek-harness/default-workspace` 并 mkdir；我们这套精简 rootfs 没装
     * xdg-user-dirs，取不到就是「无法创建默认工作区」。
     * 这里放一个只回答 XDG 目录的极简替身（不依赖网络），并预建目录。
     */
    private suspend fun ensureDocumentsDir(): Boolean {
        val out = sh(
            """
            set -u
            if [ ! -x /usr/local/bin/xdg-user-dir ]; then
              printf '%s\n' '#!/bin/sh' \
                '# dsh 默认工作区解析用的极简替身：只回答 XDG 用户目录' \
                'case "${'$'}1" in' \
                '  DOCUMENTS) echo "${'$'}{XDG_DOCUMENTS_DIR:-${'$'}HOME/Documents}" ;;' \
                '  *) echo "${'$'}HOME" ;;' \
                'esac' > /usr/local/bin/xdg-user-dir
              chmod 755 /usr/local/bin/xdg-user-dir
            fi
            mkdir -p "${'$'}HOME/Documents/deepseek-harness" && echo OK_DOCS
            """.trimIndent(),
            60_000L
        )
        return out.contains("OK_DOCS")
    } //文档目录与 xdg 替身

    /**
     * 启动 dsh：proot → Node 自举 → 安装 dsh（缺失时）→ 后台起 web 服务 → 等端口就绪。
     * @param onLog 阶段日志回调（UI 侧边跑边刷）
     */
    suspend fun start(onLog: (String) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        //阶段日志双写：界面状态行 + 日志仓（/sdcard/baicode/logs，按天加密落盘）
        fun log(s: String) {
            Logs.i(TAG, s)
            onLog(s)
        }
        Logs.i(TAG, "start() 进入，workMode=${b.ai.core.cfg.app.Cfg.workMode.id}，port=$PORT")
        if (started) return@withContext true
        startTimeMs = System.currentTimeMillis()

        //1. proot 环境
        if (!Linux.installed()) {
            lastError = "Ubuntu 环境未安装：设置 → 扩展 → Linux 环境 里先装"
            return@withContext false
        }
        log("Ubuntu 就绪")

        //1.5 文档目录 + xdg-user-dir 替身：dsh 首次建默认工作区要用（放在端口复用判断之前，复用旧进程也能补齐）
        if (ensureDocumentsDir()) log("工作区目录就绪：~/Documents/deepseek-harness")
        else log("警告：文档目录准备失败，可能影响首次工作区创建")

        //2. 已有服务在跑（上次未清理）：直接认领
        if (portOpen()) {
            started = true
            lastError = ""
            cachedUrl = ""
            log("$PORT 端口已在监听，直接复用")
            log("入口：${webUrl()}")
            return@withContext true
        }

        //3. Node 自举
        ensureNode { log(it) }?.let {
            lastError = it
            Logs.e(TAG, it)
            return@withContext false
        }

        //4. dsh 是否已安装，缺了就装（首次约 1GB 磁盘 + 数分钟）
        val has = sh("command -v $DSH_BIN 2>/dev/null || echo not_found", 30_000L)
        if (has.contains("not_found")) {
            log("开始安装 @deepseek-ai/dsh（体积较大，耐心等）…")
            val inst = sh(
                "npm install -g @deepseek-ai/dsh@latest --registry=$NPM_REG 2>&1 | tail -5",
                1_800_000L
            )
            log(inst.trim().takeLast(400))
            if (!inst.contains("added") && !inst.contains("up to date") && !inst.contains("changed")) {
                lastError = "dsh 安装失败：${inst.trim().takeLast(300)}"
                return@withContext false
            }
            //兜底软链：npm 全局 bin 有时没进 PATH
            sh("ln -sf $NODE_DIR/bin/$DSH_BIN /usr/local/bin/$DSH_BIN 2>/dev/null; true", 30_000L)
        }

        //5. 后台启动（nohup + 重定向，脱离本次 proot 会话；--no-open 免得去调不存在的浏览器）
        sh("pkill -f 'dsh web' 2>/dev/null; true", 15_000L)
        sh("cd /root && nohup $DSH_BIN web --host 127.0.0.1 --port $PORT --no-open </dev/null >$LOG 2>&1 & echo launched", 60_000L)
        log("dsh web 已拉起，等端口就绪…")

        //6. 等端口就绪（最多 90 秒；Node 冷启动 + 插件加载偏慢）
        var attempts = 0
        while (attempts < 180) {
            if (portOpen()) {
                started = true
                lastError = ""
                cachedUrl = ""
                val url = webUrl()
                log("启动成功：$url")
                //服务侧首批日志也进仓：页面白屏时对照这里最直接
                Logs.i(TAG, "dsh-web.log:\n${sh("tail -20 $LOG 2>/dev/null", 15_000L).trim().take(1500)}")
                return@withContext true
            }
            Thread.sleep(500)
            attempts++
        }

        lastError = "dsh web 端口未就绪（90s 超时），见日志"
        log(lastError)
        Logs.e(TAG, "$lastError\n${sh("tail -30 $LOG 2>/dev/null", 15_000L).trim().take(1500)}")
        return@withContext false
    } //启动

    /** 停止 dsh：pkill 后确认端口释放 */
    suspend fun stop(): Boolean = withContext(Dispatchers.IO) {
        sh("pkill -f 'dsh web' 2>/dev/null; true", 15_000L)
        var i = 0
        while (i < 20 && portOpen()) {
            Thread.sleep(250)
            i++
        }
        started = false
        lastError = "已停止"
        cachedUrl = "" //token 与进程绑定，停了就作废
        Logs.i(TAG, "stop() 已执行，端口释放=${!portOpen()}")
        portOpen()
    } //停止

    /** 当前状态：running / port / rss_kb / uptime_s */
    suspend fun status(): Map<String, Any> = withContext(Dispatchers.IO) {
        if (!portOpen()) return@withContext mapOf("running" to false, "error" to lastError)
        val rss = sh("ps -o rss= -C node 2>/dev/null | awk '{s+=\$1} END {print s}'", 15_000L)
            .trim().toLongOrNull() ?: 0L
        mapOf(
            "running" to true,
            "port" to PORT,
            "rss_kb" to rss,
            "uptime_s" to (System.currentTimeMillis() - startTimeMs) / 1000
        )
    } //状态

    /** 最近日志（dsh web 的 stdout/stderr 尾巴） */
    suspend fun logs(): String = withContext(Dispatchers.IO) {
        sh("tail -50 $LOG 2>/dev/null || echo 'no logs'", 15_000L)
    } //日志
} //dsh 内核管理
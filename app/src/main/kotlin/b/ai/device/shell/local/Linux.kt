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
import b.ai.core.data.io.Layout
import b.ai.device.shell.root.Root
import b.ai.device.shell.root.Shiz

import android.content.Context
import android.os.Process
import b.ai.App
import b.ai.core.cfg.app.Cfg
import b.ai.core.model.chat.WorkMode
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/**
 * 内置 Linux 环境：proot + Ubuntu 根文件系统，不依赖 Termux。
 * 两套落点：普通 / Root 模式用应用私有目录；Shizuku 模式用 /data/local/tmp（shell 身份读不进 App 私有目录）。
 */
object Linux {
    /** 公共根目录：Shizuku 的 shell(UID 2000) 只在这里能读写，rootfs 必须跟着它走 */
    private const val SHARED_BASE = "/data/local/tmp/bai-linux"

    /** 是否把 rootfs / proot 附属文件放公共目录：只有 Shizuku 模式需要 */
    fun sharedMode(): Boolean = Cfg.workMode == WorkMode.SHIZUKU

    /** 私有落点：App 沙箱内，普通身份可以直接执行 */
    private fun privBase() = Layout.dir(App.self, Layout.USER + "/linux")

    /** proot 与库放这里；rootfs 单独一层目录，方便整体删掉重装 */
    private fun base() = if (sharedMode()) File(SHARED_BASE) else privBase()
    private fun libs() = File(base(), "lib")
    private fun tmp() = File(base(), "tmp").apply { mkdirs() }

    /** native 库目录：proot 以 libproot.so 打进 jniLibs，Android10+ 只有这个目录里的文件能被执行 */
    private fun nativeDir(): File = runCatching { File(App.self.applicationInfo.nativeLibraryDir) }
        .getOrDefault(File(base(), "native"))

    /** proot 优先用 native 目录那份（普通模式也能跑）；老装法留在 files/linux/proot 的也认 */
    val proot: File
        get() = File(nativeDir(), "libproot.so").takeIf { it.isFile } ?: File(base(), "proot")
    val rootfs: File get() = File(base(), "rootfs")
    private fun tarball() = File(base(), "ubuntu.tar.gz")

    /**
     * 镜像落点。Shizuku 模式下 rootfs 在 /data/local/tmp（shell 的 shell_data_file 域），
     * App 身份往那里写文件会被 SELinux 直接拒（EACCES），所以镜像改放外部存储：
     * App 有 MANAGE_EXTERNAL_STORAGE 能写，shell 也能读，解包再由 shell 做。
     */
    private fun tarPath(): File =
        if (sharedMode()) File(EXT_TAR).apply { parentFile?.mkdirs() } else tarball()

    /** 外置下载兜底：/sdcard/BaiAgent/linux/ubuntu.tar.gz，或 assets 里自带的镜像 */
    private const val EXT_TAR = "/sdcard/BaiAgent/linux/ubuntu.tar.gz"
    private const val ASSET_PROOT = "Linux/proot"
    private const val ASSET_LOADER = "Linux/loader"
    private const val ASSET_TALLOC = "Linux/libtalloc.so.2"
    private const val ASSET_TAR = "Linux/rootfs.tgz"

    /** 本地执行用不了时的哨兵退出码：见到它就退回 Root 通道 */
    private const val NO_LOCAL = -999

    /** 本次进程是否已补过 SELinux 标签：失败路径上最多补一次，chcon -R 整棵 rootfs 要几秒 */
    @Volatile
    private var relabeled = false

    /** Shizuku 模式下的「就位」缓存：公共目录 App 身份 stat 不到，只能问 shell，每次问都是一个 binder 往返 */
    @Volatile
    private var sharedProbe: Boolean? = null

    /**
     * 设备能不能跑内置 Linux：proot 二进制与镜像都只编译了 arm64，
     * 32 位设备（armeabi-v7a）根本执行不了，这里提前判掉，免得报「重装 APK」这种误导文案
     */
    fun abiOk(): Boolean = android.os.Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }

    /**
     * 把 guest 里的相对/绝对路径解析成宿主上的真实文件。
     * rootfs 里大量「绝对软链」（如 /usr/bin/python3 -> /etc/alternatives/python3）在宿主上直接 isFile
     * 会按宿主根目录去解，必然不存在，于是「装好了却报未安装」。这里手动逐跳解析：
     * 绝对目标重新拼回 rootfs 下，相对目标相对自身目录，最多 16 跳。
     */
    fun inside(rel: String): File {
        val root = rootfs.absoluteFile
        var cur = File(root, rel.removePrefix("/"))
        var hops = 0
        while (hops++ < 16) {
            val link = runCatching { java.nio.file.Files.readSymbolicLink(cur.toPath()).toString() }.getOrNull()
                ?: break //不是软链：到此为止
            cur = if (link.startsWith("/")) File(root, link.removePrefix("/"))
            else File(cur.parentFile ?: root, link)
        }
        return cur
    } //解析 rootfs 内路径

    /** rootfs 里某个路径是否存在（软链按 guest 语义解析）；Shizuku 模式下只能问 shell，且不解析 guest 绝对软链 */
    fun existsIn(rel: String): Boolean {
        if (!sharedMode()) return runCatching { inside(rel).exists() }.getOrDefault(false)
        return runCatching {
            val p = File(rootfs, rel.removePrefix("/")).absolutePath
            Shiz.run("[ -e '$p' ] && echo Y", null, 20_000L).out.trim().endsWith("Y")
        }.getOrDefault(false)
    } //guest 路径存在性

    /** 目录是否存在：Shizuku 模式下公共目录 App 身份 stat 不到，只能问 shell */
    private fun dirExists(f: File): Boolean {
        if (!sharedMode()) return f.exists()
        return runCatching { Shiz.run("[ -d '${f.absolutePath}' ] && echo Y", null, 20_000L) }
            .getOrNull()?.out?.trim()?.endsWith("Y") == true
    }

    /** Shizuku 模式：问 shell 要「apt-get 是否可执行」，公共目录 App 身份 stat 不到 */
    private fun shellInstalled(): Boolean = runCatching {
        val p = File(rootfs, "usr/bin/apt-get").absolutePath
        val r = Shiz.run("[ -x '$p' ] && echo Y", null, 20_000L)
        r.out.trim().endsWith("Y")
    }.getOrDefault(false)

    /** 判环境就位：proot + rootfs 里的 apt-get、os-release 都在；apt-get 是普通 ELF，比 etc/os-release 的软链可靠 */
    fun installed(): Boolean {
        if (!abiOk() || !proot.isFile) return false
        //Shizuku 模式：公共目录只能靠 shell 判，结果缓存到进程内，install / remove 时失效
        if (sharedMode()) return sharedProbe ?: shellInstalled().also { sharedProbe = it }
        return existsIn("usr/bin/apt-get") &&
            (existsIn("usr/lib/os-release") || existsIn("etc/os-release"))
    } //环境就位判定
    fun prootReady(): Boolean = abiOk() && proot.isFile

    /** 不再挑工作方式：proot 在 native 目录里，普通 / Shizuku / Root 都能起 */
    fun canRun(): Boolean = abiOk() && proot.isFile

    /** 内嵌终端用的一次启动描述：宿主环境变量 + 本机直接起进程的参数 + 交给 su -c 的整条命令行 */
    class Launch(val env: Map<String, String>, val argv: List<String>, val cmdline: String)

    /** guest 里的 shell：优先 bash（解包镜像自带），没有就退回 sh */
    fun guestShell(): String = if (existsIn("bin/bash")) "/bin/bash" else "/bin/sh"

    /**
     * 组装「在 proot 里起常驻 shell」的启动方式；内嵌终端与一次性执行共用同一套环境。
     * inner 是 guest 内要执行的 argv（第一个元素是程序本身）。proot 缺失返回 null。
     */
    fun launch(inner: List<String>): Launch? {
        if (!proot.isFile) return null
        //每次起 proot 前校一次：重装 APK 后 native 路径会变，软链 / 副本失效就是 CANNOT LINK
        ensureTalloc()
        val ld = ensureLoader()
        val env = mapOf(
            "LD_LIBRARY_PATH" to libs().absolutePath + ":" + nativeDir().absolutePath,
            "PROOT_TMP_DIR" to tmp().absolutePath,
            "PROOT_LOADER" to ld.absolutePath
        )
        val argv = ArrayList<String>(20)
        argv += proot.absolutePath
        argv += listOf("--link2symlink", "-0", "-r", rootfs.absolutePath)
        argv += listOf("-b", "/dev", "-b", "/proc", "-b", "/sys", "-b", "/storage", "-b", "/sdcard")
        argv += listOf("-w", "/root")
        argv += inner
        //交给 su -c 的是一条字符串：环境变量前缀 + 每个参数按 shell 规则转义
        val cmdline = buildString {
            env.forEach { (k, v) -> append(k).append("='").append(v).append("' ") }
            argv.forEach { append(shq(it)).append(' ') }
        }.trimEnd()
        return Launch(env, argv, cmdline)
    } //组装 proot 启动方式

    private fun shq(s: String) = "'" + s.replace("'", "'\\''") + "'"

    private fun blocked(): ShellResult? = when {
        //32 位设备：APK 里没有 libproot.so，装多少次都没用，直接说清楚
        !abiOk() -> ShellResult(-1, "[linux] 当前设备是 32 位（armeabi-v7a），内置 Ubuntu 沙箱只支持 arm64 设备；终端类需求可改用 Shizuku / Root 通道")
        !proot.isFile -> ShellResult(-1, "[linux] 缺少 proot：APK 里的 libproot.so 没解压出来，重装一次 APK")
        else -> null //本地起不来时 exec() 自动退回 Root 通道
    }

    /** 装环境：拷 proot → 取镜像 → 解开 → 写 resolv.conf，任何一步失败立刻返回；force=true 先删旧 rootfs */
    fun install(
        force: Boolean = false,
        onStep: (Int, Int, String) -> Unit = { _, _, _ -> },
        onLog: (String) -> Unit
    ): Boolean {
        blocked()?.let {
            onLog(it.out)
            return false
        }
        //总进度口径：8 个阶段，UI 侧按 cur/total 画总进度条
        val total = 8
        val b = base()
        onLog(
            if (sharedMode()) "落点：公共目录 ${b.absolutePath}（Shizuku / shell 身份，App 身份读不到）"
            else "落点：应用私有目录 ${b.absolutePath}（普通身份直跑）"
        )
        if (sharedMode()) {
            //公共目录必须由 shell 建：App 建的目录属主是 App，shell 随后写不进去
            val mk = exec("mkdir -p '${b.absolutePath}/lib' '${tmp().absolutePath}' && chmod -R 0777 '${b.absolutePath}'", 60_000L)
            if (!mk.ok) onLog("建目录失败(${mk.code})：${mk.out.take(200)}")
        } else {
            b.mkdirs()
            libs().mkdirs()
            tmp().mkdirs()
        }

        if (force) {
            onLog("清除旧 rootfs…")
            if (sharedMode()) exec("rm -rf '${rootfs.absolutePath}'", 600_000L)
            else if (rootfs.exists()) remove()
        }

        onStep(1, total, "准备 proot")
        onLog("准备 proot…")
        if (!proot.isFile) {
            if (!copyAsset(ASSET_PROOT, proot)) {
                onLog("缺少 proot 资产：$ASSET_PROOT")
                return false
            }
        }
        proot.setExecutable(true)
        ensureTalloc()
        ensureLoader()

        if (installed()) {
            onLog("Ubuntu 已就绪，跳过解包")
            return true
        }

        onStep(2, total, "准备 Ubuntu 镜像")
        onLog("准备 Ubuntu 镜像…")
        val tar = tarPath()
        if (!tar.isFile) {
            val ext = File(EXT_TAR)
            //Shizuku 模式下 tar 本身就是外部存储那一份，不能自己拷给自己
            if (ext.absolutePath != tar.absolutePath && ext.isFile) {
                //优先级最高：用户自己放进来的镜像，离线场景靠它
                onLog("用外置镜像 ${ext.absolutePath}")
                if (!ext.copyTo(tar, overwrite = true).isFile) {
                    onLog("复制镜像失败")
                    return false
                }
            } else {
                //内置镜像不再打进 APK：联网后并行测速，择优（或用户手动指定）下载
                val src = Cfg.linuxMirrorNode
                onLog(
                    "镜像源：" + (if (src.isBlank()) "自动（并行测速择优，TTFB ≤ ${LinuxMirror.fastTtfbMs}ms 优先）"
                    else "手动 ${src}") + "（设置 → Linux 沙箱 → 镜像源 可改）"
                )
                onLog("本地没有镜像，开始从镜像节点下载…")
                var lastPct = -1
                val okDl = LinuxMirror.fetch(
                    dst = tar,
                    manualId = Cfg.linuxMirrorNode,
                    onLog = { line -> onLog(line) },
                    onProgress = { got, total ->
                        //进度按 5% 节流，避免日志被刷屏
                        if (total > 0) {
                            val pct = (got * 100 / total).toInt()
                            if (pct >= lastPct + 5) {
                                lastPct = pct
                                onLog("下载 $pct%（${got / 1024 / 1024} / ${total / 1024 / 1024} MB）")
                            }
                        }
                    }
                )
                if (!okDl || !tar.isFile) {
                    //最后兑底：老包可能还带 assets 镜像，能拿到就用
                    if (!copyAsset(ASSET_TAR, tar)) {
                        onLog("没有可用的 Ubuntu 镜像：下载失败，且本地 $EXT_TAR 不存在")
                        return false
                    }
                }
            }
        }
        //解包到 rootfs.new：中途被打断也不会把现有 rootfs 弄成半成品
        onLog("镜像 ${tar.length() / 1024 / 1024} MB，开始解包…")
        val stage = File(base(), "rootfs.new")
        //统一由 exec 建目录：公共目录下 App 建的目录 shell 随后写不进去
        runCatching { exec("rm -rf '${stage.absolutePath}' && mkdir -p '${stage.absolutePath}'", 120_000L) }
        if (!sharedMode()) {
            stage.deleteRecursively()
            stage.mkdirs()
        }
        onStep(3, total, "解包镜像（逐文件回显）")
        onLog("开始解包（1-3 分钟）…")
        relabeled = false
        //-v 计数用于进度感知，但不再逐行喂 UI：tar 解开一个 Ubuntu 是几万个文件，
        //逐行 onLog 会把日志缓冲和重组打满，这就是「点安装环境巨卡」的直接来源
        var unpacked = 0
        val r = exec("cd '${stage.absolutePath}' && tar -xzvf '${tar.absolutePath}'", 600_000L) { line ->
            if (line.isNotBlank()) {
                unpacked++
                if (unpacked % 200 == 0) onLog("… 已解包 $unpacked 个文件")
            }
        }
        onLog("解包结束：共 $unpacked 个条目")
        //tar 的退出码不可靠（perl 之类的硬链接告警也非 0），成败一律看 apt-get 有没有落盘
        if (!r.ok) onLog("tar 返回 ${r.code}（部分告警不影响使用）")
        val aptInStage = if (sharedMode()) {
            runCatching {
                Shiz.run("[ -x '${stage.absolutePath}/usr/bin/apt-get' ] && echo Y", null, 20_000L).out.trim().endsWith("Y")
            }.getOrDefault(false)
        } else File(stage, "usr/bin/apt-get").isFile
        if (!aptInStage) {
            onLog("解包后没有 apt-get：镜像可能不完整，已保留原 rootfs")
            runCatching { exec("rm -rf '${stage.absolutePath}'", 120_000L) }
            return false
        }
        onStep(4, total, "替换 rootfs")
        //校验通过再换上去：旧目录先改名兜底，新目录换名成功后才清掉旧的
        //一律用 mv 走 shell（root 通道就是 root 执行）：Java 的 renameTo 在属主/label 混杂时直接返回 false，
        //之前「旧 rootfs 改名失败，中止」就是这么来的
        val old = File(base(), "rootfs.old")
        exec("rm -rf '${old.absolutePath}'", 120_000L)
        //上一轮中断可能留下半成品 rootfs.old：rm -rf 删不掉时 mv 会报 "Directory not empty"，
        //所以再上 root 删一次，仍删不掉就改个名让位，别让整次安装卡死在第 4 步
        if (old.exists()) runCatching {
            Root.run("rm -rf '${old.absolutePath}'", 120_000L)
        }
        if (old.exists()) {
            val sway = File(base(), "rootfs.old.${System.currentTimeMillis()}")
            onLog("旧 rootfs.old 删不掉，改名让位：${sway.name}")
            runCatching { exec("mv '${old.absolutePath}' '${sway.absolutePath}'", 120_000L) }
        }
        if (dirExists(rootfs)) {
            val mv1 = exec("mv '${rootfs.absolutePath}' '${old.absolutePath}'", 120_000L)
            if (!mv1.ok || !dirExists(old)) {
                onLog("旧 rootfs 改名失败(${mv1.code})：${mv1.out.take(300)}")
                runCatching { exec("rm -rf '${stage.absolutePath}'", 120_000L) }
                return false
            }
        }
        val mv2 = exec("mv '${stage.absolutePath}' '${rootfs.absolutePath}'", 120_000L)
        if (!mv2.ok || !dirExists(rootfs)) {
            onLog("新 rootfs 替换失败(${mv2.code})：${mv2.out.take(300)}，已回退")
            runCatching { exec("mv '${old.absolutePath}' '${rootfs.absolutePath}'", 120_000L) }
            return false
        }
        runCatching { exec("rm -rf '${old.absolutePath}'", 120_000L) }
        onStep(5, total, "修正属主")
        if (sharedMode()) {
            //公共目录模式下文件属主本来就是这个 shell，rootfs 归 shell 所有正合适，不用改
            onLog("公共目录模式：文件属主为 shell，跳过改属主")
        } else {
            onLog("解包完成，修正属主…")
            //root 解开的文件属主是 root，改回应用自己：App 才能写 resolv.conf，删 rootfs 也不用惊动 root
            val uid = Process.myUid()
            val own = exec("chown -R $uid:$uid '${rootfs.absolutePath}'", 120_000L)
            if (!own.ok) onLog("改属主失败(${own.code})：${own.out.take(300)}")
        }
        //root 通道解包出来的文件 label 是裸 s0，缺本应用的 MLS 分类，execve 会被 SELinux 拒，这里补回来
        onStep(6, total, "补 SELinux 标签")
        if (sharedMode()) {
            //公共目录是 shell_data_file，不参与 App 的 MLS 分类，补了反而可能被拒
            onLog("公共目录模式：不需要补 MLS 标签，跳过")
        } else if (relabel()) {
            relabeled = true
            onLog("已补文件标签")
        }

        onStep(7, total, "写 DNS 与 apt 配置")
        //DNS 与主机名先写死，apt 能不能用全看这一步；统一走 exec，公共目录下 App 身份写不进去
        val d = rootfs.absolutePath
        val w = exec(
            "{ echo 'nameserver 1.1.1.1'; echo 'nameserver 8.8.8.8'; } > '$d/etc/resolv.conf' && " +
                "echo 'bai' > '$d/etc/hostname' && " +
                "mkdir -p '$d/etc/apt/apt.conf.d' && " +
                "printf '%s\\n' 'Acquire::ForceIPv4 \"true\";' 'Acquire::http::Timeout \"20\";' " +
                "'Acquire::https::Timeout \"20\";' 'Acquire::Retries \"1\";' > '$d/etc/apt/apt.conf.d/99bai'",
            120_000L
        )
        if (!w.ok) onLog("写 DNS / apt 配置失败(${w.code})：${w.out.take(300)}")
        //先删镜像副本再报成功：以后要重装还能从 assets 或外置路径重新取
        runCatching { tar.delete() }
        //rootfs 换过了，就位缓存作废，重新问一次 shell
        if (sharedMode()) sharedProbe = null
        if (!installed()) {
            onLog("解包后校验没过：rootfs 里缺 apt-get 或 os-release，镜像可能不完整（已保留镜像副本可重试）")
            return false
        }
        onStep(8, total, "完成")
        onLog("完成：Ubuntu 已可用")
        return true
    } //安装/解包

    /** 在 Ubuntu 里跑命令；-0 走 proot 的假 root，apt 才能用 */
    fun run(cmd: String, timeoutMs: Long = 600_000L): ShellResult {
        blocked()?.let { return it }
        //每次起 proot 前校一次：重装 APK 后 native 路径会变，软链/副本失效就是 CANNOT LINK EXECUTABLE
        ensureTalloc()
        val ld = ensureLoader()
        if (!installed()) {
            return ShellResult(-1, "[linux] 还没装 Ubuntu 环境：设置 → 扩展 → Linux 环境 里点安装")
        }
        val inner = "/usr/bin/env -i HOME=/root PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin " +
            "LANG=C.UTF-8 TERM=dumb /bin/sh -c '${cmd.replace("'", "'\\''")}'"
        val args = buildString {
            append("LD_LIBRARY_PATH='").append(libs().absolutePath)
                .append(":").append(nativeDir().absolutePath).append("' ")
            append("PROOT_TMP_DIR='").append(tmp().absolutePath).append("' ")
            append("PROOT_LOADER='").append(ld.absolutePath).append("' ")
            append("'").append(proot.absolutePath).append("' ")
            append("--link2symlink -0 ")
            append("-r '").append(rootfs.absolutePath).append("' ")
            append("-b /dev -b /proc -b /sys -b /storage -b /sdcard ")
            append("-w /root ")
            append(inner)
        }
        //Shizuku 模式：rootfs 在公共目录，proot 必须以 shell 身份跑，App 身份连目录都进不去
        if (sharedMode()) {
            val s = Shiz.run(args, null, timeoutMs)
            if (s.out.startsWith("[Shizuku]")) {
                return ShellResult(-1, s.out + "\n[linux] Shizuku 通道不可用：先在设置里授权并启动 Shizuku 服务，或切到 Root 模式")
            }
            return s
        }
        val local = exec(args, timeoutMs)
        if (local.code == 0 || !(local.out.contains("proot error") || local.out.contains("CANNOT LINK"))) return local
        //App 身份起不来 proot：内核解析不到 guest ELF 的解释器（/lib/ld-linux-aarch64.so.1 在宿主没有），实测只有 Root 通道能起
        val rr = Root.run(args, timeoutMs)
        if (rr.code == 0 || !rr.out.contains("proot error")) return rr
        return ShellResult(rr.code, rr.out + "\n[linux] 内置 Ubuntu 需要 root / Shizuku：App 身份无法执行 guest 里的程序")
    } //在 proot 里执行

    /** 自检：能不能起来、apt 在不在 */
    fun selfTest(): ShellResult = run(
        "uname -m; . /etc/os-release 2>/dev/null; echo \"\$PRETTY_NAME\"; " +
            "command -v apt-get >/dev/null && echo apt-ok || echo apt-missing; " +
            "command -v bash >/dev/null && echo bash-ok || echo bash-missing"
    )

    /** 依赖包：编译 + 常用脚本一整套；多装不影响功能，缺了才麻烦 */
    val DEPS = listOf(
        "openjdk-17-jdk-headless", "gradle", "git", "curl", "wget", "unzip", "zip",
        "xz-utils", "python3", "python3-pip", "python3-venv", "nodejs", "npm",
        "build-essential", "cmake", "make", "pkg-config", "openssh-client",
        "vim", "nano", "tmux", "ca-certificates"
    )

    /** SSH 依赖：openssh-client 不是镜像自带，只在「装依赖」时才装，没装过的机器要单独补 */
    fun hasSsh(): Boolean = installed() &&
        existsIn("usr/bin/ssh") && existsIn("usr/bin/scp")
    /** 补装 openssh-client：不用重装环境，apt 里加一个包；update 失败也继续试，装不装得上最后看 hasSsh */
    fun installSsh(onLog: (String) -> Unit): Boolean {
        if (!installed()) {
            onLog("先装 Ubuntu 环境，再补 openssh-client")
            return false
        }
        if (hasSsh()) {
            onLog("openssh-client 已经在了")
            return true
        }
        onLog("apt-get update…")
        val u = run("export DEBIAN_FRONTEND=noninteractive; apt-get update -y", 900_000L)
        if (!u.ok) onLog("update 返回 ${u.code}：${u.out.takeLast(400)}")
        onLog("apt-get install -y --no-install-recommends openssh-client…")
        val r = run(
            "export DEBIAN_FRONTEND=noninteractive; " +
                "apt-get install -y --no-install-recommends openssh-client",
            900_000L
        )
        onLog(r.out.takeLast(800))
        val ok = hasSsh()
        onLog(if (ok) "openssh-client 装好了" else "openssh-client 没装上（退出码 ${r.code}）：查网络后重试")
        return ok
    } //补装 ssh
    /** 依赖就位判断：挑两个最能代表用途的二进制，比 apt 查询快也稳 */
    fun depsInstalled(): Boolean = installed() &&
        existsIn("usr/bin/python3") &&
        existsIn("usr/bin/git")

    /** 依赖清单状态：直接看 rootfs 里有没有对应二进制（软链按 guest 语义解） */
    fun depReport(): List<Pair<String, Boolean>> = listOf(
        "python3" to existsIn("usr/bin/python3"),
        "pip3" to existsIn("usr/bin/pip3"),
        "git" to existsIn("usr/bin/git"),
        "curl" to existsIn("usr/bin/curl"),
        "gcc" to existsIn("usr/bin/gcc"),
        "make" to existsIn("usr/bin/make"),
        "node" to existsIn("usr/bin/node"),
        "java" to existsIn("usr/lib/jvm"),
        "gradle" to existsIn("usr/bin/gradle"),
        "ssh" to existsIn("usr/bin/ssh")
    )

    /** 一键装依赖：apt update + install 一整套，日志边跑边回 */
    fun installDeps(onLog: (String) -> Unit): Boolean {
        if (!installed()) {
            onLog("先装 Ubuntu 环境，再装依赖")
            return false
        }
        onLog("apt-get update（第一次要几分钟）…")
        val u = run("export DEBIAN_FRONTEND=noninteractive; apt-get update -y", 900_000L)
        if (!u.ok) onLog("update 返回 ${u.code}：${u.out.takeLast(600)}")
        onLog("apt-get install -y ${DEPS.size} 个包…")
        val r = run(
            "export DEBIAN_FRONTEND=noninteractive; " +
                "apt-get install -y --no-install-recommends ${DEPS.joinToString(" ")}",
            1_800_000L
        )
        onLog(r.out.takeLast(1500))
        val ok = depsInstalled()
        onLog(if (ok) "依赖装好了：python3 / git 都能用" else "依赖没装全（退出码 ${r.code}），可再点一次")
        return ok
    } //一键装依赖

    fun remove(): Boolean {
        sharedProbe = null
        if (sharedMode()) {
            //公共目录里全是 shell 属主，Java 删不动，直接走 shell
            exec("rm -rf '${rootfs.absolutePath}'", 600_000L)
            return !dirExists(rootfs)
        }
        runCatching { rootfs.deleteRecursively() }
        //属主还挂在 root（上次解包中途断掉）时 Java 删不动，交给 root 通道收尾一次
        if (rootfs.exists()) runCatching { exec("rm -rf '${rootfs.absolutePath}'", 120_000L) }
        return !rootfs.exists()
    } //删掉 rootfs

    /** 本进程的 SELinux MLS 段（形如 s0:c157,c256,c512,c768）：root 解出来的文件缺这一段，App 就 execve 不了它们 */
    private fun mls(): String = runCatching {
        val raw = File("/proc/self/attr/current").readText().trim().trim('\u0000')
        raw.split(":", limit = 4).getOrNull(3) ?: "s0"
    }.getOrDefault("s0")

    /** root 通道解包的文件 label 是裸 s0，MLS 下的 execute 要求进程与文件 level 相等，所以补成 app_data_file + 自己的分类 */
    private fun relabel(): Boolean {
        val ctx = "u:object_r:app_data_file:${mls()}"
        val cmd = "chcon -h -R '$ctx' '${rootfs.absolutePath}' '${libs().absolutePath}' '${tmp().absolutePath}' '${loader().absolutePath}'"
        return Root.run(cmd, 300_000L).code == 0
    } //修正 rootfs 的 SELinux 标签

    /** proot 改写 guest ELF 的解释器时要用 loader；libproot.so 里硬编的是 Termux 的路径，其他应用读不到，这里给它一份自己的 */
    private fun loader(): File = File(base(), "loader")

    /** loader 优先用 native 目录里那份，没有就从 assets 解一份并给执行位 */
    private fun ensureLoader(): File {
        val native = File(nativeDir(), "libproot-loader.so")
        if (native.isFile) return native
        val dst = loader()
        dst.parentFile?.mkdirs()
        if (!dst.isFile || dst.length() == 0L) copyAsset(ASSET_LOADER, dst)
        dst.setExecutable(true)
        return dst
    } //补齐 proot 的 loader

    /** libtalloc 必须能以 libtalloc.so.2 的名字被链接器找到；重装 APK 后 native 路径会变，所以每次核对一次，用复制不用软链，路径变了也不怕 */
    private fun ensureTalloc() {
        //Shizuku 模式：libs() 在 /data/local/tmp（shell_data_file），App 身份写不进去，
        //所以先由 App 放到外部存储，再让 shell 复制进公共目录
        if (sharedMode()) {
            val talloc = File(libs(), "libtalloc.so.2")
            val native = File(nativeDir(), "libtalloc.so")
            if (!native.isFile) return
            val stage = File(EXT_TAR).parentFile?.resolve("libtalloc.so.2") ?: return
            runCatching { stage.parentFile?.mkdirs(); native.copyTo(stage, overwrite = true) }
            if (!stage.isFile) return
            exec(
                "mkdir -p '${libs().absolutePath}' && cp -f '${stage.absolutePath}' '${talloc.absolutePath}' && " +
                    "chmod 0644 '${talloc.absolutePath}' && rm -f '${stage.absolutePath}'",
                60_000L
            )
            return
        }
        libs().mkdirs()
        val talloc = File(libs(), "libtalloc.so.2")
        val native = File(nativeDir(), "libtalloc.so")
        if (native.isFile) {
            if (talloc.isFile && talloc.length() == native.length()) return
            //断掉的软链 exists() 为 false，但 delete() 能清掉它本身
            runCatching { talloc.delete() }
            runCatching { native.copyTo(talloc, overwrite = true) }
            if (talloc.isFile) return
        }
        if (!talloc.isFile) {
            libs().mkdirs()
            copyAsset(ASSET_TALLOC, talloc)
        }
    } //补齐 libtalloc.so.2

    /**
     * 往 rootfs 里写一个文本文件，不依赖 Root：
     * 普通模式 App 自己就能写；Shizuku 模式 rootfs 属主是 shell，只能让 shell 写。
     * @param rel rootfs 内相对路径（不带前导 /）
     */
    fun writeGuest(rel: String, body: String): ShellResult {
        val f = File(rootfs, rel.removePrefix("/"))
        if (!sharedMode()) {
            return runCatching {
                f.parentFile?.mkdirs()
                f.writeText(body)
                ShellResult(0, "")
            }.getOrElse { ShellResult(-1, it.message ?: "写入失败") }
        }
        val dir = f.parentFile?.absolutePath ?: rootfs.absolutePath
        return exec(
            "mkdir -p '$dir' && cat > '${f.absolutePath}' <<'BXL_GUEST_EOF'\n$body\nBXL_GUEST_EOF",
            60_000L
        )
    } //写 guest 文件

    private fun copyAsset(name: String, dst: File): Boolean {
        val ctx: Context = App.self
        return runCatching {
            dst.parentFile?.mkdirs()
            ctx.assets.open(name).use { ins ->
                dst.outputStream().use { out -> ins.copyTo(out) }
            }
            true
        }.getOrDefault(false)
    } //assets 拷到私有目录

    /**
     * 宿主侧执行（tar / rm / mv / chown 这类）：
     * Shizuku 模式先用 shell 身份（公共目录只有它能写），失败再自己跑、再退 Root；
     * 其它模式先自己跑，本地起不来（没有 exec 权限）再退 Root。
     */
    private fun exec(cmd: String, timeoutMs: Long, onLine: ((String) -> Unit)? = null): ShellResult {
        blocked()?.let { return it }
        if (sharedMode()) {
            val s = Shiz.run(cmd, null, timeoutMs)
            //只有真跑通才收：带 [Shizuku] 前缀的都是通道层报错
            if (!s.out.startsWith("[Shizuku]")) {
                onLine?.let { cb -> s.out.lineSequence().forEach { if (it.isNotBlank()) cb(it) } }
                return s
            }
        }
        val local = localExec(arrayOf("/system/bin/sh", "-c", cmd), timeoutMs, onLine)
        if (local.code != NO_LOCAL) return local
        return Root.run(cmd, timeoutMs)
    } //宿主命令

    /** 用 ProcessBuilder 直接起进程：toybox tar / proot 都在系统或 native 目录，应用自己就能执行 */
    private fun localExec(argv: Array<String>, timeoutMs: Long, onLine: ((String) -> Unit)? = null): ShellResult {
        return runCatching {
            val p = ProcessBuilder(*argv).redirectErrorStream(true).start()
            val out = StringBuilder()
            val pump = Thread {
                runCatching {
                    p.inputStream.bufferedReader().forEachLine { line ->
                        out.appendLine(line)
                        //有回调就边跑边吐，解包时能逐文件刷进度
                        onLine?.invoke(line)
                    }
                }
            }.apply { isDaemon = true; start() }
            val done = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!done) {
                runCatching { p.destroyForcibly() }
                pump.join(1200)
                return ShellResult(-1, out.toString() + "\n[超时 ${timeoutMs / 1000}s，已中止]")
            }
            pump.join(2000)
            ShellResult(p.exitValue(), out.toString())
        }.getOrElse { ShellResult(NO_LOCAL, it.message ?: "本地执行失败") }
    } //本地执行器
} //内置 Linux 环境
package b.ai.exec

import android.content.Context
import android.os.Process
import b.ai.App
import b.ai.core.Cfg
import b.ai.core.WorkMode
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/**内置 Linux 环境：proot + Ubuntu 根文件系统，跑在应用私有目录，不依赖 Termux */
object Linux {
    /** proot 与库放这里；rootfs 单独一层目录，方便整体删掉重装 */
    private fun base() = File(App.self.filesDir, "linux")
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

    /** 外置下载兜底：/sdcard/BaiAgent/linux/ubuntu.tar.gz，或 assets 里自带的镜像 */
    private const val EXT_TAR = "/sdcard/BaiAgent/linux/ubuntu.tar.gz"
    private const val ASSET_PROOT = "linux/proot"
    private const val ASSET_LOADER = "linux/loader"
    private const val ASSET_TALLOC = "linux/libtalloc.so.2"
    private const val ASSET_TAR = "linux/rootfs.tgz"

    /** 本地执行用不了时的哨兵退出码：见到它就退回 Root 通道 */
    private const val NO_LOCAL = -999

    /** 本次进程是否已补过 SELinux 标签：失败路径上最多补一次，chcon -R 整棵 rootfs 要几秒 */
    @Volatile
    private var relabeled = false

    /** 判环境就位：proot + rootfs 里的 apt-get、os-release 都在；apt-get 是普通 ELF，比 etc/os-release 的软链可靠 */
    fun installed(): Boolean = proot.isFile &&
        File(rootfs, "usr/bin/apt-get").isFile &&
        (File(rootfs, "usr/lib/os-release").isFile || File(rootfs, "etc/os-release").isFile)
    fun prootReady(): Boolean = proot.isFile

    /** 不再挑工作方式：proot 在 native 目录里，普通 / Shizuku / Root 都能起 */
    fun canRun(): Boolean = proot.isFile

    private fun blocked(): ShellResult? = if (!proot.isFile) {
        ShellResult(-1, "[linux] 缺少 proot：APK 里的 libproot.so 没解压出来，重装一次 APK")
    } else null //本地起不来时 exec() 自动退回 Root 通道

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
        b.mkdirs()
        libs().mkdirs()
        tmp().mkdirs()

        if (force && rootfs.exists()) {
            onLog("清除旧 rootfs…")
            remove()
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
        val tar = tarball()
        if (!tar.isFile) {
            val ext = File(EXT_TAR)
            when {
                ext.isFile -> {
                    onLog("用外置镜像 ${ext.absolutePath}")
                    if (!ext.copyTo(tar, overwrite = true).isFile) {
                        onLog("复制镜像失败")
                        return false
                    }
                }
                else -> if (!copyAsset(ASSET_TAR, tar)) {
                    onLog("没有可用的 Ubuntu 镜像：assets/$ASSET_TAR 或 $EXT_TAR")
                    return false
                }
            }
        }
        onLog("镜像 ${tar.length() / 1024 / 1024} MB，开始解包…")
        //解包到 rootfs.new：中途被打断也不会把现有 rootfs 弄成半成品
        val stage = File(base(), "rootfs.new")
        runCatching { exec("rm -rf '${stage.absolutePath}'", 120_000L) }
        stage.deleteRecursively()
        stage.mkdirs()
        onStep(3, total, "解包镜像（逐文件回显）")
        onLog("开始解包（1-3 分钟）…")
        relabeled = false
        //-v 让 tar 把每个解出来的文件名都打出来，逐行喂给 UI，哪怕刷得很快也能看到在动
        val r = exec("cd '${stage.absolutePath}' && tar -xzvf '${tar.absolutePath}'", 600_000L) { line ->
            if (line.isNotBlank()) onLog(line)
        }
        //tar 的退出码不可靠（perl 之类的硬链接告警也非 0），成败一律看 apt-get 有没有落盘
        if (!r.ok) onLog("tar 返回 ${r.code}（部分告警不影响使用）")
        if (!File(stage, "usr/bin/apt-get").isFile) {
            onLog("解包后没有 apt-get：镜像可能不完整，已保留原 rootfs")
            runCatching { stage.deleteRecursively() }
            return false
        }
        onStep(4, total, "替换 rootfs")
        //校验通过再换上去：旧目录先改名兜底，新目录换名成功后才清掉旧的
        val old = File(base(), "rootfs.old")
        runCatching { exec("rm -rf '${old.absolutePath}'", 120_000L) }
        old.deleteRecursively()
        if (rootfs.exists() && !rootfs.renameTo(old)) {
            onLog("旧 rootfs 改名失败，中止")
            runCatching { stage.deleteRecursively() }
            return false
        }
        if (!stage.renameTo(rootfs)) {
            onLog("新 rootfs 替换失败，已回退")
            if (old.exists()) old.renameTo(rootfs)
            return false
        }
        runCatching { old.deleteRecursively() }
        onStep(5, total, "修正属主")
        onLog("解包完成，修正属主…")
        //root 解开的文件属主是 root，改回应用自己：App 才能写 resolv.conf，删 rootfs 也不用惊动 root
        val uid = Process.myUid()
        val own = exec("chown -R $uid:$uid '${rootfs.absolutePath}'", 120_000L)
        if (!own.ok) onLog("改属主失败(${own.code})：${own.out.take(300)}")
        //root 通道解包出来的文件 label 是裸 s0，缺本应用的 MLS 分类，execve 会被 SELinux 拒，这里补回来
        onStep(6, total, "补 SELinux 标签")
        if (relabel()) {
            relabeled = true
            onLog("已补文件标签")
        }

        onStep(7, total, "写 DNS 与主机名")
        //DNS 与主机名先写死，apt 能不能用全看这一步
        runCatching {
            File(rootfs, "etc/resolv.conf").writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n")
            File(rootfs, "etc/hostname").writeText("bai\n")
        }
        //先删镜像副本再报成功：以后要重装还能从 assets 或外置路径重新取
        runCatching { tar.delete() }
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
        File(rootfs, "usr/bin/ssh").isFile && File(rootfs, "usr/bin/scp").isFile
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
        File(rootfs, "usr/bin/python3").isFile &&
        File(rootfs, "usr/bin/git").isFile

    /** 依赖清单状态：直接看 rootfs 里有没有对应二进制 */
    fun depReport(): List<Pair<String, Boolean>> = listOf(
        "python3" to File(rootfs, "usr/bin/python3").isFile,
        "pip3" to File(rootfs, "usr/bin/pip3").isFile,
        "git" to File(rootfs, "usr/bin/git").isFile,
        "curl" to File(rootfs, "usr/bin/curl").isFile,
        "gcc" to File(rootfs, "usr/bin/gcc").isFile,
        "make" to File(rootfs, "usr/bin/make").isFile,
        "node" to File(rootfs, "usr/bin/node").isFile,
        "java" to File(rootfs, "usr/lib/jvm").isDirectory,
        "gradle" to File(rootfs, "usr/bin/gradle").isFile,
        "ssh" to File(rootfs, "usr/bin/ssh").isFile
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

    /** 先自己跑（不依赖 Root）；本地起不来（比如没有 exec 权限）再退回 Root 通道 */
    private fun exec(cmd: String, timeoutMs: Long, onLine: ((String) -> Unit)? = null): ShellResult =
        blocked() ?: localExec(arrayOf("/system/bin/sh", "-c", cmd), timeoutMs, onLine)
            .takeIf { it.code != NO_LOCAL } ?: Root.run(cmd, timeoutMs)

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
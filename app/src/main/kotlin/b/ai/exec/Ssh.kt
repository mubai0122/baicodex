package b.ai.exec

import b.ai.App
import b.ai.core.Cfg
import java.io.File

/**
 * SSH：所有远端动作都在内置 Ubuntu 里跑 ssh / scp。
 * 认证走 SSH_ASKPASS + setsid：密码不写进命令行，只落在 rootfs 里 600 的 /root/.bai/pass。
 * 所有远端路径一律单引号包裹（内部的 ' 变成 '\'' ），命令行参数不做裸拼接。
 */
object Ssh {
    private fun base() = File(Linux.rootfs, "root/.bai")
    private fun passFile() = File(base(), "pass")
    private fun askFile() = File(base(), "ask.sh")

    /** 上传下载的中转目录：放外部私有目录，proot 里通过 /storage 挂载可见，App 自己也有权限 */
    private fun tmpDir(): File {
        val ext = App.self.getExternalFilesDir(null) ?: App.self.cacheDir
        return File(ext, "ssh").apply { mkdirs() }
    }

    /** shell 单引号引用：' → '\''，包完就是 argv 里的一个安全整参数 */
    fun quote(v: String): String = "'" + v.replace("'", "'\\''") + "'"

    private fun target(): String = "${Cfg.sshUser}@${Cfg.sshHost}"

    /** 认证材料 + 环境检查：任何一步不到位就直接说清楚，不要让模型去猜 */
    private fun ready(): String? {
        if (!Linux.installed()) return "还没装内置 Ubuntu 环境：设置 → 扩展 → Linux 环境 里点安装（SSH 靠它里面的 ssh / scp）"
        if (!Linux.hasSsh()) return "内置 Ubuntu 里没有 openssh-client（它不是镜像自带，只在「装依赖」时才装）。" +
            "调一次 ssh_connect 存连接时会自动补装，或到 设置 → 扩展 → Linux 环境 里点「装依赖」；不用重装环境。"
        if (Cfg.sshHost.isBlank() || Cfg.sshUser.isBlank()) {
            return "还没连过服务器：先调 ssh_connect，带上 host / user / pass（port 默认 22）"
        }
        val b = base()
        if (!b.isDirectory && !b.mkdirs()) return "建不了目录 ${b.absolutePath}"
        runCatching {
            askFile().writeText("#!/bin/sh\ncat /root/.bai/pass\n")
            askFile().setExecutable(true, true)
            passFile().writeText(Cfg.sshPass + "\n")
            passFile().setReadable(true, true)
            passFile().setWritable(false, false)
        }.onFailure { return "写认证文件失败：${it.message}" }
        return null
    } //准备认证文件

    private fun sshCmd(remote: String): String = buildString {
        append("HOME=/root SSH_ASKPASS=/root/.bai/ask.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0 ")
        append("setsid -w ssh -T -o StrictHostKeyChecking=accept-new -o ConnectTimeout=10 ")
        append("-o NumberOfPasswordPrompts=1 -p ").append(Cfg.sshPort).append(' ')
        append(quote(target())).append(' ').append(quote(remote))
    } //拼 ssh 命令

    private fun scpCmd(from: String, to: String): String = buildString {
        append("HOME=/root SSH_ASKPASS=/root/.bai/ask.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0 ")
        append("setsid -w scp -q -o StrictHostKeyChecking=accept-new -o ConnectTimeout=10 ")
        append("-P ").append(Cfg.sshPort).append(' ')
        append(quote(from)).append(' ').append(quote(to))
    } //拼 scp 命令

    private fun remoteSpec(path: String): String = "${target()}:$path"

    /** 远端执行一条命令：命令原样交给远端 sh，路径等参数由调用方自己引用 */
    fun exec(remote: String, timeoutMs: Long = 120_000L): ShellResult {
        ready()?.let { return ShellResult(-1, it) }
        if (remote.isBlank()) return ShellResult(-1, "远端命令为空")
        return Linux.run(sshCmd(remote), timeoutMs)
    } //远端执行

    /** 补装 openssh-client：新机器第一次用 ssh 前跑一次，装完直接可用，不需要重装环境 */
    fun setup(): ShellResult {
        if (!Linux.installed()) return ShellResult(-1, "还没装内置 Ubuntu 环境：设置 → 扩展 → Linux 环境 里点安装")
        val sb = StringBuilder()
        val ok = Linux.installSsh { sb.append(it).append('\n') }
        return ShellResult(if (ok) 0 else -1, sb.toString().trimEnd())
    } //补装 ssh
    /** 存连接配置并回一次连通性自检 */
    fun save(host: String, port: Int, user: String, pass: String): ShellResult {
        Cfg.sshHost = host.trim()
        Cfg.sshPort = if (port in 1..65535) port else 22
        Cfg.sshUser = user.trim()
        Cfg.sshPass = pass
        //存连接就意味着要用 ssh 了：openssh-client 不在就当场补，缺它的话后面每条命令都会 not found
        if (Linux.installed() && !Linux.hasSsh()) {
            val sb = StringBuilder()
            if (!Linux.installSsh { sb.append(it).append('\n') }) {
                return ShellResult(-1, "连接已保存，但 openssh-client 没装上，命令还跑不了：\n" + sb.toString().takeLast(800))
            }
        }
        val r = exec("echo BAI_SSH_OK; id -un; uname -sr", 60_000L)
        return if (r.ok && r.out.contains("BAI_SSH_OK")) r
        else ShellResult(r.code, "连接失败：" + r.out)
    } //保存连接

    /** 读远端文本文件：先 scp 到本地中转目录再读，超 4MB 直接拒绝（这种文件应该 pull） */
    fun read(path: String, maxLines: Int = 0): ShellResult {
        ready()?.let { return ShellResult(-1, it) }
        val tmp = File(tmpDir(), "down-${System.currentTimeMillis()}.dat")
        try {
            val r = Linux.run(scpCmd(remoteSpec(path), tmp.absolutePath), 180_000L)
            if (!r.ok) return ShellResult(r.code, r.out)
            if (!tmp.isFile) return ShellResult(-1, "远端没有取到文件：$path")
            if (tmp.length() > 4L * 1024 * 1024) {
                return ShellResult(-1, "文件 ${tmp.length()} 字节，超过读文本的 4MB 上限：改用 ssh_get 下到本地再处理")
            }
            val all = runCatching { tmp.readText() }.getOrElse {
                return ShellResult(-1, "按文本读取失败（可能是二进制）：${it.message}，改用 ssh_get")
            }
            //限行只影响给模型看的部分，文件本身已经完整取到本地
            if (maxLines > 0) {
                val lines = all.lineSequence().toList()
                if (lines.size > maxLines) {
                    return ShellResult(
                        0,
                        lines.take(maxLines).joinToString("\n") +
                            "\n…（共 ${lines.size} 行，只显示前 $maxLines 行：要看全文就把 max_lines 调大，或直接 ssh_get 下载）"
                    )
                }
            }
            return ShellResult(0, all)
        } finally {
            runCatching { tmp.delete() }
        }
    } //读远端文件

    /** 列目录：depth 为 1 时给长格式（含隐藏文件与权限），大于 1 用 find 按层展开，最多 400 行防刷屏 */
    fun ls(path: String, depth: Int = 1): ShellResult {
        val p = path.ifBlank { "/" }
        val d = depth.coerceIn(1, 6)
        return if (d <= 1) exec("ls -la -- " + quote(p), 60_000L)
        else exec("find " + quote(p) + " -maxdepth $d | head -400", 120_000L)
    } //列目录

    /** 一键提权：密码从本地管道喂给远端 sudo -S，不落远端命令行、不进远端 ps */
    fun sudo(remote: String, timeoutMs: Long = 180_000L): ShellResult {
        ready()?.let { return ShellResult(-1, it) }
        if (remote.isBlank()) return ShellResult(-1, "要执行的命令为空")
        if (Cfg.sshPass.isBlank()) return ShellResult(-1, "sudo 免交互要密码：先调 ssh_connect 存一次带 pass 的连接")
        //远端看到的是 sudo -S -p '' -- /bin/sh -c '<cmd>'，密码只在 stdin 里
        val inner = "sudo -S -p '' -- /bin/sh -c " + quote(remote)
        val cmd = "{ printf '%s' " + quote(Cfg.sshPass) + "; echo; } | " + sshCmd(inner)
        return Linux.run(cmd, timeoutMs)
    } //sudo 提权执行

    /** 写远端文本：本地落临时文件 → scp 上传 → 删临时文件；父目录先建好 */
    fun write(path: String, content: String): ShellResult {
        ready()?.let { return ShellResult(-1, it) }
        val dir = path.substringBeforeLast('/', "")
        if (dir.isNotEmpty()) {
            val m = exec("mkdir -p -- " + quote(dir), 60_000L)
            if (!m.ok) return ShellResult(m.code, "建父目录失败：" + m.out)
        }
        val tmp = File(tmpDir(), "up-${System.currentTimeMillis()}.dat")
        return try {
            tmp.writeText(content)
            val r = Linux.run(scpCmd(tmp.absolutePath, remoteSpec(path)), 180_000L)
            if (!r.ok) {
                ShellResult(r.code, r.out)
            } else {
                val sz = exec("wc -c < " + quote(path), 60_000L)
                ShellResult(0, "已写入 $path（${content.length} 字符）" + if (sz.ok) "\n远端大小：" + sz.out.trim() else "")
            }
        } finally {
            runCatching { tmp.delete() }
        }
    } //写远端文件

    /** 本地 → 远端：local 是本机绝对路径（多半在工作区里），remote 是服务器上的目标路径 */
    fun push(local: String, remote: String): ShellResult {
        ready()?.let { return ShellResult(-1, it) }
        val src = File(local)
        if (!src.exists()) return ShellResult(-1, "本地文件不存在：$local")
        val dir = remote.substringBeforeLast('/', "")
        if (dir.isNotEmpty()) exec("mkdir -p -- " + quote(dir), 60_000L)
        return Linux.run(scpCmd(src.absolutePath, remoteSpec(remote)), 900_000L)
    } //上传

    /** 远端 → 本地：父目录自动建 */
    fun pull(remote: String, local: String): ShellResult {
        ready()?.let { return ShellResult(-1, it) }
        val dst = File(local)
        dst.parentFile?.mkdirs()
        val r = Linux.run(scpCmd(remoteSpec(remote), dst.absolutePath), 900_000L)
        if (!r.ok) return r
        if (!dst.isFile) return ShellResult(-1, "本地没有落盘：$local")
        return ShellResult(0, "已下载到 $local（${dst.length()} 字节）")
    } //下载

    /** 远端打包：to 为空就打到源文件同目录的 .tar.gz */
    fun pack(path: String, to: String): ShellResult {
        val src = path.trimEnd('/')
        val dest = to.ifBlank { "$src.tar.gz" }
        val parent = src.substringBeforeLast('/', "/")
        val name = src.substringAfterLast('/')
        val cmd = "tar czf " + quote(dest) + " -C " + quote(parent) + " " + quote(name) +
            " && wc -c < " + quote(dest) + " && echo BAI_PACK_OK"
        return exec(cmd, 900_000L)
    } //压缩

    /** 远端解包：tar.gz / tar 都认；dir 为空就解到压缩包所在目录 */
    fun unpack(path: String, dir: String): ShellResult {
        val dest = dir.ifBlank { path.substringBeforeLast('/', "/") }
        val m = exec("mkdir -p -- " + quote(dest), 60_000L)
        if (!m.ok) return ShellResult(m.code, "建目标目录失败：" + m.out)
        return exec("tar xzf " + quote(path) + " -C " + quote(dest) + " && echo BAI_UNPACK_OK", 900_000L)
    } //解压
}
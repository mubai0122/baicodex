/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Repository: https://github.com/mubai0122/Baicodex
 *
 * 本文件作用：读 rootfs 里由 root 拥有的文本文件（dsh profile 那批）。
 *
 * 为什么需要它：pnpm 在 profile 目录里写文件时用的是 600 / root:root
 * （实测 package.json 变成 -rw------- root root），App 身份直接读会 EACCES。
 * 所以先按 App 身份读，失败再退回 Root 通道 cat；两者都失败返回 null。
 */
package b.ai.core.dsh

import b.ai.device.shell.local.Linux
import java.io.File

object DshFile {

    /** 单引号转义（shell 字符串里跑） */
    private fun q(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    /**
     * 读文本：App 身份 → 经 Linux 通道（proot 内 cat）兜底。
     * 走 Linux.run 而不是 Root.run：普通模式 rootfs 属主是 App、Shizuku 模式由 shell 读，
     * 两种免 root 场景都能读到 pnpm 写出来的 600 文件。
     * @return 读不到返回 null（调用方自己决定抛什么错）
     */
    fun readText(file: File): String? {
        runCatching { return file.readText() }
        val r = Linux.run("cat ${q(file.absolutePath)}", 15_000L)
        return if (r.ok && r.out.isNotEmpty()) r.out else null
    } //读文本

    /** 文件是否存在（root 目录 700 时 App 的 exists() 也会假阴性，所以带通道兜底） */
    fun isFile(file: File): Boolean {
        if (runCatching { file.isFile }.getOrDefault(false)) return true
        val r = Linux.run("test -f ${q(file.absolutePath)} && echo YES", 10_000L)
        return r.out.contains("YES")
    } //存在判断
} //rootfs 文本读取
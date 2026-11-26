/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27 02:26:00
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * 命令守卫：run_command 是唯一能跳出「工作区」这层壳的工具，所以单独拦一道。
 * 分两级：Blocked = 直接拒绝（不可恢复的系统级操作）；
 *         Confirm = 放行但要用户点一下（删除、推远端、装包、下载即执行…）。
 */

package b.ai.desktop.core.sandbox

/** 命令检查结论 */
sealed interface CommandVerdict {
    /** 直接跑，不用问 */
    data object Safe : CommandVerdict

    /** 要用户确认，附上原因给卡片显示 */
    data class Confirm(val reason: String) : CommandVerdict

    /** 拒绝执行，原因回灌给模型 */
    data class Blocked(val reason: String) : CommandVerdict
}

object CommandGuard {

    private data class Rule(val pattern: Regex, val reason: String)

    /** 绝不执行：重装系统、改引导、抹盘、动注册表核心、改防火墙、关防护 */
    private val BLOCKED = listOf(
        Rule(Regex("""\bformat\s+[a-z]:""", RegexOption.IGNORE_CASE), "格式化磁盘"),
        Rule(Regex("""\bdiskpart\b""", RegexOption.IGNORE_CASE), "分区表操作"),
        Rule(Regex("""\bbcdedit\b""", RegexOption.IGNORE_CASE), "修改启动配置"),
        Rule(Regex("""\bcipher\s+/w""", RegexOption.IGNORE_CASE), "擦除磁盘剩余空间"),
        Rule(Regex("""\bvssadmin\s+delete""", RegexOption.IGNORE_CASE), "删除卷影副本"),
        Rule(Regex("""\bwbadmin\s+delete""", RegexOption.IGNORE_CASE), "删除系统备份"),
        Rule(Regex("""\b(rd|rmdir)\s+/s\s+/q\s+"?[a-z]:\\?"?\s*$""", RegexOption.IGNORE_CASE), "递归清空盘符根目录"),
        Rule(Regex("""\bdel\s+/[fsq]+\s+"?[a-z]:\\?"?\s*$""", RegexOption.IGNORE_CASE), "删除盘符根目录"),
        Rule(Regex("""\breg\s+delete\s+"?hklm""", RegexOption.IGNORE_CASE), "删除本机注册表配置"),
        Rule(Regex("""\bnet\s+user\s+\S+\s+\S+\s+/add""", RegexOption.IGNORE_CASE), "创建系统账户"),
        Rule(Regex("""\bnetsh\s+(advfirewall|firewall)""", RegexOption.IGNORE_CASE), "修改防火墙"),
        Rule(Regex("""\bshutdown\b""", RegexOption.IGNORE_CASE), "关机 / 重启"),
        Rule(Regex("""\btakeown\b[^\n]*\\windows""", RegexOption.IGNORE_CASE), "夺取系统目录所有权"),
        Rule(Regex("""\bicacls\b[^\n]*\\windows""", RegexOption.IGNORE_CASE), "修改系统目录权限"),
        Rule(Regex("""Set-MpPreference\s+-Disable""", RegexOption.IGNORE_CASE), "关闭杀毒防护"),
        Rule(Regex("""\bcurl\b[^\n|]*\|\s*(ba)?sh""", RegexOption.IGNORE_CASE), "下载后直接执行"),
        Rule(Regex("""\biwr\b[^\n|]*\|\s*iex""", RegexOption.IGNORE_CASE), "下载后直接执行"),
        Rule(Regex("""\bRemove-Item\b[^\n]*-Recurse[^\n]*[a-z]:\\(\s|$)""", RegexOption.IGNORE_CASE), "递归删除盘根"),
    )

    /** 要确认：删除、移动巨量、推远端、装包、联网下载、改执行策略 */
    private val CONFIRM = listOf(
        Rule(Regex("""\b(del|erase)\b""", RegexOption.IGNORE_CASE), "删除文件"),
        Rule(Regex("""\b(rd|rmdir)\b""", RegexOption.IGNORE_CASE), "删除目录"),
        Rule(Regex("""\brm\s+-[a-z]*[rf]""", RegexOption.IGNORE_CASE), "递归强制删除"),
        Rule(Regex("""\bRemove-Item\b""", RegexOption.IGNORE_CASE), "PowerShell 删除"),
        Rule(Regex("""\breg\s+(add|delete|import)\b""", RegexOption.IGNORE_CASE), "改注册表"),
        Rule(Regex("""\bgit\s+push\b""", RegexOption.IGNORE_CASE), "推送到远端仓库"),
        Rule(Regex("""\bgit\s+reset\s+--hard\b""", RegexOption.IGNORE_CASE), "丢弃本地改动"),
        Rule(Regex("""\bgit\s+clean\s+-[a-z]*f""", RegexOption.IGNORE_CASE), "清理未跟踪文件"),
        Rule(Regex("""\b(npm|pnpm|yarn)\s+(publish|install|add)\b""", RegexOption.IGNORE_CASE), "安装 / 发布依赖"),
        Rule(Regex("""\bpip\s+install\b""", RegexOption.IGNORE_CASE), "安装 Python 依赖"),
        Rule(Regex("""\b(curl|wget)\b""", RegexOption.IGNORE_CASE), "联网下载"),
        Rule(Regex("""Invoke-WebRequest|Invoke-RestMethod""", RegexOption.IGNORE_CASE), "联网下载"),
        Rule(Regex("""Set-ExecutionPolicy""", RegexOption.IGNORE_CASE), "修改脚本执行策略"),
        Rule(Regex("""\bsc\s+(create|delete|config)\b""", RegexOption.IGNORE_CASE), "改系统服务"),
        Rule(Regex("""\btaskkill\b""", RegexOption.IGNORE_CASE), "结束进程"),
        Rule(Regex("""\bstart\b\s+\S""", RegexOption.IGNORE_CASE), "启动外部程序"),
        Rule(Regex("""powershell[^\n]*-e(nc|ncodedcommand)?\s""", RegexOption.IGNORE_CASE), "执行编码后的 PowerShell"),
        Rule(Regex("""\bchmod\b|\bchown\b""", RegexOption.IGNORE_CASE), "修改文件权限"),
        Rule(Regex("""\b(gradle|mvn)\s+(clean|build|install)\b""", RegexOption.IGNORE_CASE), "执行构建（会写大量文件）"),
    )

    /**
     * 检查一条命令。返回 Blocked 时工具直接失败；
     * Confirm 时上层会弹确认卡片，用户点了才真正执行。
     */
    fun check(command: String): CommandVerdict {
        val text = command.replace('\u0000', ' ').trim()
        if (text.isEmpty()) return CommandVerdict.Blocked("命令为空")

        BLOCKED.firstOrNull { it.pattern.containsMatchIn(text) }?.let {
            return CommandVerdict.Blocked("已拦截：${it.reason}（系统级操作不在沙箱允许范围内）")
        }
        CONFIRM.firstOrNull { it.pattern.containsMatchIn(text) }?.let {
            return CommandVerdict.Confirm(it.reason)
        }
        return CommandVerdict.Safe
    }
}

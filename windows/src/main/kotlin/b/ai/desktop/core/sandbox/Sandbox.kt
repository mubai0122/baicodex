/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27 02:24:00
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Windows 文件沙箱：路径校验、敏感目录黑名单、保留设备名、写入体积与可执行文件识别。
 * 手机端跑的是 Linux 沙箱（bwrap），这里对齐成一套「先证明安全，再放行」的闸门：
 * 所有文件类工具都必须过 Workspace.resolve / resolveWritable，绕不过去。
 */

package b.ai.desktop.core.sandbox

import java.io.File
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** 工具风险等级：决定执行前要不要弹确认卡片 */
enum class Risk(val label: String, val hint: String) {
    LOW("只读", "看一眼就走，不弹窗"),
    MEDIUM("写入", "会改动工作区里的文件"),
    HIGH("危险", "删除 / 执行命令 / 覆盖可执行文件"),
}

/** 沙箱档位，对齐手机端「沙箱权限」的三档 */
enum class SandboxMode(val id: String, val label: String, val hint: String) {
    STRICT("strict", "严格", "只能读写工作区，命令一律先问"),
    STANDARD("standard", "标准", "工作区内自由读写，删除与命令先问"),
    RELAXED("relaxed", "宽松", "工作区内基本不管，只拦系统级危险操作");

    companion object {
        fun of(id: String?): SandboxMode =
            entries.firstOrNull { it.id == (id ?: "").trim() } ?: STANDARD
    }
}

/** 路径合法性检查失败；消息直接回灌给模型，说明「为什么不行」 */
class SandboxViolation(message: String) : IllegalArgumentException(message)

/**
 * Windows 特有的路径坑，逐条挡掉：
 *  - UNC / 设备命名空间（\\?\C:\...、\\.\PhysicalDrive0）可以绕过盘符限制
 *  - 保留设备名（CON、NUL、COM1…）永远不能当普通文件名
 *  - 结尾的空格和点会被 Win32 悄悄截断，"a.txt " 和 "a.txt" 其实是同一个文件
 */
object SandboxPolicy {
    /** 单文件写入上限：超过就请用户自己动手，避免模型一口气写爆磁盘 */
    const val MAX_WRITE_BYTES = 24L * 1024 * 1024

    private val RESERVED_NAMES = buildSet {
        add("CON"); add("PRN"); add("AUX"); add("NUL")
        (1..9).forEach { add("COM$it"); add("LPT$it") }
    }

    /** 可执行 / 脚本类扩展名：写入它们要升到 HIGH，让用户看清楚 */
    private val EXECUTABLE_EXT = setOf(
        "exe", "dll", "sys", "scr", "cpl", "ocx", "drv", "msi", "msix",
        "bat", "cmd", "ps1", "psm1", "vbs", "vbe", "js", "jse", "wsf", "wsh",
        "hta", "reg", "com", "pif", "lnk", "url",
    )

    /** 系统关键目录：哪怕工作区被用户设在盘根，也不许模型往里写 */
    private val SYSTEM_DIRS: List<String> = run {
        val windir = System.getenv("windir") ?: "C:\\Windows"
        val programFiles = System.getenv("ProgramFiles") ?: "C:\\Program Files"
        val programFilesX86 = System.getenv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)"
        val programData = System.getenv("ProgramData") ?: "C:\\ProgramData"
        listOf(windir, programFiles, programFilesX86, programData)
            .map { it.replace('/', '\\').trimEnd('\\').lowercase() }
    }

    fun isExecutableName(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in EXECUTABLE_EXT
    }

    /** 原始字符串层面的检查：在 resolve 之前先挡住明显恶意的写法 */
    fun checkRawPath(raw: String) {
        val p = raw
        if (p.startsWith("\\\\") || p.startsWith("//")) {
            throw SandboxViolation("不接受 UNC / 设备路径：$raw")
        }
        if (p.contains("\\\\?\\") || p.contains("//?/")) {
            throw SandboxViolation("不接受设备命名空间路径：$raw")
        }
        val leaf = p.trimEnd('\\').substringAfterLast('\\').substringAfterLast('/')
        val base = leaf.substringBefore('.').uppercase()
        if (base in RESERVED_NAMES) {
            throw SandboxViolation("「$leaf」是 Windows 保留设备名，不能作为文件")
        }
        if (leaf.endsWith(" ") || leaf.endsWith(".")) {
            throw SandboxViolation("路径结尾不能是空格或点（Windows 会截断）：$raw")
        }
        val illegal = leaf.filter { it in "<>:\"|?*" }
        if (illegal.isNotEmpty()) {
            throw SandboxViolation("路径包含非法字符 $illegal：$raw")
        }
    }

    /** 落盘前的目标校验：系统目录、盘符根、可执行文件 */
    fun checkWritableTarget(root: File, target: File, mode: SandboxMode) {
        val rootPath = root.canonicalFile.path.replace('/', '\\').lowercase()
        val targetPath = target.canonicalFile.path.replace('/', '\\').lowercase()

        if (target == target.toPath().root.toFile()) {
            throw SandboxViolation("不能直接操作盘符根目录")
        }
        // 工作区自己就在系统目录里的话（用户手动设的），标准档也不放行
        if (mode != SandboxMode.RELAXED) {
            SYSTEM_DIRS.firstOrNull { targetPath.startsWith("$it\\") || targetPath == it }?.let {
                throw SandboxViolation("系统目录受保护：$it")
            }
        }
        // 工作区被设成盘根（C:\）时，禁止对一级子目录整树动手
        val rootIsDrive = rootPath.length <= 3
        if (rootIsDrive) {
            val firstSeg = targetPath.removePrefix(rootPath).trimStart('\\').substringBefore('\\')
            if (firstSeg.isNotEmpty() && targetPath.length > rootPath.length + 1) {
                // C:\Users\... 这种二层级以下仍然拦，避免误删整个用户目录
                if (firstSeg.lowercase() in setOf("users", "windows", "program files", "program files (x86)", "programdata")) {
                    throw SandboxViolation("工作区是盘符根时，禁止进入 $firstSeg 这类系统级目录")
                }
            }
        }
    }

    /** 工具名 + 参数 → 风险等级（沙箱档位会整体升降一级） */
    fun riskOf(toolName: String, args: JsonObject, mode: SandboxMode): Risk {
        val base = when (toolName) {
            "list_dir", "read_file", "stat_path", "search_text" -> Risk.LOW
            "write_file", "edit_file", "make_dir" -> Risk.MEDIUM
            "move_path", "delete_path", "run_command" -> Risk.HIGH
            else -> Risk.MEDIUM // 插件工具、Git 工具之类，一律按写入对待
        }
        // 写可执行文件比写普通文本危险得多
        val touched = listOf("path", "to", "from")
            .mapNotNull { (args[it] as? JsonPrimitive)?.content }
        val hasExecutable = touched.any { SandboxPolicy.isExecutableName(File(it).name) }
        val raised = if (base == Risk.MEDIUM && hasExecutable) Risk.HIGH else base

        return when (mode) {
            SandboxMode.STRICT -> when (raised) {
                Risk.LOW -> Risk.LOW
                Risk.MEDIUM -> Risk.HIGH
                Risk.HIGH -> Risk.HIGH
            }
            SandboxMode.STANDARD -> raised
            SandboxMode.RELAXED -> when (raised) {
                Risk.LOW -> Risk.LOW
                Risk.MEDIUM -> Risk.LOW
                Risk.HIGH -> Risk.MEDIUM
            }
        }
    }
}

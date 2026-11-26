/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27 02:58:00
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * 自检：沙箱边界与命令守卫。gradle run --args="--selftest-sandbox"
 * 不联网、不写工作区以外的任何地方。
 */

package b.ai.desktop.dev

import b.ai.desktop.core.sandbox.CommandGuard
import b.ai.desktop.core.sandbox.CommandVerdict
import b.ai.desktop.core.sandbox.Risk
import b.ai.desktop.core.sandbox.SandboxMode
import b.ai.desktop.core.sandbox.SandboxPolicy
import b.ai.desktop.core.sandbox.SandboxViolation
import b.ai.desktop.core.tool.Workspace
import java.io.File
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun runSandboxSelftest() {
    val root = File(System.getProperty("java.io.tmpdir"), "baicodex-sandbox-test").apply {
        deleteRecursively()
        mkdirs()
    }
    val ws = Workspace(root, SandboxMode.STANDARD)
    var pass = 0
    var fail = 0

    fun ok(name: String, block: () -> Boolean) {
        val result = runCatching(block).getOrElse { false }
        if (result) {
            pass++
            println("  PASS  $name")
        } else {
            fail++
            println("  FAIL  $name")
        }
    }

    /** 期望抛 SandboxViolation */
    fun denied(name: String, block: () -> Unit) {
        val thrown = runCatching(block).exceptionOrNull()
        if (thrown is SandboxViolation) {
            pass++
            println("  PASS  $name → ${thrown.message}")
        } else {
            fail++
            println("  FAIL  $name（没有拒绝：${thrown?.message ?: "正常返回"}）")
        }
    }

    println("== 路径边界")
    ok("工作区内相对路径可用") { ws.resolve("a.txt").absolutePath.startsWith(root.absolutePath) }
    denied("越界 ../..") { ws.resolve("../../evil.txt") }
    denied("UNC 路径") { ws.resolve("\\\\server\\share\\x") }
    denied("设备命名空间") { ws.resolve("\\\\?\\C:\\Windows\\evil.txt") }
    denied("保留设备名") { ws.resolve("NUL") }
    denied("结尾空格") { ws.resolve("a.txt ") }
    denied("非法字符") { ws.resolve("a<b>.txt") }
    denied("绝对路径越界") { ws.resolve("C:\\Windows\\System32\\x.dll") }

    println("\n== 写入目标校验")
    denied("写 C:\\Windows") {
        SandboxPolicy.checkWritableTarget(root, File("C:\\Windows\\baicodex-test.txt"), SandboxMode.STANDARD)
    }
    denied("严格档写 Program Files") {
        SandboxPolicy.checkWritableTarget(
            root,
            File(System.getenv("ProgramFiles") ?: "C:\\Program Files", "baicodex-test.txt"),
            SandboxMode.STRICT,
        )
    }
    ok("工作区内写普通文件放行") {
        runCatching {
            SandboxPolicy.checkWritableTarget(root, File(root, "ok.txt"), SandboxMode.STANDARD)
        }.isSuccess
    }

    println("\n== 风险分级")
    fun args(vararg pairs: Pair<String, String>): JsonObject = buildJsonObject {
        pairs.forEach { (k, v) -> put(k, v) }
    }
    ok("读文件 = LOW") {
        SandboxPolicy.riskOf("read_file", args("path" to "a.txt"), SandboxMode.STANDARD) == Risk.LOW
    }
    ok("写文件 = MEDIUM") {
        SandboxPolicy.riskOf("write_file", args("path" to "a.txt"), SandboxMode.STANDARD) == Risk.MEDIUM
    }
    ok("写 .bat 升到 HIGH") {
        SandboxPolicy.riskOf("write_file", args("path" to "run.bat"), SandboxMode.STANDARD) == Risk.HIGH
    }
    ok("严格档把写入升到 HIGH") {
        SandboxPolicy.riskOf("write_file", args("path" to "a.txt"), SandboxMode.STRICT) == Risk.HIGH
    }
    ok("宽松档把写入降到 LOW") {
        SandboxPolicy.riskOf("write_file", args("path" to "a.txt"), SandboxMode.RELAXED) == Risk.LOW
    }
    ok("删目录 = HIGH") {
        SandboxPolicy.riskOf("delete_path", args("path" to "build"), SandboxMode.STANDARD) == Risk.HIGH
    }

    println("\n== 命令守卫")
    fun expectBlocked(cmd: String) = CommandGuard.check(cmd) is CommandVerdict.Blocked
    fun expectConfirm(cmd: String) = CommandGuard.check(cmd) is CommandVerdict.Confirm
    fun expectSafe(cmd: String) = CommandGuard.check(cmd) is CommandVerdict.Safe

    ok("format C: 拦截") { expectBlocked("format c: /q") }
    ok("diskpart 拦截") { expectBlocked("diskpart /s list.txt") }
    ok("rd /s /q C:\\ 拦截") { expectBlocked("rd /s /q C:\\") }
    ok("shutdown 拦截") { expectBlocked("shutdown -s -t 0") }
    ok("reg delete HKLM 拦截") { expectBlocked("reg delete HKLM\\SOFTWARE\\x /f") }
    ok("cmd 删文件要确认") { expectConfirm("del /f old.log") }
    ok("git push 要确认") { expectConfirm("git push origin main") }
    ok("npm install 要确认") { expectConfirm("npm install") }
    ok("curl 下载要确认") { expectConfirm("curl https://example.com/x.zip") }
    ok("git status 直接放行") { expectSafe("git status") }
    ok("dir 直接放行") { expectSafe("dir /b") }
    ok("gradle build 要确认") { expectConfirm("gradle build") }

    root.deleteRecursively()
    println("\n结果：$pass 通过 / $fail 失败")
    if (fail > 0) {
        throw IllegalStateException("沙箱自检有 $fail 项未通过")
    }
}

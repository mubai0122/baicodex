/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27 01:28:54
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * 桌面端插件：一个目录 + plugin.json 就是一个插件，放 <appDir>/plugins/<id>。
 *  - prompt：启用后追加到系统提示词（技能包/规范包都靠它）；
 *  - tools ：声明式命令工具，模型调用时把参数塞进 PLUGIN_INPUT，在工作区里执行；
 *  - 安装来源：本地目录、zip 直链、git 仓库；卸载只删 plugins 目录下的东西。
 * 没有内置市场数据：广场读用户自己填的索引地址，拉不到就说拉不到。
 */

package b.ai.desktop.core.plugin

import b.ai.desktop.core.config.ConfigStore
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 插件声明的一条命令工具 */
@Serializable
data class PluginCommand(
    val name: String = "",
    val description: String = "",
    /** shell 命令模板；模型给的参数放在环境变量 PLUGIN_INPUT 里 */
    val command: String = "",
)

/** plugin.json 的结构（未知字段忽略，方便以后扩） */
@Serializable
data class PluginManifest(
    val id: String = "",
    val name: String = "",
    val version: String = "0.1.0",
    val author: String = "",
    val description: String = "",
    val prompt: String = "",
    val tools: List<PluginCommand> = emptyList(),
)

/** 扫描结果：manifest 读坏了也留在列表里，带上原因，方便用户发现并删掉 */
data class PluginInfo(
    val dir: File,
    val manifest: PluginManifest,
    val broken: String? = null,
) {
    val id: String get() = manifest.id.ifBlank { dir.name }
    val title: String get() = manifest.name.ifBlank { id }
    val version: String get() = manifest.version
    val usable: Boolean get() = broken == null && manifest.id.isNotBlank()
}

object PluginStore {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun root(): File = File(ConfigStore.appDir(), "plugins").apply { mkdirs() }

    /** 读全部插件（按目录名排序，稳定） */
    fun list(): List<PluginInfo> {
        val dirs = root().listFiles { f -> f.isDirectory }?.sortedBy { it.name.lowercase() }.orEmpty()
        return dirs.map { dir -> read(dir) }
    }

    fun read(dir: File): PluginInfo {
        val manifestFile = File(dir, "plugin.json")
        if (!manifestFile.exists()) {
            return PluginInfo(dir, PluginManifest(id = dir.name, name = dir.name), "缺少 plugin.json")
        }
        return runCatching {
            val manifest = json.decodeFromString<PluginManifest>(manifestFile.readText())
            val broken = when {
                manifest.id.isBlank() -> "plugin.json 里没有 id"
                !manifest.id.matches(Regex("[a-zA-Z0-9._-]{2,40}")) -> "id 只能用字母数字和 . _ -（2~40 位）"
                else -> null
            }
            PluginInfo(dir, manifest.copy(id = manifest.id.ifBlank { dir.name }), broken)
        }.getOrElse { error ->
            PluginInfo(dir, PluginManifest(id = dir.name, name = dir.name), "plugin.json 解析失败：${error.message}")
        }
    }

    /**
     * 从目录安装（目录里必须有 plugin.json）。
     * 同 id 已存在时先删旧的再拷，调用方负责二次确认。
     */
    fun installFromDir(source: File, overwrite: Boolean = true): Result<PluginInfo> = runCatching {
        if (!source.isDirectory) throw IOException("不是目录：${source.absolutePath}")
        val manifestFile = File(source, "plugin.json")
        if (!manifestFile.exists()) throw IOException("这个目录里没有 plugin.json")
        val manifest = json.decodeFromString<PluginManifest>(manifestFile.readText())
        if (!manifest.id.matches(Regex("[a-zA-Z0-9._-]{2,40}"))) {
            throw IOException("plugin.json 的 id 不合法（字母数字和 . _ -，2~40 位）")
        }
        val target = File(root(), manifest.id)
        if (target.exists()) {
            if (!overwrite) throw IOException("插件 ${manifest.id} 已经装过")
            if (!target.deleteRecursively()) throw IOException("旧版本删不掉：${target.absolutePath}")
        }
        source.copyRecursively(target, overwrite = true)
        read(target)
    }

    /** zip 直链安装：解压后找 plugin.json（支持外面套一层目录） */
    fun installFromZip(url: String): Result<PluginInfo> = runCatching {
        val temp = File.createTempFile("baicodex-plugin-", ".zip")
        val dir = File.createTempFile("baicodex-plugin-", "").let { it.delete(); it.apply { mkdirs() } }
        try {
            download(url, temp)
            unzip(temp, dir)
            val manifestDir = findManifestDir(dir)
                ?: throw IOException("压缩包里没找到 plugin.json")
            installFromDir(manifestDir).getOrElse { throw it }
        } finally {
            runCatching { temp.delete() }
            runCatching { dir.deleteRecursively() }
        }
    }

    /** git 仓库安装：本机没装 git 就直说，不做假动作 */
    fun installFromGit(url: String): Result<PluginInfo> = runCatching {
        val dir = File.createTempFile("baicodex-git-", "")
            .let { it.delete(); it.apply { mkdirs() } }
        try {
            val builder = ProcessBuilder("git", "clone", "--depth", "1", url, dir.absolutePath)
                .redirectErrorStream(true)
            val process = runCatching { builder.start() }
                .getOrElse { throw IOException("本机没有可用的 git，命令行里先装 git 或改用 zip 地址") }
            val output = process.inputStream.bufferedReader().use { it.readText() }
            if (!process.waitFor(120, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly()
                throw IOException("git clone 超时")
            }
            if (process.exitValue() != 0) {
                throw IOException("git clone 失败：${output.take(300)}")
            }
            val manifestDir = findManifestDir(dir) ?: throw IOException("仓库里没找到 plugin.json")
            installFromDir(manifestDir).getOrElse { throw it }
        } finally {
            runCatching { dir.deleteRecursively() }
        }
    }

    fun uninstall(id: String): Result<Unit> = runCatching {
        val dir = File(root(), id).canonicalFile
        val base = root().canonicalFile
        if (!dir.path.startsWith(base.path + File.separator)) throw IOException("路径越界，拒绝删除")
        if (!dir.exists()) throw IOException("插件不存在：$id")
        if (!dir.deleteRecursively()) throw IOException("删除失败：${dir.absolutePath}")
    }

    /** 生成骨架插件：plugin.json + README，装完即可在列表里开关 */
    fun create(id: String, name: String, description: String, prompt: String): Result<PluginInfo> = runCatching {
        val cleanId = id.trim()
        if (!cleanId.matches(Regex("[a-zA-Z0-9._-]{2,40}"))) {
            throw IOException("id 只能用字母数字和 . _ -（2~40 位）")
        }
        val target = File(root(), cleanId)
        if (target.exists()) throw IOException("插件 $cleanId 已经存在")
        target.mkdirs()
        val manifest = PluginManifest(
            id = cleanId,
            name = name.trim().ifBlank { cleanId },
            version = "0.1.0",
            author = "",
            description = description.trim().ifBlank { "本地创建的插件" },
            prompt = prompt.trim(),
            tools = listOf(
                PluginCommand(
                    name = "p_${cleanId.replace('-', '_').replace('.', '_')}",
                    description = "把输入原样回显，用来验证插件工具已经接上",
                    command = if (System.getProperty("os.name").lowercase().contains("win")) {
                        "echo %PLUGIN_INPUT%"
                    } else {
                        "echo \"${'$'}PLUGIN_INPUT\""
                    },
                ),
            ),
        )
        File(target, "plugin.json").writeText(json.encodeToString(PluginManifest.serializer(), manifest))
        File(target, "README.md").writeText(
            "# ${manifest.name}\n\n" +
                "把 `plugin.json` 里的 `prompt` 换成这个插件要追加给模型的说明；\n" +
                "`tools` 是声明式命令工具：`command` 里用环境变量 `PLUGIN_INPUT` 取模型传的参数。\n",
        )
        read(target)
    }

    /** 读插件目录里声明的工具，转成运行期对象（工作区越界保护由 Workspace 负责） */
    fun tools(
        info: PluginInfo,
        workspace: b.ai.desktop.core.tool.Workspace,
        timeoutSec: Int,
    ): List<PluginTool> = info.manifest.tools
        .filter { it.name.isNotBlank() && it.command.isNotBlank() }
        .map { PluginTool(info, it, workspace, timeoutSec) }

    // ------------------------------------------------------------ 内部

    private fun findManifestDir(dir: File): File? {
        if (File(dir, "plugin.json").exists()) return dir
        val children = dir.listFiles { f -> f.isDirectory }.orEmpty()
        return children.firstOrNull { File(it, "plugin.json").exists() }
    }

    private fun download(url: String, target: File) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Baicodex-Windows/0.1")
        }
        try {
            if (conn.responseCode !in 200..299) throw IOException("下载失败：HTTP ${conn.responseCode}")
            conn.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
        } finally {
            conn.disconnect()
        }
    }

    /** 解压并挡掉 ../ 越界条目 */
    private fun unzip(zip: File, target: File) {
        val base = target.canonicalFile
        ZipInputStream(zip.inputStream().buffered()).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                val out = File(base, entry.name).canonicalFile
                if (!out.path.startsWith(base.path + File.separator) && out != base) {
                    throw IOException("压缩包含越界路径：${entry.name}")
                }
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { input.copyTo(it) }
                }
                input.closeEntry()
            }
        }
    }
}

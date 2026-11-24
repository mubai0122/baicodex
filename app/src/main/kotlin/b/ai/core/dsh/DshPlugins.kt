/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Repository: https://github.com/mubai0122/Baicodex
 *
 * 本文件作用：读取 dsh profile 里真实安装的插件清单，并提供按 id 开关（写 cordis.patch.yml）。
 *
 * 事实来源（都是源码取证，不是猜的）：
 *  - 插件清单不在任何 SDK 方法里（协议只有 initialize、session.*、shutdown），
 *    而是 profile 的 package.json → dsh.profile.bundles → 各 bundle 的 cordis.patch.yml，
 *    每行形如 { id, name, config, disabled, inject }；
 *  - 用户开关层是 profile 目录下的 cordis.patch.yml（按 id 定向覆盖，最后写入者生效）；
 *  - `dsh plugin` 只是 pnpm 包管理包装，不是插件注册表，所以不走它。
 *
 * 写盘必须走 Root/su：rootfs 里的 /root 目录属主是 root，App 身份写不进去。
 */
package b.ai.core.dsh

import b.ai.device.shell.local.Linux
import b.ai.device.shell.root.Root
import java.io.File

/**
 * 一条真实插件装载行（cordis loader row）。
 * @param id loader 行 id，开关就是按它定位
 * @param name 包名，如 @deepseek-ai/dsh-tool-bash
 * @param bundle 这一行来自哪个 bundle（或 user = 用户 patch 层）
 * @param disabled 当前是否禁用
 * @param condition 非空 = 该行 disabled 是 `!!js` 条件表达式（平台/上下文相关），此处不做求值
 */
data class DshPlugin(
    val id: String,
    val name: String,
    val bundle: String,
    val disabled: Boolean,
    val condition: String? = null
) {
    /** 去掉 scope 的短包名：@deepseek-ai/dsh-tool-bash → dsh-tool-bash */
    val short: String get() = if (name.startsWith("@")) name.substringAfter("/", name) else name

    /** 分类：只用于列表分组与默认筛选 */
    val kind: String get() = when {
        short.startsWith("dsh-tool-") || short == "dsh-tools" -> "tool"      //工具
        short.startsWith("dsh-skill") -> "skill"                            //技能
        short.startsWith("dsh-mcp") -> "mcp"                                //MCP
        short.startsWith("dsh-web") or short.contains("web-") -> "web"       //联网
        short.startsWith("dsh-command") || short == "dsh-commands" -> "cmd"  //斜杠命令
        short.contains("office") -> "office"                                //办公
        short.startsWith("dsh-subagent") || short.contains("agent") -> "agent" //Agent/子代理
        else -> "core"                                                       //内部服务
    }

    /** true = 能力类插件（广场默认只显示这些，内部服务收进「高级」） */
    val capable: Boolean get() = kind != "core"
} //插件行

/**
 * dsh 插件清单读写。
 * 解析器是手写的行式状态机：这些 patch 文件格式极规整（只有 id/name/disabled/config 四个键），
 * 用 js-yaml 需要跑一次 proot+node（几秒），这里直接读文件，毫秒级且可离线。
 */
object DshPlugins {

    /** 当前使用的 dsh profile：SDK 无界面运行时 */
    const val PROFILE = "sdk"

    /** proot 内 dsh home 与 bundle 根 */
    private const val DSH_HOME = "/root/.dsh"
    private const val BUNDLE_ROOT = "/opt/node24/lib/node_modules/@deepseek-ai"
    /** 包也可能只装在 dsh 自己的 node_modules 下（hoisted 布局），按顺序回落 */
    private const val BUNDLE_ROOT_ALT =
        "/opt/node24/lib/node_modules/@deepseek-ai/dsh/node_modules/@deepseek-ai"

    /** profile 的宿主真实目录（写盘用） */
    private fun profileDirAbs(): File = Linux.inside("$DSH_HOME/profiles/$PROFILE")

    /** 用户开关层文件（宿主真实路径） */
    fun patchFile(): File = File(profileDirAbs(), "cordis.patch.yml")

    /** 环境就位判断：profile 目录里得有 package.json，否则说明 dsh 还没初始化过 */
    fun ready(): Boolean = DshFile.isFile(File(profileDirAbs(), "package.json"))

    /**
     * 读真实插件清单：bundles 顺序展开，再叠用户 patch 层（同 id 后者覆盖，与 dsh loader 的 last-write-wins 一致）。
     * @return 失败时 Result.failure 带原因，UI 直接显示
     */
    fun list(): Result<List<DshPlugin>> = runCatching {
        val dir = profileDirAbs()
        val pkgFile = File(dir, "package.json")
        //pnpm 写出来的 package.json 是 600/root，App 身份读不到，统一走 DshFile（带 Root 兜底）
        val pkgText = DshFile.readText(pkgFile)
            ?: throw IllegalStateException("读不到 $DSH_HOME/profiles/$PROFILE/package.json（权限或文件缺失）")

        //1. 从 package.json 里取 bundle 列表：dsh.profile.bundles = ["@deepseek-ai/dsh-base", ...]
        val bundles = parseBundles(pkgText)
        if (bundles.isEmpty()) throw IllegalStateException("profile 里没有 dsh.profile.bundles，无法展开插件")

        //2. 依次把每个 bundle 的 cordis.patch.yml 展开成行
        val rows = LinkedHashMap<String, DshPlugin>()
        bundles.forEach { full ->
            val shortName = full.substringAfter("/", full)
            val text = readPatchOfBundle(shortName)
            if (text == null) return@forEach //该 bundle 没有 patch 层，跳过
            parsePatch(text, shortName, rows)
        }

        //3. 叠用户 patch 层（我们自己写的那一层）
        DshFile.readText(patchFile())?.let { parsePatch(it, "user", rows) }

        rows.values.toList()
    } //读清单

    /**
     * 开关一个插件：只动 cordis.patch.yml 里的 disabled，不碰 config。
     * 走 Root/su 写（rootfs 的 /root 属主是 root，App 身份没有写权限）。
     * @param enabled true = 启用（删掉禁用条目），false = 禁用（写 disabled: true）
     * @return 成功与否 + 人话说明
     */
    fun setEnabled(id: String, enabled: Boolean): Result<Unit> =
        if (id.isBlank()) Result.failure(IllegalArgumentException("插件 id 为空"))
        else DshPatch.setEnabled(patchFile(), id, enabled) //开关插件（实现见 DshPatch）

    /** 卸载一条通过插件页安装的插件（只摘装载行，不删 node_modules，卸载完成后按需清理） */
    fun uninstall(id: String): Result<Unit> =
        if (id.isBlank()) Result.failure(IllegalArgumentException("插件 id 为空"))
        else DshPatch.removeInsert(patchFile(), id) //摘掉 insert 行

    //region 解析

    /** 从 package.json 取 dsh.profile.bundles */
    private fun parseBundles(json: String): List<String> {
        val key = "\"bundles\""
        val at = json.indexOf(key)
        if (at < 0) return emptyList()
        val open = json.indexOf('[', at)
        val close = json.indexOf(']', open + 1)
        if (open < 0 || close < 0) return emptyList()
        return json.substring(open + 1, close)
            .split(',')
            .map { it.trim().trim('"').trim() }
            .filter { it.isNotBlank() }
    } //取 bundle 列表

    /** 读某个 bundle 的 cordis.patch.yml（两个可能的安装位置都要试） */
    private fun readPatchOfBundle(shortName: String): String? {
        listOf("$BUNDLE_ROOT/$shortName/cordis.patch.yml", "$BUNDLE_ROOT_ALT/$shortName/cordis.patch.yml")
            .forEach { rel ->
                val f = Linux.inside(rel)
                if (DshFile.isFile(f)) return DshFile.readText(f)
            }
        return null
    } //读 bundle patch

    /**
     * 行式解析一个 patch 文件，把行并进 rows（同 id 覆盖 disabled/name，config 忽略）。
     * 支持两种条目：`- id: x` （定向覆盖既有行）与 `- insert:` 下的 `- id: x` （插入新行）。
     */
    private fun parsePatch(text: String, bundle: String, rows: LinkedHashMap<String, DshPlugin>) {
        var id: String? = null
        var name: String? = null
        var disabled: Boolean? = null
        var cond: String? = null

        fun flush() {
            val pid = id ?: return
            val prev = rows[pid]
            rows[pid] = DshPlugin(
                id = pid,
                name = name ?: prev?.name ?: "",
                bundle = prev?.bundle ?: bundle,
                disabled = disabled ?: prev?.disabled ?: false,
                condition = cond ?: prev?.condition
            )
            id = null
            name = null
            disabled = null
            cond = null
        } //提交一行

        text.lineSequence().forEach { raw ->
            val t = raw.trim()
            if (t.isEmpty() || t.startsWith("#")) return@forEach
            if (t.startsWith("- ")) {
                //顶层新条目：先提交上一条
                flush()
                val body = t.removePrefix("- ").trim()
                if (body.startsWith("id:")) id = unquote(body.removePrefix("id:").trim())
                //`- insert:` 只是标记，其下的 `- id:` 会各自成为新条目，无需特殊处理
                return@forEach
            }
            //深层键：属于当前条目
            when {
                t.startsWith("id:") && id == null -> id = unquote(t.removePrefix("id:").trim())
                t.startsWith("name:") -> name = unquote(t.removePrefix("name:").trim())
                t.startsWith("disabled:") -> {
                    val v = t.removePrefix("disabled:").trim()
                    if (v.startsWith("!!js")) {
                        //条件禁用：真实取值由运行时求值，这里只记表达式，不给结论
                        cond = unquote(v.removePrefix("!!js").trim())
                        disabled = false
                    } else {
                        disabled = v.startsWith("true")
                    }
                }
                else -> Unit //config / inject 等字段广场不关心
            }
        }
        flush()
    } //解析 patch

    /** 去 YAML 引号（单引号、双引号都处理） */
    private fun unquote(s: String): String =
        s.removeSurrounding("'").removeSurrounding("\"").trim()

    //endregion
} //dsh 插件清单
/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Repository: https://github.com/mubai0122/Baicodex
 *
 * 本文件作用：dsh 插件的安装 / 卸载 / 本地骨架插件创建。
 *
 * 事实来源（源码取证，别改成"看起来更合理"的写法）：
 *  - 官方安装通道就是 `dsh plugin --profile <name> <pnpm-args>`，它把参数原样转发给 pnpm
 *    （dsh/lib/bin-*.js 里 plugin 子命令的说明：forwarding the remaining arguments to pnpm）。
 *  - pnpm 缺失时 dsh 会报 exitCode 127 和 "pnpm was not found"，所以安装前必须自己确保 pnpm。
 *    rootfs 里只有 corepack 垫片、没有 pnpm；实测 `npm install -g pnpm@9` 11 秒装好。
 *  - profile 的 pnpm-workspace.yaml 里有 `packages: [.]`，直接 `pnpm add` 会报
 *    ERR_PNPM_ADDING_TO_ROOT，必须带 -w（--workspace-root）。实测确认。
 *  - 安装后的激活由 dsh 自己做：plugin-manager 的 reconcile 会把「package.json 里声明了
 *    dsh.bundle.patch 的新依赖」自动加进 profile 的 dsh.profile.bundles，
 *    所以 App 侧不需要手写 cordis.patch.yml 的 insert 行（写了反而会和它抢）。
 *  - 本地插件同样可用：parseInstallSpec 接受绝对路径（kind: "path"），
 *    用 file:<绝对路径> 安装即可，pnpm 会做 link 投影。
 *
 * 全部命令走 [Linux.run]（内部会自动从 App 身份退回 Root 通道）。
 */
package b.ai.core.dsh

import b.ai.device.shell.local.Linux
import b.ai.device.shell.local.ShellResult
import b.ai.device.shell.root.Root
import java.io.File

/** 安装 / 卸载的最终结果：ok + 给用户看的日志尾部 */
data class InstallOutcome(
    val ok: Boolean,
    val message: String,
    /** 安装成功时登记的包名（卸载 / 展示用） */
    val pkg: String = ""
) //安装结果

object DshInstall {

    /** guest 内 node 与 dsh 入口（rootfs 绝对路径） */
    private const val NODE = "/opt/node24/bin/node"
    private const val DSH_BIN = "/opt/node24/lib/node_modules/@deepseek-ai/dsh/lib/bin.js"
    /** profile 目录（guest 路径） */
    private const val PROFILE_DIR = "/root/.dsh/profiles/sdk"
    /** 本地自建插件的存放根（guest 路径） */
    private const val LOCAL_ROOT = "/root/.dsh/local-plugins"

    /**
     * 命令前缀：Linux.run 内部是 `env -i PATH=/usr/local/sbin:...:/bin`，
     * 里面没有 /opt/node24/bin，所以 pnpm / node 都找不到（这正是"pnpm 安装失败"误报的根因）。
     * 所有命令都显式把 Node 24 的 bin 放到 PATH 最前面。
     */
    private const val PATH_PREFIX =
        "PATH=/opt/node24/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin; export PATH; "

    /** pnpm 装/查一次就够，装过之后每次只花一次 command -v */
    @Volatile
    private var pnpmReady = false

    /** add 的进度总步数（准备 pnpm / 安装依赖 / 回滚 / 收尾），UI 侧算百分比用 */
    private const val ADD_STEPS = 3
    /** create 的进度总步数（写骨架 / 安装依赖），比 add 多一步 */
    private const val CREATE_STEPS = 4

    /**
     * 确保 pnpm 可用。
     * rootfs 里没有独立 pnpm，只有 corepack 垫片；corepack 首次使用要联网下载，
     * 实测直接用 npm 全局装 pnpm@9 最快也最可控。
     */
    fun ensurePnpm(): Result<Unit> = runCatching {
        if (pnpmReady) return@runCatching
        //必须在 PATH_PREFIX 下判：pnpm 在 /opt/node24/bin，默认 PATH 里没有
        val has = Linux.run(PATH_PREFIX + "command -v pnpm || echo NOPNPM", 20_000L)
        if (!has.out.contains("NOPNPM")) {
            pnpmReady = true
            return@runCatching
        }
        val r = Linux.run(
            PATH_PREFIX + "npm install -g pnpm@9 --no-fund --no-audit 2>&1 | tail -3; command -v pnpm || echo NOPNPM",
            300_000L
        )
        if (r.out.contains("NOPNPM")) {
            throw IllegalStateException("pnpm 安装失败：${r.out.take(300)}")
        }
        pnpmReady = true
    } //确保 pnpm

    /**
     * 安装一个插件。
     * @param spec npm 包名（可带 @version）、git spec、或 file:/绝对路径
     * @param onLog 进度回调（UI 直接 append 到日志框）
     * @param onStep 阶段回调（step 已完成的步数 / total 总步数 / label 当前做什么），UI 据此画进度条
     * @return 成功/失败 + 日志尾部；失败时已做过一次 remove 回滚
     */
    fun add(
        spec: String,
        onLog: (String) -> Unit = {},
        onStep: (step: Int, total: Int, label: String) -> Unit = { _, _, _ -> }
    ): InstallOutcome {
        val target = spec.trim()
        if (target.isBlank()) return InstallOutcome(false, "安装目标为空")
        //0. pnpm 就位（首次可能要联网装 pnpm，耗时不可控，先报出来）
        onStep(0, ADD_STEPS, "准备 pnpm")
        ensurePnpm().onFailure {
            onStep(ADD_STEPS, ADD_STEPS, "pnpm 不可用")
            return InstallOutcome(false, it.message ?: "pnpm 不可用")
        }
        onLog("pnpm 就绪，开始安装 $target")

        //1. 本条命令失败时 dsh 自己不改 package.json（实测：ERR_PNPM_FETCH_404 后 dependencies 仍为空），
        //   但为了兜住 pnpm 部分写入的极端情况，失败后仍跑一次 remove
        onStep(1, ADD_STEPS, "安装依赖：$target")
        val r = runPlugin("add", target, 600_000L)
        onLog(r.out.takeLast(1200))
        if (r.code == 0) {
            onStep(ADD_STEPS, ADD_STEPS, "安装完成")
            val id = shortId(target)
            return InstallOutcome(true, "已安装：$target", id)
        }

        //2. 回滚：把可能半装的依赖摘掉（失败也继续，只记录）
        onStep(2, ADD_STEPS, "安装失败，回滚依赖")
        onLog("安装失败（exit=${r.code}），开始回滚…")
        val back = runPlugin("remove", target, 300_000L)
        onLog("回滚结果 exit=${back.code}")
        onStep(ADD_STEPS, ADD_STEPS, "已回滚")
        val pkgName = pkgNameOf(target)
        val hint = when {
            r.out.contains("ERR_PNPM_FETCH_404") -> "依赖里有未公开发布的包（常见于依赖 @deepseek-ai 内部包的社区插件）"
            r.out.contains("ERR_PNPM_ADDING_TO_ROOT") -> "工作区根校验（应带 -w，属程序 bug）"
            r.out.contains("EAI_AGAIN") || r.out.contains("ETIMEDOUT") -> "网络超时"
            else -> "原因见上面的安装日志"
        }
        return InstallOutcome(false, "安装失败：$hint（包：$pkgName）")
    } //安装

    /** 卸载一个已装插件（按包名） */
    fun remove(pkg: String, onLog: (String) -> Unit = {}): InstallOutcome {
        val name = pkgNameOf(pkg).ifBlank { return InstallOutcome(false, "包名为空") }
        ensurePnpm().onFailure { return InstallOutcome(false, it.message ?: "pnpm 不可用") }
        val r = runPlugin("remove", name, 300_000L)
        onLog(r.out.takeLast(1200))
        return if (r.code == 0) InstallOutcome(true, "已卸载：$name", name)
        else InstallOutcome(false, "卸载失败（exit=${r.code}），原因见日志")
    } //卸载

    /**
     * 创建本地骨架插件并安装。
     * 生成的是「能被 dsh 装载的空插件」：有 name + apply，apply 里什么都不做。
     * 工具注册需要 @deepseek-ai/dsh-tools 的 defineTool（内部包，未公开发布），
     * 所以骨架里只给注释示例，具体实现由用户自己补——不生成跑不起来的假工具。
     * @param id 插件标识（loader 行 id，字母数字点横线）
     * @param title 展示名，写进 description
     * @param pkgName npm 包名，空则由 id 推导成 bxl-plugin-<id>
     * @param onLog 进度回调
     * @param onStep 阶段回调（写骨架 / 安装依赖，步数折算成 CREATE_STEPS 段）
     */
    fun create(
        id: String,
        title: String,
        pkgName: String = "",
        onLog: (String) -> Unit = {},
        onStep: (step: Int, total: Int, label: String) -> Unit = { _, _, _ -> }
    ): Result<String> = runCatching {
        val pid = id.trim().replace(Regex("[^a-zA-Z0-9._-]"), "-").trim('-')
        if (pid.isBlank()) throw IllegalArgumentException("插件 id 不能为空")
        val pkg = pkgName.trim().ifBlank { "bxl-plugin-${pid.lowercase()}" }
        if (!Regex("^(?:@[a-z0-9][a-z0-9._~-]*/)?[a-z0-9][a-z0-9._~-]*$").matches(pkg)) {
            throw IllegalArgumentException("包名不合法（要小写 npm 名）：$pkg")
        }
        val dirAbs = Linux.inside("$LOCAL_ROOT/$pid")
        if (File(dirAbs, "package.json").isFile) throw IllegalStateException("已存在同名插件：$LOCAL_ROOT/$pid")
        onStep(0, CREATE_STEPS, "写入骨架文件")

        val desc = title.trim().ifBlank { pid }
        val manifest = """
            {
              "name": "$pkg",
              "version": "0.0.1",
              "description": "$desc",
              "type": "module",
              "main": "./index.js",
              "license": "MIT",
              "dsh": {
                "bundle": {
                  "patch": "./cordis.patch.yml"
                }
              }
            }

        """.trimIndent() //package.json：dsh.bundle.patch 是 bundle 的唯一必要条件

        val patch = """
            # 本插件插入的 loader 行：id 是开关单位，name 必须等于本包包名
            - insert:
                - id: $pid
                  name: '$pkg'

        """.trimIndent()

        val index = """
            // 由 Baicodex 生成的 dsh 插件骨架（cordis 插件形态：导出 name / inject / apply）
            // 工具注册需要 @deepseek-ai/dsh-tools 的 defineTool（dsh 内部包，npm 上取不到），
            // 所以这里只留位置：真实实现请参考 rootfs 里的 @deepseek-ai/dsh-tool-bash/src/index.ts
            export const name = '$pid'

            // 依赖的 cordis 服务：注册工具需要 'tools'
            export const inject = []

            export function apply(ctx) {
              ctx.logger?.info?.('[$pid] loaded')
            }

        """.trimIndent()

        writeFile(File(dirAbs, "package.json"), manifest)
        writeFile(File(dirAbs, "cordis.patch.yml"), patch)
        writeFile(File(dirAbs, "index.js"), index)
        onLog("已写入骨架：$LOCAL_ROOT/$pid")

        //把这批文件交给 root（proot -0 里是 root 身份读，属主不是 root 也能读，但保持与其他 profile 文件一致）
        Root.run("chown -R root:root '${dirAbs.absolutePath}'", 20_000L)

        //安装用 guest 内的绝对路径（pnpm 在 proot 里跑，看不到宿主路径）
        //add 的步数（0..3）折算成 create 的 1..4 段，进度条从「写文件」无缝接到「装依赖」
        val r = add("file:$LOCAL_ROOT/$pid", onLog) { s, _, label ->
            onStep((1 + s).coerceAtMost(CREATE_STEPS), CREATE_STEPS, label)
        }
        if (!r.ok) throw IllegalStateException(r.message)
        "$pkg（$LOCAL_ROOT/$pid）"
    } //创建本地插件

    //region 内部

    /** 转发一条 pnpm 子命令到 dsh plugin（-w 是必须的，见文件头说明） */
    private fun runPlugin(sub: String, target: String, timeoutMs: Long): ShellResult {
        val cmd = PATH_PREFIX +
            "cd $PROFILE_DIR && $NODE $DSH_BIN plugin --profile sdk $sub -w ${q(target)} 2>&1; RC=\$?; " +
            fixPermsCmd() + "; exit \$RC"   //先记原始退出码，再做权限修复，最后原样退出（否则 code 恒为 0）
        return Linux.run(cmd, timeoutMs)
    } //转发 pnpm

    /**
     * 权限修复命令：pnpm 写出来的 package.json / pnpm-lock.yaml 是 600 属主 root，
     * 且 rootfs 的 /root 是 700 —— 两者都会让 App 身份读清单时 EACCES（实测踩到）。
     */
    private fun fixPermsCmd(): String =
        "chmod 755 /root /root/.dsh /root/.dsh/profiles $PROFILE_DIR 2>/dev/null; " +
            "chmod 644 $PROFILE_DIR/package.json $PROFILE_DIR/pnpm-lock.yaml $PROFILE_DIR/cordis.patch.yml 2>/dev/null; true"

    /** 写文件（走 Root：/root 属主是 root，App 身份写不进去） */
    private fun writeFile(f: File, body: String) {
        val abs = f.absolutePath
        val tmp = "$abs.bxl.tmp"
        val cmd = buildString {
            append("mkdir -p '").append(f.parentFile?.absolutePath).append("' && ")
            append("cat > '").append(tmp).append("' <<'BXL_PLUGIN_EOF'\n")
            append(body).append('\n')
            append("BXL_PLUGIN_EOF\n")
            append("mv -f '").append(tmp).append("' '").append(abs).append("'")
        }
        val r = Root.run(cmd, 20_000L)
        if (!r.ok) throw IllegalStateException("写 ${f.name} 失败：${r.out.take(200)}")
    } //写文件

    /** shell 单引号转义 */
    private fun q(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    /** 取包名（去掉 @version / file: / link: 前缀） */
    private fun pkgNameOf(spec: String): String {
        var s = spec.trim()
        if (s.startsWith("file:")) return s.removePrefix("file:")   //本地路径没有"包名"，原样返回路径
        if (s.startsWith("link:")) return s.removePrefix("link:")
        val at = s.indexOf('@', 1)
        if (at > 0 && !s.startsWith("git")) s = s.substring(0, at)
        return s
    } //取包名

    /** loader 行 id：包名去 scope 的短名（与插件自己 patch 里常用的 id 习惯一致） */
    private fun shortId(spec: String): String =
        pkgNameOf(spec).substringAfter("/").ifBlank { "plugin" }

    //endregion
} //dsh 插件安装
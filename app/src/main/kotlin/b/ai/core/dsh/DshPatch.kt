/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Repository: https://github.com/mubai0122/Baicodex
 *
 * 本文件作用：读写 dsh profile 的用户 patch 层（cordis.patch.yml）。
 * 两层内容：①insert —— 新装插件的装载行；②disabled —— 按 id 定向开关既有行。
 *
 * 依据（dsh-app-boot 源码注释原文）：该文件是「top-level YAML array of loader patch entries，
 * id-targeted config overrides, disables, and insert lists」。写入必须走 Root/su ——
 * rootfs 里的 /root 属主是 root，App 身份写不进去。
 */
package b.ai.core.dsh

import b.ai.device.shell.root.Root
import java.io.File

/** cordis.patch.yml 的可维护状态 */
data class PatchState(
    /** 新装插件：id → 包名（insert 块） */
    val inserts: LinkedHashMap<String, String> = LinkedHashMap(),
    /** 开关覆盖：id → 是否禁用 */
    val disabled: LinkedHashMap<String, Boolean> = LinkedHashMap()
) {
    /** 该 id 是否已被本层禁用 */
    fun isDisabled(id: String): Boolean = disabled[id] == true
} //patch 状态

object DshPatch {

    /**
     * 读现有 patch 文件成结构化状态。
     * 只认我们能安全重写的东西：insert 块的 (id, name)，以及带 disabled 的条目。
     * 其余 config / inject 字段不属于我们维护，重写时会丢——所以本层只由我们写。
     */
    fun read(file: File): PatchState {
        val st = PatchState()
        val text = DshFile.readText(file) ?: return st
        var id: String? = null
        var name: String? = null
        var disabled: Boolean? = null
        var inInsert = false

        fun flush() {
            val pid = id ?: return
            if (inInsert && name != null) st.inserts[pid] = name!!
            disabled?.let { st.disabled[pid] = it }
            id = null
            name = null
            disabled = null
        } //提交一条

        text.lineSequence().forEach { raw ->
            val t = raw.trim()
            if (t.isEmpty() || t.startsWith("#")) return@forEach
            if (t == "- insert:") {
                flush()
                inInsert = true
                return@forEach
            }
            if (t.startsWith("- ")) {
                //顶层新的覆盖条目（不属于 insert 块）
                flush()
                inInsert = false
                val body = t.removePrefix("- ").trim()
                if (body.startsWith("id:")) id = unquote(body.removePrefix("id:").trim())
                return@forEach
            }
            when {
                t.startsWith("id:") && id == null -> id = unquote(t.removePrefix("id:").trim())
                t.startsWith("name:") -> name = unquote(t.removePrefix("name:").trim())
                t.startsWith("disabled:") -> disabled = t.removePrefix("disabled:").trim().startsWith("true")
                else -> Unit
            }
        }
        flush()
        return st
    } //读状态

    /** 写回整层（先写临时文件再 mv，避免写一半把 dsh 弄起不来） */
    fun write(file: File, st: PatchState): Result<Unit> = runCatching {
        val sb = StringBuilder()
        sb.appendLine("# 由 Baicodex 插件页维护：dsh profile 的用户 patch 层。")
        sb.appendLine("# insert = 新装插件装载行；- id + disabled = 对既有行的定向开关。")
        sb.appendLine("# 手改也安全：loader 按 id 匹配，最后写入者生效。")
        if (st.inserts.isEmpty() && st.disabled.isEmpty()) {
            sb.appendLine("[]")
        } else {
            if (st.inserts.isNotEmpty()) {
                sb.appendLine("- insert:")
                st.inserts.forEach { (id, name) ->
                    sb.appendLine("    - id: $id")
                    sb.appendLine("      name: '$name'")
                    //新装即禁用的情况：直接在装载行上标 disabled
                    if (st.isDisabled(id)) sb.appendLine("      disabled: true")
                }
            }
            st.disabled.forEach { (id, off) ->
                if (!off) return@forEach
                if (st.inserts.containsKey(id)) return@forEach //上面已写过
                sb.appendLine("- id: $id")
                sb.appendLine("  disabled: true")
            }
        }

        val abs = file.absolutePath
        val tmp = "$abs.bxl.tmp"
        val body = sb.toString()
        val cmd = buildString {
            append("mkdir -p '").append(file.parentFile?.absolutePath).append("' && ")
            append("cat > '").append(tmp).append("' <<'BXL_PATCH_EOF'\n")
            append(body)
            append("BXL_PATCH_EOF\n")
            append("chown root:root '").append(tmp).append("' 2>/dev/null; ")
            append("chmod 644 '").append(tmp).append("' 2>/dev/null; ")
            append("mv -f '").append(tmp).append("' '").append(abs).append("'")
        }
        val r = Root.run(cmd, 20_000L)
        if (!r.ok) throw IllegalStateException("写 cordis.patch.yml 失败：${r.out.take(200)}")
    } //写状态

    /** 开/关一条既有行 */
    fun setEnabled(file: File, id: String, enabled: Boolean): Result<Unit> = runCatching {
        val st = read(file)
        if (enabled) st.disabled.remove(id) else st.disabled[id] = true
        write(file, st).getOrThrow()
    } //开关

    /** 登记一条新装插件（id 用包名去 scope 的短名，冲突时加序号） */
    fun addInsert(file: File, pkg: String): Result<String> = runCatching {
        val st = read(file)
        val base = pkg.substringAfter("/", pkg).replace(Regex("[^a-zA-Z0-9._-]"), "-")
        var id = base
        var n = 2
        while (st.inserts.containsKey(id) && st.inserts[id] != pkg) id = "$base-$n".also { n++ }
        st.inserts[id] = pkg
        write(file, st).getOrThrow()
        id
    } //登记插入

    /** 移除一条新装插件 */
    fun removeInsert(file: File, id: String): Result<Unit> = runCatching {
        val st = read(file)
        st.inserts.remove(id)
        st.disabled.remove(id)
        write(file, st).getOrThrow()
    } //移除插入

    private fun unquote(s: String): String = s.removeSurrounding("'").removeSurrounding("\"").trim()
} //patch 读写
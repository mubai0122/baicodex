package b.ai.native

import org.json.JSONArray
import org.json.JSONObject

/**
 * 内核的类型化门面。
 *
 * 这一层的职责只有一个：把 JSON 契约翻译成 Kotlin 能看懂的签名。**不含任何业务
 * 判断**——判断都在 Rust 里，这里多写一行 if 就等于把逻辑分叉到两个语言。
 *
 * 三条约定：
 *
 * 1. 所有方法都可能抛 [KernelException]；协程里用 `runCatching` 接。
 * 2. 返回 `Unit` 的方法表示"成功即可"，调用方不需要知道细节。
 * 3. 方法名与 Rust op 名不强行一致：op 名是跨语言契约（不能随便改），方法名是
 *    Kotlin 习惯（要顺手）。
 */
object Kernel {

    /** 内核版本，boot 后才有值。 */
    @Volatile
    var version: String = ""
        private set

    /** 数据根目录，boot 后才有值。 */
    @Volatile
    var base: String = ""
        private set

    private var booted = false

    /**
     * 启动内核。
     *
     * @param base 数据目录，通常是 `context.filesDir/baicode`。`""` 表示用默认
     *             （`$BAICODE_HOME` 或 `~/.baicode`）。
     */
    suspend fun boot(base: String) {
        if (booted) return
        val v = NativeBridge.call("boot", JSONObject().put("base", base))
        if (!v.optBoolean("ok", false)) {
            throw KernelException.of(v.optJSONObject("error") ?: JSONObject())
        }
        onBooted(v.optJSONObject("data") ?: JSONObject())
        booted = true
    }

    internal fun onBooted(data: JSONObject) {
        version = data.optString("version", "")
        base = data.optString("base", "")
    }

    val isBooted: Boolean get() = booted

    // —— 一轮对话 ——

    /**
     * 发一条消息，等到这一轮结束（可能包含多次工具调用）。
     *
     * 想显示流式内容就订阅 [NativeBridge.events]，不要等这个返回值。
     */
    suspend fun send(
        text: String,
        images: List<String> = emptyList(),
        files: List<String> = emptyList()
    ): JSONObject = data(
        "send",
        JSONObject()
            .put("text", text)
            .put("images", JSONArray(images))
            .put("files", JSONArray(files))
    )

    /** 重新生成最后一条回答（长出一个兄弟分支，不删旧的）。 */
    suspend fun regenerate(): JSONObject = data("regenerate")

    /** 停止当前生成。立即返回，正在跑的请求会在下一个分片处退出。 */
    suspend fun stop() = unit("stop")

    // —— 状态与历史 ——

    /** 界面刷新用的一揽子状态。 */
    suspend fun snapshot(): JSONObject = data("snapshot")

    /** 当前会话的消息列表（当前分支）。 */
    suspend fun messages(): JSONArray = array("messages")

    suspend fun sessions(): JSONArray = array("sessions")

    suspend fun newSession() = unit("new_session")

    /** 确保有当前会话（首次启动、删光会话后调）。 */
    suspend fun ensureCurrent() = unit("ensure_session")

    /** 清空当前会话的消息（保留会话本身）。 */
    suspend fun clearMessages() = unit("clear_current")

    /**
     * 编辑并重发一条用户消息：删掉它之后的全部，再用新文本跑一轮。
     *
     * 这是"改一下刚才那句话"的正确做法——不是追加一条新的，否则历史里会留下
     * 两个互相矛盾的版本。
     */
    suspend fun editResend(
        id: Long,
        text: String,
        images: List<String> = emptyList(),
        files: List<String> = emptyList()
    ): JSONObject = data(
        "edit_resend",
        JSONObject()
            .put("id", id)
            .put("text", text)
            .put("images", JSONArray(images))
            .put("files", JSONArray(files))
    )

    /** 手动压缩上下文。返回省下的字符数。 */
    suspend fun compact(): Int = num("compact")

    /** 当前挂起的交互（确认 / 提问 / 计划），没有则 kind 为空。 */
    suspend fun pending(): JSONObject = data("pending")

    suspend fun openSession(id: String) = unit("open_session", JSONObject().put("id", id))

    /** 删会话。返回"删之前是否存在"。 */
    suspend fun deleteSession(id: String): Boolean =
        data("delete_session", JSONObject().put("id", id)).optBoolean("data", false)

    suspend fun renameSession(id: String, title: String) =
        unit("rename_session", JSONObject().put("id", id).put("title", title))

    suspend fun searchSessions(q: String): List<String> =
        stringList(array("search_sessions", JSONObject().put("q", q)))

    suspend fun pinSession(id: String, on: Boolean) =
        unit("pin_session", JSONObject().put("id", id).put("on", on))

    suspend fun archiveSession(id: String, on: Boolean) =
        unit("archive_session", JSONObject().put("id", id).put("on", on))

    suspend fun starMessage(id: Long, on: Boolean) =
        unit("star_message", JSONObject().put("id", id).put("on", on))

    suspend fun deleteMessage(id: Long) =
        unit("delete_message", JSONObject().put("id", id))

    suspend fun editMessage(id: Long, text: String) =
        unit("edit_message", JSONObject().put("id", id).put("text", text))

    /** 会话统计：消息条数、token 合计。 */
    suspend fun sessionStats(id: String): Pair<Int, Long> {
        val d = data("session_stats", JSONObject().put("id", id))
        return d.optInt("messages") to d.optLong("tokens")
    }

    suspend fun setSessionPersona(id: String, persona: String) =
        unit("set_session_persona", JSONObject().put("id", id).put("persona", persona))

    suspend fun setSessionWorkspace(id: String, path: String) =
        unit("set_session_workspace", JSONObject().put("id", id).put("path", path))

    /** 同父分支的 id 列表（界面画 ‹ 2/3 ›）。 */
    suspend fun siblings(id: Long): List<Long> {
        val a = array("siblings", JSONObject().put("id", id))
        return (0 until a.length()).map { a.optLong(it) }
    }

    /** 切到某个分支。 */
    suspend fun switchBranch(id: Long) =
        unit("switch_branch", JSONObject().put("id", id))

    // —— 配置 ——

    suspend fun config(): JSONObject = data("config_get")

    /** 浅合并：只提交改动的字段。 */
    suspend fun patchConfig(patch: JSONObject): JSONObject =
        data("config_patch", JSONObject().put("patch", patch))

    suspend fun saveConfig() = unit("config_save")

    suspend fun setSystemPrompt(text: String) =
        unit("set_system_prompt", JSONObject().put("text", text))

    // —— 工具 ——

    suspend fun tools(): JSONArray = array("tools")

    /** 直接调一个工具（不经过模型）。用于设置页里的"试一试"。 */
    suspend fun callTool(name: String, args: JSONObject): JSONObject =
        data("tool_call", JSONObject().put("name", name).put("args", args))

    /** 用户对确认请求的答复。 */
    suspend fun confirmTool(allow: Boolean, permanent: Boolean = false) =
        unit("confirm_tool", JSONObject().put("allow", allow).put("permanent", permanent))

    suspend fun answerAsk(answers: List<String>) =
        unit("answer_ask", JSONObject().put("answers", JSONArray(answers)))

    /** 计划裁决：`confirm` / `revise` / 其它表示驳回。 */
    suspend fun answerPlan(verdict: String, note: String = "") =
        unit("answer_plan", JSONObject().put("verdict", verdict).put("note", note))

    // —— 记忆 ——

    suspend fun memoryList(): JSONArray = array("memory_list")

    suspend fun memorySave(text: String): Boolean =
        data("memory_save", JSONObject().put("text", text)).optBoolean("data", false)

    suspend fun memorySearch(q: String): JSONArray =
        array("memory_search", JSONObject().put("q", q))

    // —— 技能 / 技能包 ——

    suspend fun skills(): JSONObject = data("skills")

    suspend fun skillGet(name: String): JSONObject =
        data("skill_get", JSONObject().put("name", name))

    suspend fun skillDelete(name: String): Boolean =
        data("skill_delete", JSONObject().put("name", name)).optBoolean("data", false)

    suspend fun packs(): JSONArray = array("pack_list")

    suspend fun togglePack(id: String, on: Boolean): Boolean =
        data("pack_toggle", JSONObject().put("id", id).put("on", on)).optBoolean("data", false)

    // —— MCP ——

    suspend fun mcp(): JSONObject = data("mcp_list")

    suspend fun mcpAdd(name: String, url: String, token: String = "", kind: String = "streamable"): String =
        data(
            "mcp_add",
            JSONObject().put("name", name).put("url", url).put("token", token).put("kind", kind)
        ).optString("data")

    suspend fun mcpRemove(id: String): Boolean =
        data("mcp_remove", JSONObject().put("id", id)).optBoolean("data", false)

    suspend fun mcpToggle(id: String, on: Boolean): Boolean =
        data("mcp_toggle", JSONObject().put("id", id).put("on", on)).optBoolean("data", false)

    /** 重新拉取某个服务的能力表。返回能力条数。 */
    suspend fun mcpRefresh(id: String): Int =
        data("mcp_refresh", JSONObject().put("id", id)).optInt("data", 0)

    // —— 工作流 ——

    suspend fun flows(): JSONArray = array("flows")

    suspend fun saveFlow(flow: JSONObject): String =
        data("flow_save", JSONObject().put("patch", flow)).optString("data")

    suspend fun deleteFlow(id: String): Boolean =
        data("flow_delete", JSONObject().put("id", id)).optBoolean("data", false)

    // —— 用量 ——

    suspend fun usage(): JSONObject = data("usage")

    /** 最近 14 天的用量。 */
    suspend fun usageDays(): JSONObject = data("usage_days")

    // —— 人设 ——

    suspend fun personas(): JSONObject = data("personas")

    suspend fun savePersona(p: JSONObject): String =
        data("persona_save", JSONObject().put("patch", p)).optString("data")

    suspend fun deletePersona(id: String): Boolean =
        data("persona_delete", JSONObject().put("id", id)).optBoolean("data", false)

    // —— 配置档与供应商 ——

    suspend fun profiles(): JSONObject = data("profiles")

    suspend fun saveProfile(p: JSONObject): String =
        data("profile_save", JSONObject().put("patch", p)).optString("data")

    suspend fun useProfile(id: String) = unit("profile_use", JSONObject().put("id", id))

    suspend fun deleteProfile(id: String): Boolean =
        data("profile_delete", JSONObject().put("id", id)).optBoolean("data", false)

    suspend fun providers(): JSONArray = array("providers")

    suspend fun saveProvider(p: JSONObject): String =
        data("provider_save", JSONObject().put("patch", p)).optString("data")

    suspend fun deleteProvider(id: String): Boolean =
        data("provider_delete", JSONObject().put("id", id)).optBoolean("data", false)

    // —— 凭据（只出掩码）——

    suspend fun vaultKeys(bucket: String): JSONArray =
        array("vault_keys", JSONObject().put("bucket", bucket))

    suspend fun vaultPut(bucket: String, key: String, value: String) =
        unit("vault_put", JSONObject().put("bucket", bucket).put("key", key).put("value", value))

    suspend fun vaultDelete(bucket: String, key: String) =
        unit("vault_delete", JSONObject().put("bucket", bucket).put("key", key))

    // —— 文件（界面浏览用）——

    suspend fun fsList(path: String, recurse: Boolean = false, limit: Int = 200): JSONObject =
        data("fs_list", JSONObject().put("path", path).put("recurse", recurse).put("limit", limit))

    suspend fun fsRead(path: String): JSONObject = data("fs_read", JSONObject().put("path", path))

    suspend fun fsWrite(path: String, content: String): Boolean =
        data("fs_write", JSONObject().put("path", path).put("content", content))
            .optBoolean("ok", false)

    suspend fun fsDelete(path: String): Boolean =
        data("fs_delete", JSONObject().put("path", path)).optBoolean("ok", false)

    suspend fun fsInfo(path: String): JSONObject = data("fs_info", JSONObject().put("path", path))

    suspend fun fsSearch(
        path: String,
        pattern: String,
        ext: String = "",
        max: Int = 100,
        ignoreCase: Boolean = true
    ): JSONObject = data(
        "fs_search",
        JSONObject()
            .put("path", path)
            .put("pattern", pattern)
            .put("ext", ext)
            .put("max", max)
            .put("ignore_case", ignoreCase)
    )

    // —— 设备 ——

    suspend fun device(): JSONObject = data("device")

    suspend fun clipboard(): String = data("clipboard").optString("data")

    suspend fun setClipboard(text: String): String =
        data("clipboard", JSONObject().put("action", "set").put("text", text)).optString("data")

    suspend fun notify(title: String, body: String): String =
        data("notify", JSONObject().put("title", title).put("body", body)).optString("data")

    suspend fun apk(action: String, path: String, keyword: String = "", limit: Int = 60): JSONObject =
        data(
            "apk_info",
            JSONObject().put("action", action).put("path", path)
                .put("keyword", keyword).put("limit", limit)
        )

    // —— 插件 ——

    suspend fun plugins(): JSONArray = array("plugin_list")

    suspend fun installPlugin(zipPath: String, id: String): Int =
        data("plugin_install", JSONObject().put("path", zipPath).put("id", id)).optInt("data", 0)

    suspend fun removePlugin(id: String): Boolean =
        data("plugin_remove", JSONObject().put("id", id)).optBoolean("data", false)

    /** 备份数据库与配置到 `base/backup/*.zip`，返回文件路径。 */
    suspend fun backup(): String = data("backup").optString("data")

    /** 收藏夹。 */
    suspend fun starred(limit: Int = 300): JSONArray =
        array("starred", JSONObject().put("limit", limit))

    /** 某个会话的用量（输入、输出）。 */
    suspend fun usageSession(id: String): Pair<Long, Long> {
        val d = data("usage_session", JSONObject().put("id", id))
        return d.optLong("prompt") to d.optLong("completion")
    }

    // —— 工作区 ——

    suspend fun workspace(): JSONObject = data("workspace")

    suspend fun bindWorkspace(path: String): String =
        data("workspace_bind", JSONObject().put("path", path)).optString("data")

    // —— 内部 ——

    /**
     * 调一次并取出 `data`。
     *
     * `data` 为 `null` 时返回空对象：前端只关心"成功"，不需要每次都判空。
     */
    private suspend fun data(op: String, args: JSONObject = JSONObject()): JSONObject {
        val v = NativeBridge.call(op, args)
        if (!v.optBoolean("ok", false)) {
            throw KernelException.of(v.optJSONObject("error") ?: JSONObject())
        }
        return v.optJSONObject("data") ?: JSONObject()
    }

    /** 调一次，要求 `data` 是数组。 */
    private suspend fun array(op: String, args: JSONObject = JSONObject()): JSONArray {
        val v = NativeBridge.call(op, args)
        if (!v.optBoolean("ok", false)) {
            throw KernelException.of(v.optJSONObject("error") ?: JSONObject())
        }
        return v.optJSONArray("data") ?: JSONArray()
    }

    /** 调一次，要求 `data` 是数字。 */
    private suspend fun num(op: String, args: JSONObject = JSONObject()): Int {
        val v = NativeBridge.call(op, args)
        if (!v.optBoolean("ok", false)) {
            throw KernelException.of(v.optJSONObject("error") ?: JSONObject())
        }
        val d = v.opt("data")
        return when (d) {
            is Number -> d.toInt()
            is String -> d.toIntOrNull() ?: 0
            else -> 0
        }
    }

    /** 调一次，不关心返回值。 */
    private suspend fun unit(op: String, args: JSONObject = JSONObject()) {
        data(op, args)
    }

    private fun stringList(a: JSONArray): List<String> =
        (0 until a.length()).mapNotNull { a.optString(it).takeIf { s -> s.isNotEmpty() } }
}

package b.ai.core

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import b.ai.native.Kernel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * 设置项。
 *
 * ## 谁拥有哪个字段
 *
 * 这里**刻意分成两半**，因为这两半的生命周期和用途完全不同：
 *
 * | 归属 | 存在哪 | 例子 |
 * |---|---|---|
 * | **行为** | Rust 的 `config.json` | 模型、温度、上下文预算、权限档位、工作方式、工作区 |
 * | **外观** | 本地 SharedPreferences | 桌宠、毛玻璃、气泡、背景图、字号、触感反馈 |
 *
 * 为什么行为参数必须在 Rust：终端界面与安卓界面共用同一套内核，如果模型名、
 * 预算只在 SharedPreferences 里，`baicodex --repl` 就会用另一套配置，两边行为
 * 不一致——这是最难查的一类 bug。
 *
 * 为什么外观参数不搬：它们对内核毫无意义，搬进去只会让配置结构膨胀，还要求
 * 每次改个圆角都过一遍 JSON。
 *
 * ## 读写方式
 *
 * - 读：`rev` 是一个 Compose 快照状态，任何一次写入都会让它自增，于是所有读过
 *   配置的 composable 自动重组。这样不必为 90 个字段各建一个 `mutableStateOf`。
 * - 写：改内存里的 JSON → 立即返回（界面不卡）→ 300ms 防抖后整份推给 Rust。
 *   防抖是必要的：拖动滑杆会连续写几十次，每次都过 JNI 不值得。
 */
object Cfg {

    // —— 存储 ——

    private const val SP = "bai_cfg"

    private var sp: SharedPreferences? = null
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var flushJob: Job? = null

    /** Rust 侧的配置镜像。只有 [load] 与 [patch] 会改它。 */
    private var doc: JSONObject = JSONObject()

    /** 变更计数。getter 读它，Compose 才能感知到"配置变了"。 */
    private var rev by mutableStateOf(0)

    /** 是否已经从内核读到配置。false 时读到的都是默认值。 */
    var ready by mutableStateOf(false)
        private set

    /**
     * 初始化。**只做本地存储**，不碰内核——内核可能还没启动。
     * 内核启动后由 `App` 调 [load] 把行为参数拉过来。
     */
    fun init(c: Context) {
        if (sp != null) return
        sp = c.applicationContext.getSharedPreferences(SP, Context.MODE_PRIVATE)
        ready = false
    }

    /** 从内核拉一份配置。启动时调一次，外部改过配置文件后也可以再调。 */
    suspend fun load() {
        doc = runCatching { Kernel.config() }.getOrDefault(JSONObject())
        ready = true
        rev++
    }

    /**
     * 立刻把待写的内容推给内核。切后台、退出前调用。
     */
    fun flush() {
        flushJob?.cancel()
        flushJob = null
        push()
    }

    private fun schedule() {
        rev++
        if (flushJob?.isActive == true) return
        flushJob = scope.launch {
            delay(300)
            push()
        }
    }

    private fun push() {
        if (!Kernel.isBooted) return
        val snapshot = JSONObject(doc.toString())
        scope.launch {
            runCatching { Kernel.patchConfig(snapshot) }
        }
        // 内核返回的是合并后的完整配置；这里不覆盖 doc，避免把本地刚落下的
        // 输入用旧值盖回去（比如用户正在输入框里打字）。
    }

    // —— 读 ——

    @Suppress("UNUSED_EXPRESSION")
    private fun <T> get(path: String, def: T): T {
        // 这一句是给 Compose 看的：读到 rev，本次组合就订阅了"配置变更"。
        // 少了它，改完设置界面不会重组——而且很难看出来是这里的问题。
        rev
        return fromDoc(doc, path, def)
    }

    private fun <T> set(path: String, v: T) {
        putDoc(doc, path, v)
        schedule()
    }

    // —— 行为参数（Rust）——

    var onboarded: Boolean
        get() = get("onboarded", false)
        set(v) = set("onboarded", v)

    var lang: String
        get() = get("agent.language", "zh")
        set(v) = set("agent.language", v)

    var workMode: WorkMode
        get() = WorkMode.of(get("agent.work_mode", "normal"))
        set(v) = set("agent.work_mode", v.id)

    var agentMode: AgentMode
        get() = AgentMode.of(get("agent.mode", "standard"))
        set(v) = set("agent.mode", v.id)

    var zeroWidth: Boolean
        get() = get("security.zero_width", true)
        set(v) = set("security.zero_width", v)

    var providerId: String
        get() = get("provider.id", "")
        set(v) = set("provider.id", v)

    var profileId: String
        get() = get("provider.profile_id", "")
        set(v) = set("provider.profile_id", v)

    var model: String
        get() = get("provider.model", "")
        set(v) = set("provider.model", v)

    var think: Boolean
        get() = get("model.think", false)
        set(v) = set("model.think", v)

    var thinkEffort: String
        get() = get("model.effort", "medium")
        set(v) = set("model.effort", v)

    var maxMode: Boolean
        get() = get("model.max_mode", false)
        set(v) = set("model.max_mode", v)

    var modelVision: Boolean
        get() = get("model.vision", true)
        set(v) = set("model.vision", v)

    var modelToolCall: Boolean
        get() = get("model.tools", true)
        set(v) = set("model.tools", v)

    var modelReasoning: Boolean
        get() = get("model.reasoning", false)
        set(v) = set("model.reasoning", v)

    var temperature: Float
        get() = get("model.temperature", 0.7f)
        set(v) = set("model.temperature", v)

    var maxTokens: Int
        get() = get("model.max_tokens", 0)
        set(v) = set("model.max_tokens", v)

    var personaId: String
        get() = get("agent.persona", "")
        set(v) = set("agent.persona", v)

    var workspace: String
        get() = get("workspace.current", "")
        set(v) = set("workspace.current", v)

    var autoBindWorkspace: Boolean
        get() = get("workspace.auto_bind", true)
        set(v) = set("workspace.auto_bind", v)

    var ctxAuto: Boolean
        get() = get("context.auto_compact", true)
        set(v) = set("context.auto_compact", v)

    var ctxTokenBudget: Int
        get() = get("context.budget_chars", 120_000)
        set(v) = set("context.budget_chars", v)

    var ctxKeep: Int
        get() = get("context.keep_msgs", 12)
        set(v) = set("context.keep_msgs", v)

    var ctxMsgCount: Int
        get() = get("context.msg_count", 0)
        set(v) = set("context.msg_count", v)

    var summaryPrompt: String
        get() = get("context.summary_prompt", "")
        set(v) = set("context.summary_prompt", v)

    var summaryWords: Int
        get() = get("context.summary_words", 600)
        set(v) = set("context.summary_words", v)

    var summaryProfileId: String
        get() = get("context.summary_profile", "")
        set(v) = set("context.summary_profile", v)

    var toolBudgetTokens: Int
        get() = get("tool.budget_chars", 12_000)
        set(v) = set("tool.budget_chars", v)

    var searchProvider: String
        get() = get("search.provider", "")
        set(v) = set("search.provider", v)

    /** 每 token 平均字符数，纯展示用（估算 token）。 */
    val CHARS_PER_TOKEN: Int get() = 3

    // —— 外观参数（本地）——

    private fun sb(key: String, def: Boolean) = sp?.getBoolean(key, def) ?: def
    private fun si(key: String, def: Int) = sp?.getInt(key, def) ?: def
    private fun sf(key: String, def: Float) = sp?.getFloat(key, def) ?: def
    private fun ss(key: String, def: String) = sp?.getString(key, def) ?: def

    private fun wb(key: String, v: Boolean) {
        sp?.edit()?.putBoolean(key, v)?.apply()
        rev++
    }

    private fun wi(key: String, v: Int) {
        sp?.edit()?.putInt(key, v)?.apply()
        rev++
    }

    private fun wf(key: String, v: Float) {
        sp?.edit()?.putFloat(key, v)?.apply()
        rev++
    }

    private fun ws(key: String, v: String) {
        sp?.edit()?.putString(key, v)?.apply()
        rev++
    }

    var starShown: Boolean
        get() = sb("starShown", false)
        set(v) = wb("starShown", v)

    var noRate: Boolean
        get() = sb("noRate", false)
        set(v) = wb("noRate", v)

    var usedSec: Int
        get() = si("usedSec", 0)
        set(v) = wi("usedSec", v)

    var tosAgreed: Int
        get() = si("tosAgreed", 0)
        set(v) = wi("tosAgreed", v)

    var joinJoined: Boolean
        get() = sb("joinJoined", false)
        set(v) = wb("joinJoined", v)

    var migClean: Boolean
        get() = sb("migClean", false)
        set(v) = wb("migClean", v)

    var httpConfirmed: Boolean
        get() = sb("httpConfirmed", false)
        set(v) = wb("httpConfirmed", v)

    var petModelTip: Boolean
        get() = sb("petModelTip", false)
        set(v) = wb("petModelTip", v)

    var accNick: String
        get() = ss("accNick", "")
        set(v) = ws("accNick", v)

    var accUid: Int
        get() = si("accUid", 0)
        set(v) = wi("accUid", v)

    var accUname: String
        get() = ss("accUname", "")
        set(v) = ws("accUname", v)

    var accAvatar: String
        get() = ss("accAvatar", "")
        set(v) = ws("accAvatar", v)

    var avatarOn: Boolean
        get() = sb("avatarOn", true)
        set(v) = wb("avatarOn", v)

    var avatarUser: String
        get() = ss("avatarUser", "")
        set(v) = ws("avatarUser", v)

    var bubbleCapsule: Boolean
        get() = sb("bubbleCapsule", true)
        set(v) = wb("bubbleCapsule", v)

    var agentName: String
        get() = ss("agentName", "白")
        set(v) = ws("agentName", v)

    // 桌宠
    var petOn: Boolean
        get() = sb("petOn", false)
        set(v) = wb("petOn", v)
    var petGlobal: Boolean
        get() = sb("petGlobal", false)
        set(v) = wb("petGlobal", v)
    var petSize: Int
        get() = si("petSize", 96)
        set(v) = wi("petSize", v)
    var petAlpha: Float
        get() = sf("petAlpha", 0.9f)
        set(v) = wf("petAlpha", v)
    var petX: Int
        get() = si("petX", 0)
        set(v) = wi("petX", v)
    var petY: Int
        get() = si("petY", 0)
        set(v) = wi("petY", v)
    var petFlip: Boolean
        get() = sb("petFlip", false)
        set(v) = wb("petFlip", v)
    var petShadow: Boolean
        get() = sb("petShadow", true)
        set(v) = wb("petShadow", v)
    var petTalk: Boolean
        get() = sb("petTalk", true)
        set(v) = wb("petTalk", v)
    var petSound: Boolean
        get() = sb("petSound", false)
        set(v) = wb("petSound", v)
    var petSoundVol: Int
        get() = si("petSoundVol", 60)
        set(v) = wi("petSoundVol", v)
    var petFont: String
        get() = ss("petFont", "")
        set(v) = ws("petFont", v)
    var petFontBold: Boolean
        get() = sb("petFontBold", false)
        set(v) = wb("petFontBold", v)

    // 毛玻璃与输入
    var glassCard: Boolean
        get() = sb("glassCard", true)
        set(v) = wb("glassCard", v)
    var glassBar: Boolean
        get() = sb("glassBar", true)
        set(v) = wb("glassBar", v)
    var glassBlur: Int
        get() = si("glassBlur", 18)
        set(v) = wi("glassBlur", v)
    var bgBlur: Int
        get() = si("bgBlur", 0)
        set(v) = wi("bgBlur", v)
    var bgStyle: Int
        get() = si("bgStyle", 0)
        set(v) = wi("bgStyle", v)
    var bgImg: String
        get() = ss("bgImg", "")
        set(v) = ws("bgImg", v)
    var floatInput: Boolean
        get() = sb("floatInput", false)
        set(v) = wb("floatInput", v)
    var fullInputNew: Boolean
        get() = sb("fullInputNew", true)
        set(v) = wb("fullInputNew", v)
    var tactileBtn: Boolean
        get() = sb("tactileBtn", true)
        set(v) = wb("tactileBtn", v)
    var pasteToFile: Boolean
        get() = sb("pasteToFile", true)
        set(v) = wb("pasteToFile", v)
    var pasteLimit: Int
        get() = si("pasteLimit", 4000)
        set(v) = wi("pasteLimit", v)
    var showMeta: Boolean
        get() = sb("showMeta", true)
        set(v) = wb("showMeta", v)
    var showUsage: Boolean
        get() = sb("showUsage", true)
        set(v) = wb("showUsage", v)
    var hintText: String
        get() = ss("hintText", "")
        set(v) = ws("hintText", v)
    var draft: String
        get() = ss("draft", "")
        set(v) = ws("draft", v)
    var setDraft: Boolean
        get() = sb("setDraft", false)
        set(v) = wb("setDraft", v)

    /** 会话自己的工作区（界面临时展示用，真值在会话行里）。 */
    var sessionWorkspace: String
        get() = ss("sessionWorkspace", "")
        set(v) = ws("sessionWorkspace", v)

    // —— 凭据：只进加密库 ——

    /**
     * 当前供应商的 API Key。
     *
     * 读永远返回空串——密钥不会回到界面，UI 只显示"已配置"。写会进加密库。
     */
    var apiKey: String
        get() = ""
        set(v) = run {
            if (v.isBlank()) return@run
            val bucket = "api"
            val key = providerId.ifBlank { "default" }
            scope.launch { runCatching { Kernel.vaultPut(bucket, key, v.trim()) } }
        }

    /** Tavily / Google 搜索的 key。同样只写不读。 */
    var tavilyKey: String
        get() = ""
        set(v) = run {
            if (v.isBlank()) return@run
            scope.launch { runCatching { Kernel.vaultPut("api", "tavily", v.trim()) } }
        }

    /** 某个配置档的 key。 */
    fun setProfileKey(profileId: String, key: String) {
        if (profileId.isBlank() || key.isBlank()) return
        scope.launch { runCatching { Kernel.vaultPut("api", "profile:$profileId", key.trim()) } }
    }

    /**
     * 清空运行时状态（退出登录、重置配置）。
     */
    fun reset() {
        doc = JSONObject()
        sp?.edit()?.clear()?.apply()
        ready = false
        rev++
    }

    // —— JSON 工具 ——

    /**
     * 按 `a.b.c` 取值。
     *
     * 类型不对/字段缺失都回退到默认值：配置文件是用户能手动改的，不能假设它合法。
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> fromDoc(o: JSONObject, path: String, def: T): T {
        var cur: Any? = o
        for (part in path.split('.')) {
            cur = (cur as? JSONObject)?.opt(part) ?: return def
        }
        return when (def) {
            is Boolean -> (cur as? Boolean) ?: def
            is Int -> (cur as? Number)?.toInt() ?: def
            is Long -> (cur as? Number)?.toLong() ?: def
            is Float -> (cur as? Number)?.toFloat() ?: def
            is Double -> (cur as? Number)?.toDouble() ?: def
            is String -> (cur as? String) ?: def
            else -> (cur as? T) ?: def
        }
    }

    private fun putDoc(o: JSONObject, path: String, v: Any?) {
        val parts = path.split('.')
        var cur = o
        for (i in 0 until parts.size - 1) {
            cur = cur.optJSONObject(parts[i]) ?: JSONObject().also { cur.put(parts[i], it) }
        }
        cur.put(parts.last(), v)
    }
}

package b.ai.core

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/** 全局配置：SharedPreferences 落盘 + Compose 状态镜像 */
object Cfg {
    /** 自定义系统提示在数据根里的文件名 */
    const val SYSTEM_PROMPT_FILE = "system_prompt.md"

    private var sp: SharedPreferences? = null
    private val cache = HashMap<String, MutableState<Any?>>()

    @Suppress("UNCHECKED_CAST")
    private fun <T> mem(key: String, def: T): MutableState<T> =
        cache.getOrPut(key) { mutableStateOf(def as Any?) } as MutableState<T>

    fun init(c: Context) {
        if (sp != null) return
        val p = c.applicationContext.getSharedPreferences("bai_cfg", Context.MODE_PRIVATE)
        sp = p
        mem("onboarded", false).value = p.getBoolean("onboarded", false)
        mem("work_mode", WorkMode.NORMAL.id).value = p.getString("work_mode", WorkMode.NORMAL.id)!!
        mem("zero_width", false).value = p.getBoolean("zero_width", false)
        mem("provider_id", "deepseek").value = p.getString("provider_id", "deepseek")!!
        mem("base_url", "").value = p.getString("base_url", "")!!
        mem("api_key", "").value = p.getString("api_key", "")!!
        mem("model", "").value = p.getString("model", "")!!
        mem("workflow", "general").value = p.getString("workflow", "general")!!
        mem("ctx_auto", true).value = p.getBoolean("ctx_auto", true)
        mem("ctx_budget", 12000).value = p.getInt("ctx_budget", 12000)
        mem("ctx_keep", 8).value = p.getInt("ctx_keep", 8)
        mem("ctx_msg_count", 16).value = p.getInt("ctx_msg_count", 16)
        mem("think", false).value = p.getBoolean("think", false)
        mem("think_effort", "medium").value = p.getString("think_effort", "medium")!!
        mem("temperature", -1f).value = p.getFloat("temperature", -1f)
        //模型能力勾选：默认按「都支持」走，勾掉后请求体自动降级而不是报错
        mem("model_vision", true).value = p.getBoolean("model_vision", true)
        mem("model_tool_call", true).value = p.getBoolean("model_tool_call", true)
        mem("model_reasoning", true).value = p.getBoolean("model_reasoning", true)
        mem("tavily_key", "").value = p.getString("tavily_key", "")!!
        mem("search_provider", "").value = p.getString("search_provider", "")!!
        mem("persona_id", "").value = p.getString("persona_id", "")!!
        mem("system_prompt", "").value = p.getString("system_prompt", "")!!
        mem("extra_params", "").value = p.getString("extra_params", "")!!
        mem("workspace", "").value = p.getString("workspace", "")!!
        mem("default_workspace", "").value = p.getString("default_workspace", "")!!
        mem("font_scale", 1f).value = p.getFloat("font_scale", 1f)
        mem("float_input", false).value = p.getBoolean("float_input", false)
        mem("predictive_back", true).value = p.getBoolean("predictive_back", true)
        mem("auto_bind_workspace", true).value = p.getBoolean("auto_bind_workspace", true)
        mem("session_workspace", "").value = p.getString("session_workspace", "")!!
        //输入框占位提示：老配置没有这个键就留空，用默认的「请输入问题」
        mem("hint_text", "").value = p.getString("hint_text", "")!!
        mem("profile_id", "default").value = p.getString("profile_id", "default")!!
        mem("max_tokens", 0).value = p.getInt("max_tokens", 0)
        //界面语言：空串 = 跟随系统
        mem("lang", "").value = p.getString("lang", "")!!
        mem("max_mode", false).value = p.getBoolean("max_mode", false)
        mem("ctx_total", 128000).value = p.getInt("ctx_total", 128000)
        mem("ctx_pct", 70).value = p.getInt("ctx_pct", 70)
        mem("preset_seed", 0).value = p.getInt("preset_seed", 0)
        mem("persona_seed", 0).value = p.getInt("persona_seed", 0)
        //这几个键以前只写不读：改完当场生效，进程一重启就回到默认值，看着像没存盘
        mem("dark_theme", 0).value = p.getInt("dark_theme", 0)
        mem("inject_time", true).value = p.getBoolean("inject_time", true)
        mem("inject_battery", false).value = p.getBoolean("inject_battery", false)
        mem("inject_place", false).value = p.getBoolean("inject_place", false)
        mem("show_usage", true).value = p.getBoolean("show_usage", true)
        //模型价格：元 / 百万 token，0 = 不计价（统计页只数 token 不算钱）
        mem("price_in", 0f).value = p.getFloat("price_in", 0f)
        mem("price_out", 0f).value = p.getFloat("price_out", 0f)
        mem("price_cache", 0f).value = p.getFloat("price_cache", 0f)
        mem("show_meta", true).value = p.getBoolean("show_meta", true)
        mem("speak_replies", false).value = p.getBoolean("speak_replies", false)
        mem("profile_name", "").value = p.getString("profile_name", "")!!
        mem("profile_custom", false).value = p.getBoolean("profile_custom", false)
        //这几个键上一版只写不读：选完头像进程一重启就回默认，看着像没存过盘
        mem("avatar_on", true).value = p.getBoolean("avatar_on", true)
        mem("avatar_user", "").value = p.getString("avatar_user", "")!!
        mem("avatar_ai_mode", "persona").value = p.getString("avatar_ai_mode", "persona")!!
        mem("avatar_ai", "").value = p.getString("avatar_ai", "")!!
        mem("bubble_capsule", false).value = p.getBoolean("bubble_capsule", false)
        //1.2.0 外观四项：玻璃卡片 / 质感按钮 / 玻璃底栏 / 页面背景（0 纯色 1 渐变 2 点阵）
        mem("glass_card", false).value = p.getBoolean("glass_card", false)
        mem("tactile_btn", false).value = p.getBoolean("tactile_btn", false)
        mem("glass_bar", false).value = p.getBoolean("glass_bar", false)
        mem("bg_style", 0).value = p.getInt("bg_style", 0)
        mem("waifu_mode", false).value = p.getBoolean("waifu_mode", false)
        //旧版本把自定义提示只存在 SP 里，这里补写到数据根，之后改文件就生效
        if (!Store.exists(SYSTEM_PROMPT_FILE)) {
            val v = p.getString("system_prompt", "")!!
            if (v.isNotBlank()) Store.write(SYSTEM_PROMPT_FILE, v)
        }
    } //读盘并预热状态

    private fun put(k: String, v: Any?) {
        val e = sp?.edit() ?: return
        when (v) {
            is Boolean -> e.putBoolean(k, v)
            is Int -> e.putInt(k, v)
            is Float -> e.putFloat(k, v)
            else -> e.putString(k, v as? String ?: "")
        }
        e.apply()
    }

    var onboarded: Boolean
        get() = mem("onboarded", false).value
        set(v) { mem("onboarded", false).value = v; put("onboarded", v) }
    /** 界面语言：空串 = 跟随系统，其它是 BCP-47（zh-CN / zh-TW / en / ja / fr） */
    var lang: String
        get() = mem("lang", "").value
        set(v) { mem("lang", "").value = v; put("lang", v) }

    var workMode: WorkMode
        get() = WorkMode.of(mem("work_mode", WorkMode.NORMAL.id).value)
        set(v) { mem("work_mode", WorkMode.NORMAL.id).value = v.id; put("work_mode", v.id) }

    var zeroWidth: Boolean
        get() = mem("zero_width", false).value
        set(v) { mem("zero_width", false).value = v; put("zero_width", v) }

    var providerId: String
        get() = mem("provider_id", "deepseek").value
        set(v) { mem("provider_id", "deepseek").value = v; put("provider_id", v) }

    var baseUrl: String
        get() = mem("base_url", "").value
        set(v) { mem("base_url", "").value = v; put("base_url", v) }

    var apiKey: String
        get() = mem("api_key", "").value
        set(v) { mem("api_key", "").value = v; put("api_key", v) }

    var model: String
        get() = mem("model", "").value
        set(v) { mem("model", "").value = v; put("model", v) }

    var workflow: String
        get() = mem("workflow", "general").value
        set(v) { mem("workflow", "general").value = v; put("workflow", v) }

    /** 上下文超预算时自动把早期消息压成摘要 */
    var ctxAuto: Boolean
        get() = mem("ctx_auto", true).value
        set(v) { mem("ctx_auto", true).value = v; put("ctx_auto", v) }

    /** 未压缩部分的字符预算 */
    var ctxBudget: Int
        get() = mem("ctx_budget", 12000).value
        set(v) { mem("ctx_budget", 12000).value = v; put("ctx_budget", v) }

    /** 压缩时保留的最近消息条数 */
    var ctxKeep: Int
        get() = mem("ctx_keep", 8).value
        set(v) { mem("ctx_keep", 8).value = v; put("ctx_keep", v) }

    /** 未压缩消息条数超过它就触发压缩（与字符预算二选一，先到先压） */
    var ctxMsgCount: Int
        get() = mem("ctx_msg_count", 16).value
        set(v) { mem("ctx_msg_count", 16).value = v; put("ctx_msg_count", v) }

    /** 是否让模型先思考再回答（下发 reasoning_effort） */
    var think: Boolean
        get() = mem("think", false).value
        set(v) { mem("think", false).value = v; put("think", v) }

    /** 思考强度：low / medium / high */
    var thinkEffort: String
        get() = mem("think_effort", "medium").value
        set(v) { mem("think_effort", "medium").value = v; put("think_effort", v) }

    /** 采样温度，负数表示不下发（跟随服务端默认） */
    var temperature: Float
        get() = mem("temperature", -1f).value
        set(v) { mem("temperature", -1f).value = v; put("temperature", v) }

    /** 模型能力：是否支持图片输入。关掉后图片附件降级成一行文字说明，不会把请求打成 400 */
    var modelVision: Boolean
        get() = mem("model_vision", true).value
        set(v) { mem("model_vision", true).value = v; put("model_vision", v) }

    /** 模型能力：是否支持工具调用。关掉后请求体不下发 tools，模型只能纯文本回答 */
    var modelToolCall: Boolean
        get() = mem("model_tool_call", true).value
        set(v) { mem("model_tool_call", true).value = v; put("model_tool_call", v) }

    /** 模型能力：是否支持推理参数。关掉后即使开了思考也不下发 reasoning_effort */
    var modelReasoning: Boolean
        get() = mem("model_reasoning", true).value
        set(v) { mem("model_reasoning", true).value = v; put("model_reasoning", v) }

    /** Tavily API Key：联网搜索工具用 */
    var tavilyKey: String
        get() = mem("tavily_key", "").value
        set(v) { mem("tavily_key", "").value = v; put("tavily_key", v) }

    /** 选中的搜索平台 id（对应 Search.providers），空 = 没启用搜索技能 */
    var searchProvider: String
        get() = mem("search_provider", "").value
        set(v) { mem("search_provider", "").value = v; put("search_provider", v) }

    /** 当前选中的智能体角色 id，空 = 默认助手 */
    var personaId: String
        get() = mem("persona_id", "").value
        set(v) { mem("persona_id", "").value = v; put("persona_id", v) }
    /** 已注入的内置角色版本：小于 Personas.PRESET_SEED 时补一次，用户删掉的不复活 */
    var personaSeed: Int
        get() = mem("persona_seed", 0).value
        set(v) { mem("persona_seed", 0).value = v; put("persona_seed", v) }

    /** 聊天里是否显示头像 */
    var avatarOn: Boolean
        get() = mem("avatar_on", true).value
        set(v) { mem("avatar_on", true).value = v; put("avatar_on", v) }

    /** 用户头像的本地图片路径，空 = 用默认头像 */
    var avatarUser: String
        get() = mem("avatar_user", "").value
        set(v) { mem("avatar_user", "").value = v; put("avatar_user", v) }

    /** AI 头像来源：persona = 跟当前智能体角色的头像，custom = 用下面这张自定义图 */
    var avatarAiMode: String
        get() = mem("avatar_ai_mode", "persona").value
        set(v) { mem("avatar_ai_mode", "persona").value = v; put("avatar_ai_mode", v) }

    /** 消息强制高圆角：开了之后所有消息气泡取高度一半的胶囊形圆角 */
    var bubbleCapsule: Boolean
        get() = mem("bubble_capsule", false).value
        set(v) { mem("bubble_capsule", false).value = v; put("bubble_capsule", v) }

    /** 自定义 AI 头像的本地图片路径，仅 avatarAiMode=custom 时用 */
    var avatarAi: String
        get() = mem("avatar_ai", "").value
        set(v) { mem("avatar_ai", "").value = v; put("avatar_ai", v) }

    /** Waifu 模式：回复按标点切成小句逐条发送，营造更自然的对话节奏 */
    var waifuMode: Boolean
        get() = mem("waifu_mode", false).value
        set(v) { mem("waifu_mode", false).value = v; put("waifu_mode", v) }

    /** 自定义系统提示，拼在角色之后；落盘在数据根 system_prompt.md，直接改文件也生效 */
    var systemPrompt: String
        get() = Store.read(SYSTEM_PROMPT_FILE) ?: mem("system_prompt", "").value
        set(v) {
            mem("system_prompt", "").value = v
            put("system_prompt", v)
            Store.write(SYSTEM_PROMPT_FILE, v)
        }

    /** 搜索平台配置项的存储键：tavily 的 key 沿用旧键，升级后已填的 Key 不丢 */
    private fun searchCfgKey(provider: String, field: String): String =
        if (provider == "tavily" && field == "key") "tavily_key" else "search_cfg_${provider}_$field"

    /** 读某个平台某个配置项（如 tavily 的 key、google 的 cx） */
    fun searchCfg(provider: String, field: String): String {
        if (provider.isBlank() || field.isBlank()) return ""
        val k = searchCfgKey(provider, field)
        val st = mem(k, "")
        if (st.value == "") st.value = sp?.getString(k, "") ?: ""
        return st.value
    } //读平台配置

    fun setSearchCfg(provider: String, field: String, v: String) {
        if (provider.isBlank() || field.isBlank()) return
        val k = searchCfgKey(provider, field)
        mem(k, "").value = v
        put(k, v)
    } //写平台配置

    /** 附加请求参数（JSON），覆盖/补齐默认字段，留空则不加 */
    var extraParams: String
        get() = mem("extra_params", "").value
        set(v) { mem("extra_params", "").value = v; put("extra_params", v) }

    /** 用户显式绑定的工作区路径；空 = 用数据根下的自动工作区 */
    var workspace: String
        get() = mem("workspace", "").value
        set(v) { mem("workspace", "").value = v; put("workspace", v) }

    /** 自动生成的默认工作区（数据根下的 4-4-4-4-4-4 哈希目录），由 Workspace 首次使用时写入 */
    var defaultWorkspace: String
        get() = mem("default_workspace", "").value
        set(v) { mem("default_workspace", "").value = v; put("default_workspace", v) }

    /** 深浅主题：0 = 跟随系统，1 = 浅色，2 = 深色 */
    var darkTheme: Int
        get() = mem("dark_theme", 0).value
        set(v) { mem("dark_theme", 0).value = v; put("dark_theme", v) }

    /** 全局字号缩放：乘在系统 fontScale 上，1f = 跟随系统 */
    var fontScale: Float
        get() = mem("font_scale", 1f).value
        set(v) { mem("font_scale", 1f).value = v; put("font_scale", v) }

    /** 输入区是否做成悬浮卡片（不贴屏幕底边） */
    var floatInput: Boolean
        get() = mem("float_input", false).value
        set(v) { mem("float_input", false).value = v; put("float_input", v) }

    /** 液态玻璃卡片：所有卡片换成半透玻璃 + 手指位置柔光 */
    var glassCard: Boolean
        get() = mem("glass_card", false).value
        set(v) { mem("glass_card", false).value = v; put("glass_card", v) }

    /** 质感按钮：深度思考 / 最大 / 语音 / 发送换成带厚度的 3D 按钮 */
    var tactileBtn: Boolean
        get() = mem("tactile_btn", false).value
        set(v) { mem("tactile_btn", false).value = v; put("tactile_btn", v) }

    /** 液态玻璃底栏：设置页底部那条悬浮胶囊换成玻璃 */
    var glassBar: Boolean
        get() = mem("glass_bar", false).value
        set(v) { mem("glass_bar", false).value = v; put("glass_bar", v) }

    /** 页面背景：0 纯色 / 1 柔光渐变 / 2 点阵 */
    var bgStyle: Int
        get() = mem("bg_style", 0).value
        set(v) { mem("bg_style", 0).value = v; put("bg_style", v) }

    /** 输入框占位提示：空则显示「请输入问题」；填多行则每行一条随机轮换 */
    var hintText: String
        get() = mem("hint_text", "").value
        set(v) { mem("hint_text", "").value = v; put("hint_text", v) }

    /** 返回键是否由 App 接管（关抽屉、退设置）；关掉就直接交给系统退出 */
    var predictiveBack: Boolean
        get() = mem("predictive_back", true).value
        set(v) { mem("predictive_back", true).value = v; put("predictive_back", v) }

    /** 模型价格：输入 / 输出 / 缓存命中，单位元每百万 token；0 = 不计价 */
    var priceIn: Float
        get() = mem("price_in", 0f).value
        set(v) { mem("price_in", 0f).value = v; put("price_in", v) }

    var priceOut: Float
        get() = mem("price_out", 0f).value
        set(v) { mem("price_out", 0f).value = v; put("price_out", v) }

    var priceCache: Float
        get() = mem("price_cache", 0f).value
        set(v) { mem("price_cache", 0f).value = v; put("price_cache", v) }

    /** 新会话自动绑新工作区：开 = 每建一个会话配一个新哈希目录；关 = 不绑定，空工作区 */
    var autoBindWorkspace: Boolean
        get() = mem("auto_bind_workspace", true).value
        set(v) { mem("auto_bind_workspace", true).value = v; put("auto_bind_workspace", v) }

    /** 本会话自动绑定的工作区路径（用户显式绑的 Cfg.workspace 优先） */
    var sessionWorkspace: String
        get() = mem("session_workspace", "").value
        set(v) { mem("session_workspace", "").value = v; put("session_workspace", v) }

    /** 当前选中的模型配置 id（Profiles 里的条目），切换它会把提供商 / Key / 模型整套换上 */
    var profileId: String
        get() = mem("profile_id", "default").value
        set(v) { mem("profile_id", "default").value = v; put("profile_id", v) }

    /** 单轮回复最大 token；0 = 不下发，跟随服务端默认 */
    var maxTokens: Int
        get() = mem("max_tokens", 0).value
        set(v) { mem("max_tokens", 0).value = v; put("max_tokens", v) }

    /** 最大模式：不下发 max_tokens 并把思考强度顶到 high，让模型把话尽量说完 */
    var maxMode: Boolean
        get() = mem("max_mode", false).value
        set(v) { mem("max_mode", false).value = v; put("max_mode", v) }

    /** 当前选中配置的显示名；只在 profileCustom=true 时生效 */
    var profileName: String
        get() = mem("profile_name", "").value
        set(v) { mem("profile_name", "").value = v; put("profile_name", v) }

    /** 配置名是否用户自定义：false 时跟随「厂商 · 模型」自动变化 */
    var profileCustom: Boolean
        get() = mem("profile_custom", false).value
        set(v) { mem("profile_custom", false).value = v; put("profile_custom", v) }

    /** 配置名迁移版本：老版本把自动名也标成了自定义，升级后降一次级 */
    var profileSeed: Int
        get() = mem("profile_seed", 0).value
        set(v) { mem("profile_seed", 0).value = v; put("profile_seed", v) }

    /** 注入当前时间到系统提示 */
    var injectTime: Boolean
        get() = mem("inject_time", true).value
        set(v) { mem("inject_time", true).value = v; put("inject_time", v) }

    /** 注入电量与充电状态到系统提示 */
    var injectBattery: Boolean
        get() = mem("inject_battery", false).value
        set(v) { mem("inject_battery", false).value = v; put("inject_battery", v) }

    /** 注入最后已知位置到系统提示（需定位权限，没授权就跳过） */
    var injectPlace: Boolean
        get() = mem("inject_place", false).value
        set(v) { mem("inject_place", false).value = v; put("inject_place", v) }

    /** 回复底部显示用量：总 token 与 tok/s */
    var showUsage: Boolean
        get() = mem("show_usage", true).value
        set(v) { mem("show_usage", true).value = v; put("show_usage", v) }

    /** 回复底部显示请求信息：时间 / 供应商 / 模型 / 首响 / 总耗时 */
    var showMeta: Boolean
        get() = mem("show_meta", true).value
        set(v) { mem("show_meta", true).value = v; put("show_meta", v) }

    /** 回复念出来：走系统 TTS，默认关 */
    var speakReplies: Boolean
        get() = mem("speak_replies", false).value
        set(v) { mem("speak_replies", false).value = v; put("speak_replies", v) }

    /** 模型总上下文窗口（token），压缩阈值按它的百分比折算 */
    var ctxTotal: Int
        get() = mem("ctx_total", 128000).value
        set(v) { mem("ctx_total", 128000).value = v; put("ctx_total", v) }

    /** 压缩触发阈值：占总上下文窗口的百分比，10-99 */
    var ctxPct: Int
        get() = mem("ctx_pct", 70).value
        set(v) {
            val n = v.coerceIn(10, 99)
            mem("ctx_pct", 70).value = n
            put("ctx_pct", n)
        }

    /** 折成字符预算：中文按 1 token ≈ 1.8 字粗估，给压缩判定用 */
    val ctxCharBudget: Int
        get() = (ctxTotal.toLong() * ctxPct / 100L * 1.8).toInt()

    /** 已注入的预置技能版本：小于 Skills.PRESET_SEED 时补一次新版预置 */
    var presetSeed: Int
        get() = mem("preset_seed", 0).value
        set(v) { mem("preset_seed", 0).value = v; put("preset_seed", v) }

    /** 读某个模型配置的 Key：单独存私有 SP，数据根文件里不放明文 */
    fun profileKey(id: String): String {
        if (id.isBlank()) return ""
        val k = "profkey_$id"
        val st = mem(k, "")
        if (st.value == "") st.value = sp?.getString(k, "") ?: ""
        return st.value
    } //读配置 Key

    fun setProfileKey(id: String, v: String) {
        if (id.isBlank()) return
        val k = "profkey_$id"
        mem(k, "").value = v
        put(k, v)
    } //写配置 Key

    /** 一个配置可以填多个 Key：换行 / 逗号 / 分号分隔，请求失败时轮换 */
    fun keyList(id: String): List<String> =
        profileKey(id).split('\n', ',', ';', '\t')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct() //Key列表

    /** 工具策略：ask / allow / deny */
    fun policy(tool: String): String = mem("policy_$tool", "ask").also {
        if (it.value == "ask") it.value = sp?.getString("policy_$tool", "ask") ?: "ask"
    }.value

    fun setPolicy(tool: String, policy: String) {
        mem("policy_$tool", "ask").value = policy
        put("policy_$tool", policy)
    }

    val ready: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()

    /** 每个会话各自一份输入框草稿：切走再回来、杀进程重进都不丢 */
    fun draft(sid: String): String =
        if (sid.isBlank()) "" else sp?.getString("draft_$sid", "") ?: ""

    /** 写草稿：写空就当删除，省得 SP 里攒一堆空串 */
    fun setDraft(sid: String, text: String) {
        if (sid.isBlank()) return
        if (text.isBlank()) sp?.edit()?.remove("draft_$sid")?.apply()
        else sp?.edit()?.putString("draft_$sid", text)?.apply()
    } //写草稿
} //配置读写
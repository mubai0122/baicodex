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
        //独立无障碍激活流程：走过一次就不再自动弹，设置里仍可手动重跑
        mem("a11y_done", false).value = p.getBoolean("a11y_done", false)
        //「加入我们」卡片：join_dismissed = 勾了永不提醒；join_seen = 已提示过的 versionCode；join_joined = 跳转过 QQ 群
        mem("join_dismissed", false).value = p.getBoolean("join_dismissed", false)
        mem("join_seen", 0).value = p.getInt("join_seen", 0)
        mem("join_joined", false).value = p.getBoolean("join_joined", false)
        //累计前台使用秒数 + 评价弹窗只弹一次 + 用户关掉了评价弹窗
        mem("used_sec", 0).value = p.getInt("used_sec", 0)
        mem("star_shown", false).value = p.getBoolean("star_shown", false)
        mem("no_rate", false).value = p.getBoolean("no_rate", false)
        //反馈凭据：提交反馈时服务端下发，凭它只能看本机提交过的反馈
        mem("fb_token", "").value = p.getString("fb_token", "")!!
        //用户协议同意过的版本号（0 = 没同意过）；tos_sent = 已上报过的版本号
        mem("tos_agreed", 0).value = p.getInt("tos_agreed", 0)
        mem("tos_sent", 0).value = p.getInt("tos_sent", 0)
        mem("work_mode", WorkMode.NORMAL.id).value = p.getString("work_mode", WorkMode.NORMAL.id)!!
        mem("agent_mode", AgentMode.STANDARD.id).value = p.getString("agent_mode", AgentMode.STANDARD.id)!!
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
        mem("tool_budget_tokens", 0).value = p.getInt("tool_budget_tokens", 0)
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
        mem("inject_noti", false).value = p.getBoolean("inject_noti", false)
        mem("noti_limit", 0).value = p.getInt("noti_limit", 0)
        mem("inject_usage", false).value = p.getBoolean("inject_usage", false)
        mem("auto_a11y", false).value = p.getBoolean("auto_a11y", false)
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
        //背景图路径可能因清数据/换机失效，失效就清掉，免得设置页对着一条死路径
        val bg = p.getString("bg_img", "")!!
        val bgAlive = bg.isNotBlank() && java.io.File(bg).exists()
        if (bg.isNotBlank() && !bgAlive) put("bg_img", "")
        mem("bg_img", "").value = if (bgAlive) bg else ""
        mem("bg_blur", 0).value = p.getInt("bg_blur", 0)
        mem("profile_seed", 0).value = p.getInt("profile_seed", 0)
        mem("waifu_mode", false).value = p.getBoolean("waifu_mode", false)
        //旧版本把自定义提示只存在 SP 里，这里补写到数据根，之后改文件就生效
        if (!Store.exists(SYSTEM_PROMPT_FILE)) {
            val v = p.getString("system_prompt", "")!!
            if (v.isNotBlank()) Store.write(SYSTEM_PROMPT_FILE, v)
        }
        mem("pet_on", true).value = p.getBoolean("pet_on", true)
        mem("pet_global", true).value = p.getBoolean("pet_global", true)
        mem("pet_x", -1).value = p.getInt("pet_x", -1)
        mem("pet_y", -1).value = p.getInt("pet_y", -1)
        mem("pet_size", 120).value = p.getInt("pet_size", 120)
        mem("pet_seen", 0).value = p.getInt("pet_seen", 0)
        mem("pet_alpha", 100).value = p.getInt("pet_alpha", 100)
        mem("pet_flip", false).value = p.getBoolean("pet_flip", false)
        mem("pet_shadow", true).value = p.getBoolean("pet_shadow", true)
        mem("pet_card", false).value = p.getBoolean("pet_card", false)
        mem("pet_font", 14).value = p.getInt("pet_font", 14)
        mem("pet_font_bold", true).value = p.getBoolean("pet_font_bold", true)
        mem("pet_bubble_w", 210).value = p.getInt("pet_bubble_w", 210)
        mem("pet_bubble_r", 14).value = p.getInt("pet_bubble_r", 14)
        mem("pet_bubble_dark", false).value = p.getBoolean("pet_bubble_dark", false)
        mem("pet_float", true).value = p.getBoolean("pet_float", true)
        mem("pet_float_amp", 4).value = p.getInt("pet_float_amp", 4)
        mem("pet_float_ms", 1600).value = p.getInt("pet_float_ms", 1600)
        mem("pet_swim", false).value = p.getBoolean("pet_swim", false)
        mem("pet_swim_speed", 60).value = p.getInt("pet_swim_speed", 60)
        mem("pet_swim_bounce", true).value = p.getBoolean("pet_swim_bounce", true)
        mem("pet_bounce", true).value = p.getBoolean("pet_bounce", true)
        mem("pet_bounce_pct", 14).value = p.getInt("pet_bounce_pct", 14)
        mem("pet_talk", true).value = p.getBoolean("pet_talk", true)
        mem("pet_bubble_ms", 2600).value = p.getInt("pet_bubble_ms", 2600)
        mem("pet_timed_pct", 35).value = p.getInt("pet_timed_pct", 35)
        mem("pet_dbl_bal", true).value = p.getBoolean("pet_dbl_bal", true)
        mem("pet_sound", true).value = p.getBoolean("pet_sound", true)
        mem("pet_sound_vol", 70).value = p.getInt("pet_sound_vol", 70)
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
    var a11yDone: Boolean
        get() = mem("a11y_done", false).value
        set(v) { mem("a11y_done", false).value = v; put("a11y_done", v) }

    /** 「加入我们」卡片：勾了以后永不提醒就不再弹，设置里也没入口，属于一次性推广 */
    var joinDismissed: Boolean
        get() = mem("join_dismissed", false).value
        set(v) { mem("join_dismissed", false).value = v; put("join_dismissed", v) }

    /** 「加入我们」卡片已提示过的 versionCode：同版本只弹一次，升级后再弹一次 */
    var joinSeen: Int
        get() = mem("join_seen", 0).value
        set(v) { mem("join_seen", 0).value = v; put("join_seen", v) }

    /** 点过「立即加入」并成功跳出 QQ：下次卡片把「以后永不提醒」换成「我已加入，下次不再提示」 */
    var joinJoined: Boolean
        get() = mem("join_joined", false).value
        set(v) { mem("join_joined", false).value = v; put("join_joined", v) }

    /** 累计前台使用秒数：只用来判定「用了 2 小时要不要邀请评价」 */
    var usedSec: Int
        get() = mem("used_sec", 0).value
        set(v) { mem("used_sec", 0).value = v; put("used_sec", v) }

    /** 评价调研弹窗已经弹过一次，之后不再弹 */
    var starShown: Boolean
        get() = mem("star_shown", false).value
        set(v) { mem("star_shown", false).value = v; put("star_shown", v) }

    /** 用户关掉了评价调研弹窗：开关在设置「关于」与引导最后一步都能改 */
    var noRate: Boolean
        get() = mem("no_rate", false).value
        set(v) { mem("no_rate", false).value = v; put("no_rate", v) }

    var fbToken: String
        get() = mem("fb_token", "").value
        set(v) { mem("fb_token", "").value = v; put("fb_token", v) }

    /** 已同意的用户协议版本号，0 = 没同意过；进主界面前必须 >= Tos.VERSION */
    var tosAgreed: Int
        get() = mem("tos_agreed", 0).value
        set(v) { mem("tos_agreed", 0).value = v; put("tos_agreed", v) }

    /** 已上报给服务端的协议版本号，避免每次启动重复上报 */
    var tosSent: Int
        get() = mem("tos_sent", 0).value
        set(v) { mem("tos_sent", 0).value = v; put("tos_sent", v) }

    /** 界面语言：空串 = 跟随系统，其它是 BCP-47（zh-CN / zh-TW / en / ja / fr） */
    var lang: String
        get() = mem("lang", "").value
        set(v) { mem("lang", "").value = v; put("lang", v) }

    var workMode: WorkMode
        get() = WorkMode.of(mem("work_mode", WorkMode.NORMAL.id).value)
        set(v) { mem("work_mode", WorkMode.NORMAL.id).value = v.id; put("work_mode", v.id) }

    /** 运行模式：计划 / 标准 / 代码 / 极简 / 创造，改的是提示 + 工具面 */
    var agentMode: AgentMode
        get() = AgentMode.of(mem("agent_mode", AgentMode.STANDARD.id).value)
        set(v) { mem("agent_mode", AgentMode.STANDARD.id).value = v.id; put("agent_mode", v.id) }

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
    /**总结提示词：留空走内置默认；填了就整套替换，自动总结与手动重新总结都用它 */
    var summaryPrompt: String
        get() = mem("summary_prompt", "").value
        set(v) { mem("summary_prompt", "").value = v; put("summary_prompt", v) }

    /** 是否让模型先思考再回答（下发 reasoning_effort） */
    var think: Boolean
        get() = mem("think", false).value
        set(v) { mem("think", false).value = v; put("think", v) }

    /** 思考强度：low / medium / high */
    var thinkEffort: String
        get() = mem("think_effort", "medium").value
        set(v) { mem("think_effort", "medium").value = v; put("think_effort", v) }

    /** 工具结果预算：单条工具结果最多留给模型多少 token，0 = max（不限） */
    var toolBudgetTokens: Int
        get() = mem("tool_budget_tokens", 0).value
        set(v) { mem("tool_budget_tokens", 0).value = v; put("tool_budget_tokens", v) }

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
    /** 自定义背景图：私有目录里的绝对路径，空 = 不铺图 */
    var bgImg: String
        get() = mem("bg_img", "").value
        set(v) { mem("bg_img", "").value = v; put("bg_img", v) }
    /** 背景图模糊度 0-100：0 原图，100 最糊 */
    var bgBlur: Int
        get() = mem("bg_blur", 0).value
        set(v) { mem("bg_blur", 0).value = v.coerceIn(0, 100); put("bg_blur", v.coerceIn(0, 100)) }

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

    /** 注入今日通知（需通知使用权，没授权就跳过） */
    var injectNoti: Boolean
        get() = mem("inject_noti", false).value
        set(v) { mem("inject_noti", false).value = v; put("inject_noti", v) }

    /** 注入通知条数上限：0 = 不限，只受注入字符上限约束 */
    var notiLimit: Int
        get() = mem("noti_limit", 0).value
        set(v) { mem("noti_limit", 0).value = v; put("noti_limit", v) }

    /** 桌宠总开关：默认开，装完就有一只待在应用里 */
    var petOn: Boolean
        get() = mem("pet_on", true).value
        set(v) { mem("pet_on", true).value = v; put("pet_on", v) }

    /** true = 所有状态下都浮在屏幕上（默认）；false = 只在应用内展示 */
    var petGlobal: Boolean
        get() = mem("pet_global", true).value
        set(v) { mem("pet_global", true).value = v; put("pet_global", v) }

    /** 悬浮窗位置（px，-1 表示还没摆过）与边长（dp） */
    var petX: Int
        get() = mem("pet_x", -1).value
        set(v) { mem("pet_x", -1).value = v; put("pet_x", v) }

    var petY: Int
        get() = mem("pet_y", -1).value
        set(v) { mem("pet_y", -1).value = v; put("pet_y", v) }

    var petSize: Int
        get() = mem("pet_size", 120).value
        set(v) { val n = v.coerceIn(60, 260); mem("pet_size", 120).value = n; put("pet_size", n) }

    /** 看过桌宠引导的版本号：比当前 versionCode 低就弹一次 */
    var petSeen: Int
        get() = mem("pet_seen", 0).value
        set(v) { mem("pet_seen", 0).value = v; put("pet_seen", v) }

    /** 立绘不透明度（%）：越低越透 */
    var petAlpha: Int
        get() = mem("pet_alpha", 100).value
        set(v) { val n = v.coerceIn(30, 100); mem("pet_alpha", 100).value = n; put("pet_alpha", n) }

    /** 左右镜像 */
    var petFlip: Boolean
        get() = mem("pet_flip", false).value
        set(v) { mem("pet_flip", false).value = v; put("pet_flip", v) }

    /** 脚下垫一层淡阴影，深色背景里立绘不至于糊住 */
    var petShadow: Boolean
        get() = mem("pet_shadow", true).value
        set(v) { mem("pet_shadow", true).value = v; put("pet_shadow", v) }

    /** 立绘垫白色圆角卡片 */
    var petCard: Boolean
        get() = mem("pet_card", false).value
        set(v) { mem("pet_card", false).value = v; put("pet_card", v) }

    /** 气泡字号（sp）、是否加粗、最大宽（dp）、圆角（dp）、深色底 */
    var petFont: Int
        get() = mem("pet_font", 14).value
        set(v) { val n = v.coerceIn(9, 20); mem("pet_font", 14).value = n; put("pet_font", n) }

    var petFontBold: Boolean
        get() = mem("pet_font_bold", true).value
        set(v) { mem("pet_font_bold", true).value = v; put("pet_font_bold", v) }

    var petBubbleW: Int
        get() = mem("pet_bubble_w", 210).value
        set(v) { val n = v.coerceIn(100, 320); mem("pet_bubble_w", 210).value = n; put("pet_bubble_w", n) }

    var petBubbleR: Int
        get() = mem("pet_bubble_r", 14).value
        set(v) { val n = v.coerceIn(0, 28); mem("pet_bubble_r", 14).value = n; put("pet_bubble_r", n) }

    var petBubbleDark: Boolean
        get() = mem("pet_bubble_dark", false).value
        set(v) { mem("pet_bubble_dark", false).value = v; put("pet_bubble_dark", v) }

    /** 原地上下缓动：窗口不动，只动图片自己 */
    var petFloat: Boolean
        get() = mem("pet_float", true).value
        set(v) { mem("pet_float", true).value = v; put("pet_float", v) }

    var petFloatAmp: Int
        get() = mem("pet_float_amp", 4).value
        set(v) { val n = v.coerceIn(0, 24); mem("pet_float_amp", 4).value = n; put("pet_float_amp", n) }

    var petFloatMs: Int
        get() = mem("pet_float_ms", 1600).value
        set(v) { val n = v.coerceIn(400, 4000); mem("pet_float_ms", 1600).value = n; put("pet_float_ms", n) }

    /** 游泳状态：窗口自己在屏幕上跑，比原地浮动费电 */
    var petSwim: Boolean
        get() = mem("pet_swim", false).value
        set(v) { mem("pet_swim", false).value = v; put("pet_swim", v) }

    var petSwimSpeed: Int
        get() = mem("pet_swim_speed", 60).value
        set(v) { val n = v.coerceIn(10, 200); mem("pet_swim_speed", 60).value = n; put("pet_swim_speed", n) }

    /** true = 撞到屏幕边反弹；false = 从对面绕回来 */
    var petSwimBounce: Boolean
        get() = mem("pet_swim_bounce", true).value
        set(v) { mem("pet_swim_bounce", true).value = v; put("pet_swim_bounce", v) }

    /** 点击弹跳与幅度（%） */
    var petBounce: Boolean
        get() = mem("pet_bounce", true).value
        set(v) { mem("pet_bounce", true).value = v; put("pet_bounce", v) }

    var petBouncePct: Int
        get() = mem("pet_bounce_pct", 14).value
        set(v) { val n = v.coerceIn(0, 30); mem("pet_bounce_pct", 14).value = n; put("pet_bounce_pct", n) }

    /** 单击是否说话；气泡停留时长（ms） */
    var petTalk: Boolean
        get() = mem("pet_talk", true).value
        set(v) { mem("pet_talk", true).value = v; put("pet_talk", v) }

    var petBubbleMs: Int
        get() = mem("pet_bubble_ms", 2600).value
        set(v) { val n = v.coerceIn(800, 8000); mem("pet_bubble_ms", 2600).value = n; put("pet_bubble_ms", n) }

    /** 说「高峰 / 错峰」这类时段提示的概率（%），0 = 不说 */
    var petTimedPct: Int
        get() = mem("pet_timed_pct", 35).value
        set(v) { val n = v.coerceIn(0, 100); mem("pet_timed_pct", 35).value = n; put("pet_timed_pct", n) }

    /** 双击查余额 */
    var petDblBal: Boolean
        get() = mem("pet_dbl_bal", true).value
        set(v) { mem("pet_dbl_bal", true).value = v; put("pet_dbl_bal", v) }

    /** Q 弹音效与音量（%） */
    var petSound: Boolean
        get() = mem("pet_sound", true).value
        set(v) { mem("pet_sound", true).value = v; put("pet_sound", v) }

    var petSoundVol: Int
        get() = mem("pet_sound_vol", 70).value
        set(v) { val n = v.coerceIn(20, 100); mem("pet_sound_vol", 70).value = n; put("pet_sound_vol", n) }

    /** 注入设备使用时长（需使用情况访问权，没授权就跳过） */
    var injectUsage: Boolean
        get() = mem("inject_usage", false).value
        set(v) { mem("inject_usage", false).value = v; put("inject_usage", v) }

    /** 回到前台时用 Shizuku 把无障碍写回系统设置：掉了自动补，不用每次手动点 */
    var autoA11y: Boolean
        get() = mem("auto_a11y", false).value
        set(v) { mem("auto_a11y", false).value = v; put("auto_a11y", v) }

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
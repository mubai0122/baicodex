/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:06
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package b.ai.core.data.profile
import b.ai.core.cfg.app.Cfg
import b.ai.core.cfg.io.Paths
import b.ai.core.cfg.io.Store
import b.ai.core.plugin.skill.Providers

import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 一条模型配置：提供商 + base_url + Key + 模型名 + 该模型支持的能力，可同时存多套随时切换 */
data class ModelProfile(
    val id: String,
    val name: String,
    val providerId: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    /** 设置里勾选过的模型：聊天页的模型卡片只在这几个里挑，可同时存多条 */
    val models: List<String> = emptyList(),
    val vision: Boolean = true,
    val toolCall: Boolean = true,
    val reasoning: Boolean = true,
    /** 这条模型是否能自己联网检索（原生 search）：勾上才在请求里声明各家搜索工具 / 参数 */
    val nativeSearch: Boolean = false,
    /** 是否接受创造性采样参数（temperature / top_p / top_k）：个别厂商不认这些字段，一发就 400 */
    val creative: Boolean = true,
    /** 是否接受重复控制参数（presence / frequency / repeat penalty）：同上，不认就别发 */
    val repeatCtl: Boolean = true,
    val maxTokens: Int = 0,
    /** 这条配置自己的上下文窗口（token）；0 表示跟随全局「模型窗口」设置 */
    val ctxTotal: Int = 0,
    /** 名字是否用户自定义：false 时始终按「厂商 · 模型」显示，改模型名字跟着变 */
    val custom: Boolean = false,
    /** 这条模型认的思考档位（逗号分隔）；空 = 按厂商/型号自动推断 */
    val thinkLevels: String = "",
    /** 档位明细（JSON：显示名 / 写入路径 / 写入值），与 thinkLevels 一起存 */
    val thinkLevelsJson: String = "",
    /**
     * 接口协议：chat = OpenAI Chat Completions（/chat/completions）；
     * responses = OpenAI Responses API（/responses）。
     * 按每条配置独立保存——同一家厂商可能只有部分模型要求新协议。
     */
    val protocol: String = "chat"
) {
    val autoLabel: String get() = Profiles.autoLabel(providerId, model)
    val label: String get() = if (custom && name.isNotBlank()) name else autoLabel
}

/** 模型配置库：整表 JSON 落在数据根 models.json，选中项把值灌进 Cfg 供请求链读取 */
object Profiles {
    const val FILE = "models.json"

    private val cache = mutableStateOf<List<ModelProfile>>(emptyList())

    val list: List<ModelProfile> get() = cache.value

    val selected: ModelProfile?
        get() = cache.value.firstOrNull { it.id == Cfg.profileId } ?: cache.value.firstOrNull()

    /** 按 id 取一条配置：会话级后端绑定要用它，别把整表暴露出去 */
    fun byId(id: String): ModelProfile? = cache.value.firstOrNull { it.id == id }

    /**
     * 余额查询来源（DeepSeek 官方 /user/balance 只认官方 Key）：
     * ① 用户在「余额查询来源」里显式指定过、且该配置还在 → 用它；
     * ② 否则自动：优先 providerId == deepseek 的那条（列表顺序 = 添加顺序，越早越靠前）；
     * ③ 再否则退列表第一条（= 最先添加 / 社区列表最上面）；
     * ④ 一条配置都没有 → null，调用方直接跳过查询。
     */
    fun balanceSource(): ModelProfile? {
        val list = cache.value
        if (list.isEmpty()) return null
        val picked = Cfg.balProfileId
        if (picked.isNotBlank()) list.firstOrNull { it.id == picked }?.let { return it }
        return list.firstOrNull { it.providerId == Providers.DEEPSEEK } ?: list.first()
    } //余额来源

    fun init() {
        //外部存储没挂上（direct boot / 开机自启 / 后台起进程）：ensure() 会退回私有目录，
        //这时"读不到文件"不等于用户没配过，一律不读盘也不落盘，等界面回来再重试
        if (!Paths.externalOk()) {
            deferred = true
            if (cache.value.isEmpty()) cache.value = listOf(legacy())
            if (cache.value.none { it.id == Cfg.profileId }) Cfg.profileId = cache.value.first().id
            apply(cache.value.first { it.id == Cfg.profileId })
            return
        }
        deferred = false
        val f = Paths.file(FILE)
        val exists = f.isFile
        val raw = if (exists) runCatching { f.readText() }.getOrNull() else null
        val parsed = raw?.let { runCatching { parse(it) }.getOrNull() }
        //两种异常分开：内容坏了解析不出来（已备份 .bad，允许覆盖）/ 根本读不出来（不许覆盖，等权限修好）
        corrupt = exists && raw != null && parsed == null
        unreadable = exists && raw == null
        if (corrupt || unreadable) runCatching { f.copyTo(File(f.parentFile, "$FILE.bad"), overwrite = true) }
        //真源判定：数据根里那份是不是最后一次保存写进去的（modelsFileAt == modelsRev）。
        //数据根文件的属主被系统改成 media_rw 之后本应用只有读权限，写盘会一直失败；
        //这时只信文件就等于每次冷启动都把配置回滚成旧档，所以改成分片快照兜底
        val snap = Cfg.modelsSnap
        val fromSnap = if (snap.isNotBlank()) runCatching { parse(snap) }.getOrNull() else null
        val fileOk = parsed != null && Cfg.modelsFileAt == Cfg.modelsRev
        cache.value = when {
            fileOk -> parsed!!
            fromSnap != null -> fromSnap
            parsed != null && parsed.isNotEmpty() -> parsed
            corrupt || unreadable -> cache.value.ifEmpty { listOf(legacy()) }
            else -> listOf(legacy()) //文件本来就没有：全新安装，或从单条配置升级上来
        }
        if (cache.value.none { it.id == Cfg.profileId }) Cfg.profileId = cache.value.first().id
        //Key一律搬到私有 SP：数据根 models.json 在公共存储里，别的应用读得到，不能放明文
        cache.value.forEach { p ->
            if (Cfg.profileKey(p.id).isBlank() && p.apiKey.isNotBlank()) Cfg.setProfileKey(p.id, p.apiKey)
        }
        //无论文件在不在都落一次盘：既补默认项，也把老文件里的明文 Key 洗掉
        //老版本把自动名也写成了"自定义名"，这里降一次级：名字正好等于自动名的重新跟随模型
        if (Cfg.profileSeed < 1) {
            cache.value = cache.value.map { p ->
                if (p.custom && p.name == autoLabel(p.providerId, p.model)) p.copy(custom = false) else p
            }
            Cfg.profileSeed = 1
        }
        //坏档只备份不覆盖：这次先拿默认条目撑着，用户重新配完下次启动还能读到好档
        if (!blocked()) save()
        apply(cache.value.first { it.id == Cfg.profileId })
        //总结 / 标题用模型指着的那条已经不在表里（被删 / 没读出来）就回落「跟随当前聊天」，
        //否则下拉显示的是跟随，实际请求还在用一个不存在的配置
        if (Cfg.summaryProfileId.isNotBlank() && cache.value.none { it.id == Cfg.summaryProfileId }) {
            Cfg.summaryProfileId = ""
        }
        if (Cfg.titleProfileId.isNotBlank() && cache.value.none { it.id == Cfg.titleProfileId }) {
            Cfg.titleProfileId = ""
        }
    } //读盘、迁 Key 到私有 SP、落盘

    /** 磁盘档内容解析不出来（坏档）：已备份成 .bad，下一次保存允许覆盖 */
    private var corrupt = false

    /** 磁盘档能 stat 到却读不出来（不归本应用所有 / 无读权限）：本次会话只读，绝不覆盖 */
    private var unreadable = false

    /** 外部存储还没挂上就起进程：读盘延后，等界面回来再补一次 */
    private var deferred = false

    /** 上一次写盘的结果：空串 = 落盘成功，否则是给界面看的错误说明 */
    var lastError: String = ""
        private set

    private fun blocked(): Boolean = unreadable || deferred

    /** 外部存储晚挂载（开机自启 / 后台起进程）时补一次读盘：界面回来后调，幂等 */
    fun retry() {
        //延后读盘与读不出来都要能救回来：权限/属主修好后回到应用就自动恢复，不用重启。
        //上次写盘失败（例如用户没给「所有文件访问」）同样要重试：否则配置只活在私有快照里，
        //用户在设置页看不到任何变化，只能靠重启触发
        if (blocked() || writeFailed) init()
    } //延后读盘与只读状态的重试

    /** 上一次落盘是否失败：决定 retry() 要不要再写一遍 */
    private var writeFailed = false

    private fun legacy(): ModelProfile = ModelProfile(
        id = "default",
        name = "",
        providerId = Cfg.providerId,
        baseUrl = Cfg.baseUrl,
        apiKey = Cfg.apiKey,
        model = Cfg.model,
        models = if (Cfg.model.isBlank()) emptyList() else listOf(Cfg.model),
        vision = Cfg.modelVision,
        toolCall = Cfg.modelToolCall,
        reasoning = Cfg.modelReasoning,
        nativeSearch = Cfg.modelNativeSearch,
        creative = Cfg.modelCreative,
        repeatCtl = Cfg.modelRepeat,
        maxTokens = Cfg.maxTokens,
        protocol = Cfg.modelProtocol
    )

    private fun parse(raw: String): List<ModelProfile> {
        if (raw.isBlank()) return emptyList()
        val arr = JSONArray(raw)
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id")
            if (id.isBlank()) return@mapNotNull null
            val prov = o.optString("providerId", o.optString("provider_id", "custom"))
            val modelName = o.optString("model", "")
            val name = o.optString("name")
            ModelProfile(
                id = id,
                name = name,
                providerId = prov,
                baseUrl = o.optString("baseUrl", o.optString("base_url", "")),
                //Key 只在私有 SP 里；老文件可能还留着明文字段，读出来是为了迁移
                apiKey = Cfg.profileKey(id).ifBlank { o.optString("apiKey", o.optString("api_key", "")) },
                model = modelName,
                //老文件没有 models 字段：把当前那条模型当成已勾选的唯一一条
                models = o.optJSONArray("models")?.let { a ->
                    (0 until a.length()).mapNotNull { j -> a.optString(j).takeIf { it.isNotBlank() } }
                } ?: listOfNotNull(modelName.takeIf { it.isNotBlank() }),
                vision = o.optBoolean("vision", true),
                toolCall = o.optBoolean("toolCall", o.optBoolean("tool_call", true)),
                reasoning = o.optBoolean("reasoning", true),
                //老文件没有这个字段：按厂商推断，别把 DeepSeek 这类默认关掉
                nativeSearch = o.optBoolean("nativeSearch", supportsNativeSearch(prov)),
                //老配置没有这两个键：默认 true，保持升级前“该发的都发”的行为
                creative = o.optBoolean("creative", true),
                repeatCtl = o.optBoolean("repeatCtl", true),
                maxTokens = o.optInt("maxTokens", o.optInt("max_tokens", 0)),
                ctxTotal = o.optInt("ctxTotal", 0),
                //档位以前只在内存里，没落盘，重启就丢（用户反馈「填了没用」）；现在跟配置一起存
                thinkLevels = o.optString("thinkLevels", ""),
                thinkLevelsJson = o.optString("thinkLevelsJson", ""),
                //老文件没这个字段：名字正好等于自动名就算自动名，跟着模型走
                custom = o.optBoolean("custom", name.isNotBlank() && name != autoLabel(prov, modelName)),
                //老档没这个字段：一律当 Chat Completions，行为与升级前完全一致
                protocol = o.optString("protocol", "chat").takeIf { it.isNotBlank() } ?: "chat"
            )
        }
    } //从 JSON 还原（Key 从私有 SP 取）

    private fun save() {
        //真正不能写的只剩两种：文件读不出来（不归本应用所有）/ 外部存储还没挂上。
        //坏档在 init 里已备份成 .bad，不再挡着保存
        if (blocked()) {
            lastError = if (unreadable)
                "没写进磁盘：${Paths.file(FILE).absolutePath} 读不出来（文件不归本应用所有或无读权限）；权限修好后回到应用会自动重试"
            else
                "没写进磁盘：外部存储还没挂上，挂上后回到应用会自动重试"
            return
        }
        val arr = JSONArray()
        cache.value.forEach { p ->
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("providerId", p.providerId)
                    .put("baseUrl", p.baseUrl)
                    //Key 不落这里：models.json 在公共存储，明文 Key 会被别的应用读到
                    .put("model", p.model)
                    .put("models", JSONArray(p.models))
                    .put("vision", p.vision)
                    .put("toolCall", p.toolCall)
                    .put("reasoning", p.reasoning)
                    .put("nativeSearch", p.nativeSearch)
                    .put("creative", p.creative)
                    .put("repeatCtl", p.repeatCtl)
                    .put("maxTokens", p.maxTokens)
                    .put("ctxTotal", p.ctxTotal)
                    .put("thinkLevels", p.thinkLevels)
                    .put("thinkLevelsJson", p.thinkLevelsJson)
                    .put("custom", p.custom)
                    .put("protocol", p.protocol)
            )
        }
        val json = arr.toString(2)
        val rev = System.currentTimeMillis()
        //先落分片快照（私有目录一定写得进），再尽力写数据根：
        //数据根写不动时配置也不会丢，下次冷启动按快照把整表捞回来
        Cfg.modelsSnap = json
        Cfg.modelsRev = rev
        //写失败不许当成功：以前这里返回值直接丢掉，界面照样提示「已保存」，重启才发现没了
        lastError = Store.why(FILE, json)
        Cfg.modelsFileAt = if (lastError.isEmpty()) rev else 0L
        writeFailed = lastError.isNotEmpty()
    } //整表落盘

    /** 切换配置：整组提供商 / Key / 模型 / 能力一起换掉 */
    fun select(id: String) {
        val p = cache.value.firstOrNull { it.id == id } ?: return
        Cfg.profileId = id
        apply(p)
    } //切换

    /**
     * 把某个 GGUF 设成聊天用的后端：没有对应的「本地模型」配置就新建一条并选中。
     * 本地模型页加载成功后调这里——否则用户加载完模型回去聊天，后端还停在云端，
     * 又会弹「请填 base_url / API key」。返回配置名给界面提示用。
     */
    fun useLocal(f: File): String {
        val exist = cache.value.firstOrNull { it.providerId == Providers.LOCAL && it.model == f.name }
        val p = exist ?: ModelProfile(
            id = "p" + System.currentTimeMillis(),
            name = "",
            providerId = Providers.LOCAL,
            baseUrl = "",
            apiKey = "",
            model = f.name,
            models = listOf(f.name),
            //本地小模型默认关掉视觉 / 工具调用 / 推理链，免得请求带上用不了的能力
            vision = false,
            toolCall = false,
            reasoning = false,
            nativeSearch = false,
            protocol = "chat"
        ).also { upsert(it) }
        select(p.id)
        //文件可能不在公共模型目录（用户从别处导入后加载的）：select 里找不到会清空，这里显式兜住
        Cfg.llmLocal = true
        Cfg.llmModel = f.absolutePath
        return p.label
    } //加载模型即切成聊天后端

    private fun apply(p: ModelProfile) {
        Cfg.providerId = p.providerId
        Cfg.baseUrl = p.baseUrl
        Cfg.apiKey = p.apiKey
        Cfg.model = p.model
        //本地模型这条：模型名是 GGUF 文件名，切过来就直接打开公共目录里的那个文件
        if (p.providerId == Providers.LOCAL) {
            Cfg.llmLocal = true
            val f = File(b.ai.core.llm.ModelHub.dir(), p.model)
            if (f.isFile) {
                Cfg.llmModel = f.absolutePath
            } else if (File(Cfg.llmModel).name != p.model) {
                //公共目录里已经没有这个文件：清掉旧路径，否则会拿上一条模型的文件冒充当前这条
                Cfg.llmModel = ""
            }
        } else {
            Cfg.llmLocal = false
        }
        Cfg.profileModels = p.models
        Cfg.modelVision = p.vision
        Cfg.modelToolCall = p.toolCall
        Cfg.modelReasoning = p.reasoning
        Cfg.modelNativeSearch = p.nativeSearch
        Cfg.modelCreative = p.creative
        Cfg.modelRepeat = p.repeatCtl
        Cfg.maxTokens = p.maxTokens
        //窗口不写进全局那份设置：全局是用户手填的默认值，这条配置没填（0）时继续用它
        Cfg.profCtxTotal = p.ctxTotal
        Cfg.profThinkLevels = p.thinkLevels
        Cfg.profThinkLevelsJson = p.thinkLevelsJson
        Cfg.profileName = p.name
        Cfg.profileCustom = p.custom
        //协议也按配置走：切到哪条就用哪条的协议，别让上一条的 Responses 影响这条
        Cfg.modelProtocol = b.ai.net.api.llm.LlmClient.protocolOf(p.protocol)
    } //把配置灌进全局

    /** 自动名：厂商 · 模型，配置没起名时用它 */
    fun autoLabel(providerId: String, model: String): String {
        //只显示模型名：输入区胶囊位置紧张，供应商名冗余（模型名本身已能区分），没有模型名时才退回厂商名
        return model.trim().ifBlank { Providers.byId(providerId).name }
    } //自动名

    /**
     * 厂商默认是否有「原生 search」：自家 API 已提供内置联网工具的那几家默认勾上，
     * 中转 / 聚合站（OpenRouter、硅基流动、Groq、Together、MiMo、自定义）默认不勾，避免未知 tools 直接 400。
     */
    fun supportsNativeSearch(providerId: String): Boolean = when (providerId) {
        "openai", "deepseek", "moonshot", "dashscope", "zhipu" -> true
        else -> false
    } //原生 search 默认值

    /**
     * 这家厂商的原生联网搜索是不是「必须在思考模式下」：
     * DeepSeek 官方硬约束（实测 400）——client tools 只能和 deepseek_web_search 一起、且在思考模式使用。
     * 所以这家不是「思考与搜索互斥」，而是「搜索必须先开思考」。
     */
    fun searchNeedsThink(providerId: String): Boolean =
        providerId.contains("deepseek", true) || b.ai.core.cfg.app.Cfg.baseUrl.contains("deepseek", true)

    /** 把当前 Cfg 的状态写回选中条目：实时跟随，改一个字段就落一次 */
    fun capture() {
        val cur = selected ?: return
        //Key 为空时不能拿空值覆盖：Vault 在启动早期还没解出来、或桶被锁时读到的是空串，
        //一次无脑 capture 就会把用户原来的 Key 永久清掉（这是"Key 重启后没了"的一条真实路径）。
        //要清 Key 只有一条路：删掉这条配置（remove 里显式写空）。
        val key = if (Cfg.apiKey.isBlank()) cur.apiKey else Cfg.apiKey
        //Key 单独进私有 SP，文件里只留非敏感字段
        Cfg.setProfileKey(cur.id, key)
        val custom = Cfg.profileCustom && Cfg.profileName.isNotBlank()
        val p = cur.copy(
            name = if (custom) Cfg.profileName.trim() else autoLabel(Cfg.providerId, Cfg.model),
            custom = custom,
            providerId = Cfg.providerId,
            baseUrl = Cfg.baseUrl,
            apiKey = key,
            model = Cfg.model,
            models = Cfg.profileModels,
            vision = Cfg.modelVision,
            toolCall = Cfg.modelToolCall,
            reasoning = Cfg.modelReasoning,
            nativeSearch = Cfg.modelNativeSearch,
            creative = Cfg.modelCreative,
            repeatCtl = Cfg.modelRepeat,
            maxTokens = Cfg.maxTokens,
            ctxTotal = Cfg.profCtxTotal,
            thinkLevels = Cfg.profThinkLevels.trim(),
            thinkLevelsJson = Cfg.profThinkLevelsJson,
            protocol = Cfg.modelProtocol
        )
        upsert(p)
    } //当前值写回

    /** 设置里勾选完模型：写回当前配置并灌进 Cfg，聊天页卡片据此列可选模型 */
    fun setModels(list: List<String>) {
        val cur = selected ?: return
        val clean = list.distinct().filter { it.isNotBlank() }
        Cfg.profileModels = clean
        //当前模型被取消勾选就落到剩下的第一条，免得请求一个已经不在列表里的模型
        if (Cfg.model.isBlank() || Cfg.model !in clean) Cfg.model = clean.firstOrNull() ?: Cfg.model
        upsert(cur.copy(models = clean, model = Cfg.model))
    } //保存勾选结果

    fun upsert(p: ModelProfile): Boolean {
        val exists = cache.value.any { it.id == p.id }
        cache.value = if (exists) cache.value.map { if (it.id == p.id) p else it } else cache.value + p
        save()
        return lastError.isEmpty()
    } //新增或覆盖，返回是否真写进了磁盘

    /** 复制当前配置成一条新的，方便同厂商加第二个模型 */
    fun duplicateCurrent(): String {
        val cur = selected
        val id = "p${System.currentTimeMillis()}"
        val p = ModelProfile(
            id = id,
            name = "",
            providerId = cur?.providerId ?: Cfg.providerId,
            baseUrl = cur?.baseUrl ?: Cfg.baseUrl,
            apiKey = cur?.apiKey ?: Cfg.apiKey,
            model = cur?.model ?: Cfg.model,
            models = cur?.models ?: emptyList(),
            vision = cur?.vision ?: Cfg.modelVision,
            toolCall = cur?.toolCall ?: Cfg.modelToolCall,
            reasoning = cur?.reasoning ?: Cfg.modelReasoning,
            nativeSearch = cur?.nativeSearch ?: Cfg.modelNativeSearch,
            creative = cur?.creative ?: Cfg.modelCreative,
            repeatCtl = cur?.repeatCtl ?: Cfg.modelRepeat,
            maxTokens = cur?.maxTokens ?: Cfg.maxTokens,
            ctxTotal = cur?.ctxTotal ?: 0,
            custom = false,
            protocol = cur?.protocol ?: Cfg.modelProtocol
        )
        upsert(p.copy(name = ""))
        //新条目沿用当前 Key（同样只进私有 SP）
        Cfg.setProfileKey(id, Cfg.apiKey)
        select(id)
        return id
    } //新增一条

    /** 新样式：新增一条空白供应商配置，选完厂商再填 Key 就行（模型交给列表挑） */
    fun add(): String {
        val id = "p${System.currentTimeMillis()}"
        upsert(
            ModelProfile(
                id = id,
                name = "",
                providerId = "custom",
                baseUrl = "",
                apiKey = "",
                model = "",
                models = emptyList(),
                vision = Cfg.modelVision,
                toolCall = Cfg.modelToolCall,
                reasoning = Cfg.modelReasoning,
                nativeSearch = Cfg.modelNativeSearch,
                creative = Cfg.modelCreative,
                repeatCtl = Cfg.modelRepeat,
                maxTokens = 0,
                ctxTotal = 0,
                custom = false,
                protocol = Cfg.modelProtocol
            )
        )
        Cfg.setProfileKey(id, "")
        select(id)
        return id
    } //新增空白配置

    fun rename(id: String, name: String) {
        val n = name.trim()
        cache.value = cache.value.map { if (it.id == id) it.copy(name = n, custom = n.isNotBlank()) else it }
        save()
    } //改名

    /** 删除；删的是当前选中项就自动切到剩下的第一条 */
    fun remove(id: String) {
        if (cache.value.size <= 1) return
        cache.value = cache.value.filterNot { it.id == id }
        Cfg.setProfileKey(id, "")
        save()
        if (Cfg.profileId == id) cache.value.firstOrNull()?.let { select(it.id) }
    } //删除
} //模型配置库
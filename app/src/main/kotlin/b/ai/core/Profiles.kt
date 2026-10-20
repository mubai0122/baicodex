package b.ai.core

import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject

/** 一条模型配置：提供商 + base_url + Key + 模型名 + 该模型支持的能力，可同时存多套随时切换 */
data class ModelProfile(
    val id: String,
    val name: String,
    val providerId: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val vision: Boolean = true,
    val toolCall: Boolean = true,
    val reasoning: Boolean = true,
    val maxTokens: Int = 0,
    /** 名字是否用户自定义：false 时始终按「厂商 · 模型」显示，改模型名字跟着变 */
    val custom: Boolean = false
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

    fun init() {
        val fromFile = Store.read(FILE)
        cache.value = if (fromFile != null) {
            runCatching { parse(fromFile) }.getOrDefault(emptyList())
        } else {
            //升级用户：把原来那套单条配置直接变成第一条
            listOf(legacy())
        }
        if (cache.value.isEmpty()) cache.value = listOf(legacy())
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
        save()
        apply(cache.value.first { it.id == Cfg.profileId })
    } //读盘、迁 Key 到私有 SP、落盘

    private fun legacy(): ModelProfile = ModelProfile(
        id = "default",
        name = "",
        providerId = Cfg.providerId,
        baseUrl = Cfg.baseUrl,
        apiKey = Cfg.apiKey,
        model = Cfg.model,
        vision = Cfg.modelVision,
        toolCall = Cfg.modelToolCall,
        reasoning = Cfg.modelReasoning,
        maxTokens = Cfg.maxTokens
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
                vision = o.optBoolean("vision", true),
                toolCall = o.optBoolean("toolCall", o.optBoolean("tool_call", true)),
                reasoning = o.optBoolean("reasoning", true),
                maxTokens = o.optInt("maxTokens", o.optInt("max_tokens", 0)),
                //老文件没这个字段：名字正好等于自动名就算自动名，跟着模型走
                custom = o.optBoolean("custom", name.isNotBlank() && name != autoLabel(prov, modelName))
            )
        }
    } //从 JSON 还原（Key 从私有 SP 取）

    private fun save() {
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
                    .put("vision", p.vision)
                    .put("toolCall", p.toolCall)
                    .put("reasoning", p.reasoning)
                    .put("maxTokens", p.maxTokens)
                    .put("custom", p.custom)
            )
        }
        Store.write(FILE, arr.toString(2))
    } //整表落盘

    /** 切换配置：整组提供商 / Key / 模型 / 能力一起换掉 */
    fun select(id: String) {
        val p = cache.value.firstOrNull { it.id == id } ?: return
        Cfg.profileId = id
        apply(p)
    } //切换

    private fun apply(p: ModelProfile) {
        Cfg.providerId = p.providerId
        Cfg.baseUrl = p.baseUrl
        Cfg.apiKey = p.apiKey
        Cfg.model = p.model
        Cfg.modelVision = p.vision
        Cfg.modelToolCall = p.toolCall
        Cfg.modelReasoning = p.reasoning
        Cfg.maxTokens = p.maxTokens
        Cfg.profileName = p.name
        Cfg.profileCustom = p.custom
    } //把配置灌进全局

    /** 自动名：厂商 · 模型，配置没起名时用它 */
    fun autoLabel(providerId: String, model: String): String {
        val prov = Providers.byId(providerId).name
        return if (model.isBlank()) prov else "$prov · $model"
    } //自动名

    /** 把当前 Cfg 的状态写回选中条目：实时跟随，改一个字段就落一次 */
    fun capture() {
        val cur = selected ?: return
        //Key 单独进私有 SP，文件里只留非敏感字段
        Cfg.setProfileKey(cur.id, Cfg.apiKey)
        val custom = Cfg.profileCustom && Cfg.profileName.isNotBlank()
        val p = cur.copy(
            name = if (custom) Cfg.profileName.trim() else autoLabel(Cfg.providerId, Cfg.model),
            custom = custom,
            providerId = Cfg.providerId,
            baseUrl = Cfg.baseUrl,
            apiKey = Cfg.apiKey,
            model = Cfg.model,
            vision = Cfg.modelVision,
            toolCall = Cfg.modelToolCall,
            reasoning = Cfg.modelReasoning,
            maxTokens = Cfg.maxTokens
        )
        upsert(p)
    } //当前值写回

    fun upsert(p: ModelProfile) {
        val exists = cache.value.any { it.id == p.id }
        cache.value = if (exists) cache.value.map { if (it.id == p.id) p else it } else cache.value + p
        save()
    } //新增或覆盖

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
            vision = cur?.vision ?: Cfg.modelVision,
            toolCall = cur?.toolCall ?: Cfg.modelToolCall,
            reasoning = cur?.reasoning ?: Cfg.modelReasoning,
            maxTokens = cur?.maxTokens ?: Cfg.maxTokens,
            custom = false
        )
        upsert(p.copy(name = ""))
        //新条目沿用当前 Key（同样只进私有 SP）
        Cfg.setProfileKey(id, Cfg.apiKey)
        select(id)
        return id
    } //新增一条

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
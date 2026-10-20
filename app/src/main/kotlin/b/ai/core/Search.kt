package b.ai.core

/** 一个平台需要的配置项；bing 这种免 Key 的平台就是空表 */
data class KeyField(val id: String, val label: String, val hint: String)

/**
 * 一个搜索平台。id 与 net 层实现一一对应（tavily / bing / google），
 * 表里有平台、且用户选了、且该填的配置都填了，web_search 才会下发给模型。
 */
data class SearchProvider(
    val id: String,
    val label: String,
    val hint: String,
    val fields: List<KeyField>
)

/**
 * 搜索平台注册表。
 * 平台清单由用户后续提供，往 [providers] 里加一行、再在 Tools.search 里加一个分支即可。
 */
object Search {
    //平台清单：加平台在这里加一行，再在 Tools.search 里加一个分支
    val providers: List<SearchProvider> = listOf(
        SearchProvider(
            id = "tavily",
            label = "Tavily",
            hint = "官方 API，每月 1000 次免费额度，返回带摘要的结果。",
            fields = listOf(KeyField("key", "API Key", "tvly-..."))
        ),
        SearchProvider(
            id = "bing",
            label = "Bing",
            hint = "免 Key：走必应网页版 RSS 接口。微软官方 Search API 已于 2025-08-11 退役，" +
                "这里按 RSS 聚合读取，可能被限流，适合个人低频使用。",
            fields = emptyList()
        ),
        SearchProvider(
            id = "google",
            label = "Google",
            hint = "Custom Search JSON API：要 API Key + 搜索引擎 ID(cx)。" +
                "注意 Google 已对该 API 关闭新客户注册，存量 Key 可用到 2027-01-01。",
            fields = listOf(
                KeyField("key", "API Key", "AIza..."),
                KeyField("cx", "搜索引擎 ID (cx)", "012345678901234567890:abcdefghij")
            )
        )
    )

    fun byId(id: String): SearchProvider? = providers.firstOrNull { it.id == id }

    /** 用户选中的平台；null 表示搜索技能没启用 */
    val selected: SearchProvider? get() = byId(Cfg.searchProvider)

    /** 平台 + 该填的配置都齐了才真正可用 */
    val ready: Boolean
        get() {
            val p = selected ?: return false
            return p.fields.all { Cfg.searchCfg(p.id, it.id).isNotBlank() }
        }

    /** 第一个没填的配置项，用来拼提示 */
    fun missing(): KeyField? {
        val p = selected ?: return null
        return p.fields.firstOrNull { Cfg.searchCfg(p.id, it.id).isBlank() }
    }

    val key: String get() = if (Cfg.searchProvider.isBlank()) "" else Cfg.searchCfg(Cfg.searchProvider, "key")
} //搜索平台注册表
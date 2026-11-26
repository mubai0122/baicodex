/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27 06:40:00
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * 网页搜索工具：网关原生搜索之外的第二条路；平台（Tavily / Bing / Google）
 * 与配置在设置里选，见 core/search/WebSearch。
 */

package b.ai.desktop.core.tool.tools

import b.ai.desktop.core.search.SearchProvider
import b.ai.desktop.core.search.WebSearch
import b.ai.desktop.core.tool.Tool
import b.ai.desktop.core.tool.ToolResult
import b.ai.desktop.core.tool.intArg
import b.ai.desktop.core.tool.stringArg
import b.ai.desktop.core.tool.ToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class WebSearchTool(
    /** 当前选中的平台；null = 未启用（工具不该被挂进工具表） */
    private val providerProvider: () -> SearchProvider?,
    /** 平台配置取值：Tavily 的 key / Google 的 key、cx */
    private val cfgProvider: (String) -> String,
    /** 检索范围：general / news */
    private val topicProvider: () -> String,
) : Tool {
    override val spec: ToolSpec
        get() {
            val provider = providerProvider()
            val label = provider?.label ?: "网页搜索"
            return ToolSpec(
                name = "web_search",
                description = "联网搜索（$label）：按查询词返回网页结果与摘要。适合查时效性信息；" +
                    "使用前先确认查询词是完整、独立的一句话。",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put(
                                "query",
                                buildJsonObject {
                                    put("type", "string")
                                    put("description", "搜索查询词")
                                },
                            )
                            put(
                                "max_results",
                                buildJsonObject {
                                    put("type", "integer")
                                    put("description", "返回条数，1-10，默认 5")
                                },
                            )
                            put(
                                "topic",
                                buildJsonObject {
                                    put("type", "string")
                                    put("description", "general 或 news，默认 general；仅 Tavily 支持")
                                },
                            )
                            put(
                                "days",
                                buildJsonObject {
                                    put("type", "integer")
                                    put("description", "topic=news 时限定最近多少天；仅 Tavily 支持")
                                },
                            )
                            put(
                                "search_depth",
                                buildJsonObject {
                                    put("type", "string")
                                    put("description", "basic 或 advanced，默认 basic；仅 Tavily 支持")
                                },
                            )
                        },
                    )
                    put(
                        "required",
                        kotlinx.serialization.json.JsonArray(listOf(kotlinx.serialization.json.JsonPrimitive("query"))),
                    )
                },
            )
        }

    override suspend fun run(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val provider = providerProvider() ?: return@withContext ToolResult(
            false,
            "还没有选择搜索平台：设置 → 扩展 → 联网搜索",
        )
        val query = args.stringArg("query")
        if (query.isBlank()) return@withContext ToolResult(false, "缺少 query 参数")
        val out = WebSearch.search(
            provider = provider,
            cfg = cfgProvider,
            query = query,
            maxResults = (args.intArg("max_results") ?: 5).coerceIn(1, 10),
            topic = topicProvider().ifBlank { args.stringArg("topic").ifBlank { "general" } },
            days = args.intArg("days"),
            depth = args.stringArg("search_depth").ifBlank { "basic" },
        )
        ToolResult(!out.startsWith("搜索失败") && !out.startsWith("未配置"), out)
    }
}

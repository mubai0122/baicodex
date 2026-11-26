/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27 01:00:22
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * 思考链路自检：gradle run --args="--selftest-thinking"
 * 用当前配置真实发三次请求，看档位是否跟随、思考内容是否回得来。
 */

package b.ai.desktop.dev

import b.ai.desktop.core.config.ConfigStore
import b.ai.desktop.core.model.RunOptions
import b.ai.desktop.core.net.OpenAiChatClient
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val QUESTION = "9.11 和 9.9 哪个大？先想清楚再答，只要一句结论。"

suspend fun runThinkingSelftest() {
    val profile = ConfigStore.load().activeProfile
    if (profile == null || !profile.ready) {
        println("未配置模型，先填 baseUrl / API Key / 模型名")
        return
    }
    println("模型 ${profile.model}  端点 ${profile.baseUrl}")
    println("-".repeat(48))

    val cases = listOf(
        "思考=关" to RunOptions(thinking = false),
        "思考=低" to RunOptions(thinking = true, effort = "low"),
        "思考=最高" to RunOptions(thinking = true, effort = "max"),
    )

    cases.forEach { (label, options) ->
        val measured = runCatching { ask(options) }.getOrElse { e ->
            println("$label → 失败：${e.message}")
            return@forEach
        }
        println("$label → 思考 ${measured.first} 字 / 正文 ${measured.second} 字")
    }

    println("-".repeat(48))
    println("判读：思考=关 应为 0；最高档的思考字数应明显高于低档。")
}

private suspend fun ask(options: RunOptions): Pair<Int, Int> {
    val profile = ConfigStore.load().activeProfile ?: error("未配置模型")
    val reply = OpenAiChatClient(profile).complete(
        messages = listOf(
            buildJsonObject {
                put("role", "user")
                put("content", QUESTION)
            },
        ),
        tools = emptyList(),
        options = options,
    )
    return reply.reasoning.length to reply.text.length
}

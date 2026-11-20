/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:08
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * Thinking.kt — 「这个模型到底支不支持思考」的唯一判断入口。
 *
 * 判断顺序（先松后紧，宁可让用户在规则表里显式接管，也不硬编码一堆厂商 if）：
 * 1) 思考规则表里有规则命中该模型 → 支持（用户自己配的，最高优先级）
 * 2) 内置白名单命中（DeepSeek 推理款 / OpenAI o 系列与 gpt-5 起 / Qwen3 / GLM-4.5 起 / Kimi K2 思考款 / Gemini 2.5 起）→ 支持
 * 3) 其它一律视为不支持：UI 上不显示思考开关与档位，下发时也不塞思考字段，避免不认识的字段被 400
 */
package b.ai.core.model.chat

import b.ai.net.api.llm.ThinkRules

object Thinking {
    /** 明确不支持思考的关键字：命中就一票否决，避免「deepseek-chat 被当成推理款」这类误判 */
    private val NOT_THINK = listOf(
        "deepseek-chat", "-instruct", "embed", "rerank", "tts", "whisper", "dall-e", "moderation"
    )

    /** 内置的「支持思考」关键字：按 模型名 or 厂商id 任一命中即可 */
    private val THINK_KEYS = listOf(
        "deepseek-reasoner", "deepseek-r1", "reasoner",
        "o1", "o3", "o4", "gpt-5", "-reasoning", "thinking",
        "qwen3", "qwq", "glm-4.5", "glm-4.6", "glm-z1",
        "kimi-k2-thinking", "kimi-thinking", "gemini-2.5", "gemini-3"
    )

    /**
     * 这个模型是否支持思考。
     * providerId 与 model 都做小写包含匹配：中转站经常 providerId 是自定义的，只能靠模型名识别。
     */
    fun supported(providerId: String, model: String): Boolean {
        val m = model.lowercase()
        //1) 规则表命中：用户显式配了就按他的
        if (model.isNotBlank() && ThinkRules.hit(model) != null) return true
        if (model.isBlank()) return false
        //2) 明确不支持的先否决（deepseek-chat 不带推理）
        if (NOT_THINK.any { m.contains(it) }) return false
        //3) 内置白名单
        val pid = providerId.lowercase()
        return THINK_KEYS.any { m.contains(it) || pid.contains(it) }
    } //判断是否支持思考
} //思考能力判断
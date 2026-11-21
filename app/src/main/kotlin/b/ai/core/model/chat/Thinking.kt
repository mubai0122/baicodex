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
 * Thinking.kt — 思考能力的「参考名单」（1.20.1 起不再做任何门控）。
 *
 * 为什么降级：中转站/自建部署的模型名永远列不全，名单一窄就等于替用户
 * 把明确打开的思考悄悄吞掉。现在唯一判据是设置里的开关
 * （模型能力 →「推理 / 思考参数」= Cfg.modelReasoning），UI 与下发都用它。
 *
 * 本文件保留的用途：
 * 1) supported()：给设置页/自检展示「这个名字大概支持思考」的参考值，以及新建配置时的默认建议；
 * 2) 规则表（ThinkRules）命中与否，仍用于决定思考字段写到哪个路径（thinking.type / reasoning_effort ...）。
 *
 * 判定顺序（仅作参考，不参与门控）：
 * 1) 思考规则表里有规则命中该模型 → 参考为支持（用户自己配的，最高优先级）
 * 2) 内置白名单命中（DeepSeek 推理款 / OpenAI o 系列与 gpt-5 起 / Qwen3 / GLM-4.5 起 / Kimi K2 思考款 / Gemini 2.5 起）→ 参考为支持
 * 3) 其它 → 参考为不支持（但 UI 照样显示，由用户自己决定）
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
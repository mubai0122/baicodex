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
 * Baicodex Windows - 一轮请求的运行参数
 * 这些开关以前只活在界面状态里，没进过请求体（滑块拖了也不生效）。
 * 现在统一收在这里，从 ChatState 一路传到 HTTP 层。
 */

package b.ai.desktop.core.model

data class RunOptions(
    /** 深度思考 */
    val thinking: Boolean = false,
    /** 思考强度原始档位（low / medium / high / xhigh / max …） */
    val effort: String = "medium",
    /** 原生联网搜索 */
    val nativeSearch: Boolean = false,
    /** 允许模型调用本地工具 */
    val toolCalls: Boolean = true,
    /** 单条工具输出字符上限；0 表示不限 */
    val toolBudget: Int = 0,
    /** 最大模式：抬高单轮输出上限 */
    val maxMode: Boolean = false,
    /** 允许模型一轮里并行发起多个工具调用 */
    val parallelToolCalls: Boolean = true,
    /** 运行模式（计划/标准/代码/极简/创造），只用于提示词与状态展示 */
    val mode: String = "标准",
) {
    /**
     * 真正下发的 reasoning_effort。原样发档位值：
     * 实测 deepseek-v4 这类上游认 low/medium/high/xhigh/max，提前收敛会让
     * 「拖到最高档」和「中间档」发出同一个值，滑块就又白拖了。
     * 端点不认这个值时会由客户端降级（先退到 high，再不行就不发）。
     */
    val requestEffort: String
        get() = effort.trim().lowercase().ifBlank { "medium" }

    companion object {
        /**
         * 名字像推理模型：不传参数也会思考，关掉时必须显式发 none。
         * 宁可多认几个（不认的会被 400 挡回来再剔除），也别漏。
         */
        fun looksLikeReasoningModel(model: String): Boolean {
            val m = model.lowercase()
            return m.startsWith("o1") || m.startsWith("o3") || m.startsWith("o4") ||
                m.contains("gpt-5") || m.contains("gpt-6") || m.contains("codex") ||
                m.contains("deepseek") || m.contains("reasoning") || m.contains("thinking") ||
                m.contains("qwq") || m.contains("qwen3") || m.contains("glm-4.5") ||
                m.contains("glm-5") || m.contains("kimi-k2") || m.contains("magistral")
        }
    }
}

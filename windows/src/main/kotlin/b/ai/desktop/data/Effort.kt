/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:15
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * 思考强度档位表（与手机端 core/model/chat/Effort.kt 同规则）
 */

package b.ai.desktop.data

object Effort {
    /** 等级顺序：用来把历史档位映射到当前模型认的档位 */
    private val RANK = listOf("none", "minimal", "low", "medium", "high", "xhigh", "max")

    /** 最保守的三档：绝大多数中转站都认 */
    private val SAFE = listOf("low", "medium", "high")

    /**
     * 按厂商与型号给档位表。
     * @param model 模型名（Windows MVP 只有展示名，例如 deepseek-chat / gpt-4o）
     * @param providerId 来源标识，可为空
     * @param custom 配置里手填的档位（逗号分隔），非空时以它为准
     */
    fun levels(model: String, providerId: String = "", custom: String = ""): List<String> {
        val own = custom.split(',', '，').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (own.isNotEmpty()) return own.distinct()

        val pid = providerId.lowercase()
        val m = model.lowercase()
        if (pid.contains("deepseek") || m.contains("deepseek")) return listOf("low", "high", "max")

        val openai = pid.contains("openai") || m.startsWith("gpt-") || m.startsWith("o1") ||
            m.startsWith("o3") || m.startsWith("o4") || m.contains("codex")
        if (!openai) return SAFE

        return when {
            m.contains("gpt-6") || m.contains("5.6") ->
                listOf("low", "medium", "high", "xhigh", "max")

            m.contains("5.2") || m.contains("5.4") || m.contains("5.5") || m.contains("codex-max") ->
                listOf("none", "low", "medium", "high", "xhigh")

            m.contains("5.1") -> listOf("none", "low", "medium", "high")
            m.contains("gpt-5") -> listOf("minimal", "low", "medium", "high")
            else -> SAFE
        }
    }

    /** 把历史档位落到当前表上：优先精确命中，其次取第一个不低于它的档位，最后落到最高档 */
    fun indexOf(levels: List<String>, effort: String): Int {
        if (levels.isEmpty()) return 0
        val want = effort.lowercase()
        val hit = levels.indexOf(want)
        if (hit >= 0) return hit
        val wantRank = RANK.indexOf(want).let { if (it < 0) RANK.indexOf("medium") else it }
        val up = levels.firstOrNull { level ->
            RANK.indexOf(level).let { if (it < 0) 0 else it } >= wantRank
        }
        return levels.indexOf(up ?: levels.last())
    }
}

/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27 01:28:54
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * 上下文用量统计：给顶栏预算环和预算卡提供数字。
 * 估算口径与手机端一致——中日韩字符按 1 token、其余按 3.5 字符 1 token；
 * 服务端回了 usage 的消息优先用真实值。
 */

package b.ai.desktop.core.util

import b.ai.desktop.core.model.ChatMessage
import b.ai.desktop.core.model.MsgRole
import kotlin.math.max

object Tokens {

    /** 单段文本的 token 估算（不追求精确，只要和真实用量同量级） */
    fun estimate(text: String): Int {
        if (text.isEmpty()) return 0
        var cjk = 0
        var other = 0
        for (ch in text) {
            if (ch.code in 0x2E80..0x9FFF || ch.code in 0xF900..0xFAFF || ch.code in 0xFF00..0xFFEF) {
                cjk++
            } else {
                other++
            }
        }
        return cjk + (other + 3) / 4
    }

    /** 一条消息的估算值：正文 + 思考 + 工具参数/输出 */
    fun estimate(message: ChatMessage): Int =
        estimate(message.text) + estimate(message.reasoning) + 24

    /** 一组消息的估算总量 */
    fun estimateAll(messages: List<ChatMessage>): Int = messages.sumOf { estimate(it) }

    /** 服务端回过真实用量的累计值 */
    fun actualSpent(messages: List<ChatMessage>): Int = messages.sumOf { it.tokens }
}

/** 当前会话的上下文占用快照 */
data class CtxStat(
    /** 估算上下文占用 */
    val used: Int,
    /** 预算上限（0 = 未设置） */
    val budget: Int,
    /** 参与上下文的消息条数（不含工具回执） */
    val count: Int,
    /** 本次会话服务端回报的真实 token 累计 */
    val spent: Int,
) {
    val pct: Float get() = if (budget <= 0) 0f else (used.toFloat() / budget).coerceIn(0f, 1f)

    /** 70% 起视为偏满：再长就该开新会话或压缩历史 */
    val nearLimit: Boolean get() = budget > 0 && used.toLong() * 100 / budget >= 70

    companion object {
        fun of(messages: List<ChatMessage>, budget: Int): CtxStat {
            val keep = messages.filter { it.role != MsgRole.SYSTEM || it.text.isNotBlank() }
            return CtxStat(
                used = Tokens.estimateAll(keep),
                budget = max(0, budget),
                count = keep.count { it.role != MsgRole.TOOL },
                spent = Tokens.actualSpent(messages),
            )
        }
    }
}

/** 12,345 → 12.3k；1,234,567 → 1.2M */
fun fmtTokens(value: Int): String = when {
    value >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", value / 1_000_000f)
    value >= 10_000 -> String.format(java.util.Locale.US, "%.1fk", value / 1000f)
    else -> value.toString()
}

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
 * 提示词优化：把用户在输入框里随手写的一句话，按当前运行模式补成结构清晰的指令。
 * 只改写文字，不执行任何工具、不改意图，结果直接回填到输入框（可一键回滚）。
 * 规则与手机端 net/api/llm/PromptOptimizer.kt 保持一致。
 */

package b.ai.desktop.feature.chat.prompt

import b.ai.desktop.feature.chat.AgentMode

/** 优化结果：把「为什么失败」拆开，界面才能给准话，而不是一句「确认已配置模型」 */
sealed interface OptResult {
    data class Ok(val text: String) : OptResult
    data object EmptyInput : OptResult
    data object TooLong : OptResult
    data object NoModel : OptResult
    data object EmptyOut : OptResult
    data class Failed(val reason: String) : OptResult
}

/**
 * 优化后端：接入 dsh / LLM 后实现这一个接口即可，其余逻辑不用动。
 * 未设置时走本地规则兜底（不联网、不阻塞）。
 */
interface PromptOptimizerBackend {
    suspend fun optimize(system: String, user: String, temperature: Float): String
}

object PromptOptimizer {
    /** 交给优化模型的原话上限：再长就不做优化（自己写得很清楚，改了反而丢信息） */
    const val MAX_IN = 4000

    /** 预留：接入真实模型时给这里赋值 */
    var backend: PromptOptimizerBackend? = null

    /**
     * 跑一次优化。失败原因分类型返回（空输入 / 超长 / 网络或服务端报错 / 空结果），
     * mode 决定往哪个方向补：代码模式按代码需求写，其它模式按日常任务写。
     */
    suspend fun run(
        text: String,
        mode: AgentMode,
        attach: List<String> = emptyList(),
        modeName: String = "",
    ): OptResult {
        val src = text.trim()
        if (src.isEmpty()) return OptResult.EmptyInput
        if (src.length > MAX_IN) return OptResult.TooLong

        val engine = backend ?: return OptResult.Ok(localRewrite(src, mode))

        val system = buildSystem(mode)
        val user = buildUser(src, mode, attach, modeName)
        val out = runCatching { engine.optimize(system, user, 0.4f) }
            .getOrElse { e ->
                // 把真实原因带出去：HTTP 状态、鉴权失败、超时、DNS 各自可分辨
                val why = e.message?.trim().orEmpty().ifBlank { e::class.simpleName ?: "未知错误" }
                return OptResult.Failed(why.take(160))
            }
            .trim()
        return if (out.isBlank()) OptResult.EmptyOut else OptResult.Ok(out)
    }

    /** 系统提示：与手机端 PromptStore 的优化要求同义（只输出正文、不新增需求、保留原语言…） */
    private fun buildSystem(mode: AgentMode): String = buildString {
        appendLine("你是一个提示词优化器，只做表达优化，不执行任务。")
        appendLine("1) 只输出改写后的提示词正文，不要解释、不要加引号、不要加「优化后：」这类前缀；")
        appendLine("2) 只优化表达，不新增用户没提的需求，不替他做技术选型或决定性取舍；")
        appendLine("3) 保持用户原本的意图和语言（中文就写中文），不要翻译；")
        appendLine("4) 用户已经写清楚的部分原样保留，别改写成另一件事；")
        appendLine("5) 信息不足以判断关键点时，把待确认点作为一行「请先确认：…」写在最后，不要自己编。")
        if (mode == AgentMode.CODE) {
            appendLine()
            appendLine("场景：代码。补全时优先写清——要改哪个文件/模块、期望行为、约束（技术栈与风格沿用仓库现状）、验收方式（怎么验证改对了）。不要凭空指定库或版本，不确定就列为待确认。")
        } else {
            appendLine()
            appendLine("场景：日常任务。补全时优先写清——目标、已知背景、限制条件、期望的产出形式与长度。不要替用户加需求，也不要写成客套话。")
        }
    }

    /** 用户内容：运行模式 + 附着内容（仅作上下文，不写进结果）+ 原话 */
    private fun buildUser(
        src: String,
        mode: AgentMode,
        attach: List<String>,
        modeName: String,
    ): String = buildString {
        append("运行模式：").append(modeName.ifBlank { mode.label }).append('\n')
        if (attach.isNotEmpty()) {
            append("当前附着内容（只用来理解上下文，不要写进结果）：\n")
            attach.take(12).forEach { append("- ").append(it).append('\n') }
        }
        append("原话：\n").append(src)
    }

    /**
     * 本地兜底：没有配置优化模型时也能给出结构骨架。
     * 不编造内容：只把原话放进「目标」，其余项按模式列成待确认。
     */
    private fun localRewrite(src: String, mode: AgentMode): String {
        val cleaned = src.lines().joinToString("\n") { it.trim() }.trim()
        // 已经是结构化写法：原样返回，避免二次加工丢信息
        if (cleaned.contains("目标：") || cleaned.contains("目标:")) return cleaned
        val body = cleaned.replace(Regex("[ \\t]+"), " ")
        return if (mode == AgentMode.CODE) {
            buildString {
                append("目标：").append(body).append('\n')
                append("请先确认：要改哪个文件或模块、期望行为、验收方式；约束沿用仓库现有技术栈与风格。")
            }
        } else {
            buildString {
                append("目标：").append(body).append('\n')
                append("请先确认：已知背景、限制条件、期望的产出形式与长度。")
            }
        }
    }
}

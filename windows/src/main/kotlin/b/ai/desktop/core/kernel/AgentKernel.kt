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
 * Agent 内核：一次用户输入 → 模型 → 工具 → 模型 … 直到给出最终回答
 */

package b.ai.desktop.core.kernel

import b.ai.desktop.core.approval.ApprovalDecision
import b.ai.desktop.core.approval.ToolGate
import b.ai.desktop.core.approval.ToolLabels
import b.ai.desktop.core.model.ChatMessage
import b.ai.desktop.core.model.MsgRole
import b.ai.desktop.core.model.RunOptions
import b.ai.desktop.core.net.ChatClient
import b.ai.desktop.core.tool.ToolCall
import b.ai.desktop.core.tool.ToolRegistry
import b.ai.desktop.core.tool.ToolResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** 内核跑一轮的结果 */
data class KernelOutcome(
    /** 是否正常收尾（false = 达到工具轮次上限被截断） */
    val finished: Boolean,
    /** 本次产生的全部新消息（含中间工具消息） */
    val messages: List<ChatMessage>,
)

class AgentKernel(
    private val client: ChatClient,
    private val registry: ToolRegistry,
    private val maxRounds: Int = 8,
    /** 本轮运行参数：思考开关/强度、联网、工具、预算 —— 全部要真正下发 */
    private val options: RunOptions = RunOptions(),
    /**
     * 执行前的闸门：读文件放行、写文件要问、系统级命令直接拒。
     * 传 null 表示不做审批（自检脚本、后台任务）。
     */
    private val gate: ((ToolCall) -> ToolGate)? = null,
    /** 需要用户点头时调这里；挂起等界面返回 */
    private val approver: (suspend (ToolCall, ToolGate.Ask) -> ApprovalDecision)? = null,
) {
    /**
     * 跑一轮对话。每产生一条消息就通过 [onMessage] 抛给界面，便于边跑边看；
     * [onStatus] 用于「思考中 / 正在调用 xx」这类状态提示。
     * 网络或协议错误直接抛出，由调用方提示用户。
     */
    suspend fun run(
        systemPrompt: String,
        history: List<ChatMessage>,
        onMessage: (ChatMessage) -> Unit,
        onStatus: (String) -> Unit,
    ): KernelOutcome {
        val produced = mutableListOf<ChatMessage>()
        fun emit(message: ChatMessage) {
            produced += message
            onMessage(message)
        }

        val messages = mutableListOf<JsonObject>()
        messages += buildJsonObject {
            put("role", "system")
            put("content", systemText(systemPrompt))
        }
        history.forEach { messages += it.toApiJson() }

        repeat(maxRounds) { round ->
            onStatus(
                when {
                    round > 0 -> "模型整理结果中…"
                    options.thinking -> "深度思考中（${options.requestEffort}）…"
                    else -> "模型思考中…"
                },
            )
            val started = System.currentTimeMillis()
            val reply = client.complete(
                messages = messages,
                tools = if (options.toolCalls) registry.specs() else emptyList(),
                options = options,
            )
            val elapsed = (System.currentTimeMillis() - started).toInt()

            // 没有工具调用：这就是最终回答
            if (reply.toolCalls.isEmpty()) {
                if (reply.text.isNotBlank() || reply.reasoning.isNotBlank()) {
                    val raw = buildJsonObject {
                        put("role", "assistant")
                        put("content", reply.text)
                    }
                    emit(
                        ChatMessage(
                            role = MsgRole.ASSISTANT,
                            text = reply.text,
                            reasoning = reply.reasoning,
                            raw = raw.toString(),
                            tokens = reply.usage?.totalTokens ?: 0,
                            durationMs = elapsed,
                        ),
                    )
                }
                return KernelOutcome(finished = true, messages = produced)
            }

            // 带工具调用的一轮：先把 assistant 这步（可能含前置说明）落到协议里
            val assistantRaw = buildJsonObject {
                put("role", "assistant")
                put("content", reply.text)
                put(
                    "tool_calls",
                    buildJsonArray {
                        reply.toolCalls.forEach { call ->
                            add(
                                buildJsonObject {
                                    put("id", call.id)
                                    put("type", "function")
                                    put(
                                        "function",
                                        buildJsonObject {
                                            put("name", call.name)
                                            put("arguments", call.args.toString())
                                        },
                                    )
                                },
                            )
                        }
                    },
                )
            }
            messages += assistantRaw
            // 空文本也要落库（协议需要 tool_calls），UI 侧渲染时会跳过空内容
            emit(
                ChatMessage(
                    role = MsgRole.ASSISTANT,
                    text = reply.text,
                    reasoning = reply.reasoning,
                    raw = assistantRaw.toString(),
                    tokens = reply.usage?.totalTokens ?: 0,
                    durationMs = elapsed,
                ),
            )

            // 逐个执行工具，结果按协议回灌；超出预算的输出先截断，免得把上下文撑爆
            reply.toolCalls.forEach { call ->
                onStatus("正在调用 ${call.name}…")
                val result = runGuarded(call, onStatus)
                val output = clipToBudget(result.output)
                val toolRaw = buildJsonObject {
                    put("role", "tool")
                    put("tool_call_id", call.id)
                    put("content", output)
                }
                messages += toolRaw
                emit(
                    ChatMessage(
                        role = MsgRole.TOOL,
                        text = output,
                        toolName = call.name,
                        toolOk = result.ok,
                        raw = toolRaw.toString(),
                    ),
                )
            }
        }

        val note = "已达到单轮工具调用上限（${maxRounds} 次），先停在这里。需要继续的话，再发一句话让我接着做。"
        emit(ChatMessage(role = MsgRole.ASSISTANT, text = note))
        return KernelOutcome(finished = false, messages = produced)
    }

    /**
     * 过闸门再执行：
     *  - Pass   → 直接跑
     *  - Ask    → 等用户点确认；拒绝时把理由当作工具结果回灌，模型能自己改方案
     *  - Reject → 不执行，理由回灌
     */
    private suspend fun runGuarded(call: ToolCall, onStatus: (String) -> Unit): ToolResult {
        val verdict = gate?.invoke(call) ?: return registry.run(call)
        return when (verdict) {
            is ToolGate.Pass -> registry.run(call)
            is ToolGate.Reject -> ToolResult(false, "已拒绝：${verdict.reason}")
            is ToolGate.Ask -> {
                onStatus("等待你确认「${ToolLabels.titleOf(call.name)}」…")
                when (val decision = approver?.invoke(call, verdict)) {
                    is ApprovalDecision.Deny -> ToolResult(
                        false,
                        "用户拒绝执行 ${call.name}（${decision.reason}）。不要重复同一操作，换个做法或先说明你打算做什么。",
                    )
                    null -> ToolResult(false, "无人确认，${call.name} 未执行。")
                    else -> registry.run(call)
                }
            }
        }
    }

    /** 联网搜索是服务端能力，提示词里再点一句，模型才会主动先搜再答 */    private fun systemText(base: String): String =
        if (options.nativeSearch) {
            base.trimEnd() + "\n\n本轮已开启联网搜索：涉及最新信息时先检索再回答，并说明信息来源。"
        } else {
            base
        }

    /** 工具结果预算：0 表示不限 */
    private fun clipToBudget(output: String): String {
        val budget = options.toolBudget
        if (budget <= 0 || output.length <= budget) return output
        return output.take(budget) + "\n…（超出工具结果预算，已截断 ${output.length - budget} 字符）"
    }
}

/** 历史消息 → 下发 JSON：有 raw 按原样回放，否则按 role + 文本重建 */
fun ChatMessage.toApiJson(): JsonObject {
    raw?.let { saved ->
        runCatching { Json.parseToJsonElement(saved).jsonObject }.getOrNull()?.let { return it }
    }
    val content = buildString {
        append(text)
        if (attachments.isNotEmpty()) {
            if (isNotEmpty()) append('\n')
            append("（附件：")
            append(attachments.joinToString("、") { "${it.kind}·${it.name}" })
            append("）")
        }
    }
    return buildJsonObject {
        put("role", role)
        put("content", content)
    }
}

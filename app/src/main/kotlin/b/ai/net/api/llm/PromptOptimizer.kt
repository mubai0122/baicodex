/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:16
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

// 提示词优化：把用户在输入框里随手写的一句话，按当前运行模式补成结构清晰的指令。
// 只改写文字，不执行任何工具、不改意图，结果直接回填到输入框（可一键回滚）。
package b.ai.net.api.llm

import b.ai.core.cfg.app.Cfg
import b.ai.core.model.chat.FnModel
import b.ai.core.plugin.skill.Providers
import b.ai.core.cfg.app.AgentMode
import org.json.JSONArray
import org.json.JSONObject

/** 优化结果：把「为什么失败」拆开，界面才能给准话，而不是一句「确认已配置模型」 */
sealed interface OptResult {
    data class Ok(val text: String) : OptResult
    data object EmptyInput : OptResult
    data object TooLong : OptResult
    data object NoModel : OptResult
    data object EmptyOut : OptResult
    data class Failed(val reason: String) : OptResult
}

object PromptOptimizer {
    /** 交给优化模型的原话上限：再长就不做优化（自己写得很清楚，改了反而丢信息） */
    const val MAX_IN = 4000

    /** 优化用的连接参数：baseUrl / apiKey / providerId / model，单独配了就用自己的，否则跟随当前聊天 */
    private fun conn(): Array<String> = FnModel.conn(FnModel.PROMPT_OPT)

    /**
     * 跑一次优化。失败原因分类型返回（没配模型 / 空输入 / 超长 / 网络或服务端报错 / 空结果），
     * 调用方按类型给准确提示。mode 决定往哪个方向补：代码模式按代码需求写，其它模式按日常任务写。
     */
    suspend fun run(
        text: String,
        mode: AgentMode,
        attach: List<String> = emptyList(),
        modeName: String = ""
    ): OptResult {
        val src = text.trim()
        if (src.isEmpty()) return OptResult.EmptyInput
        if (src.length > MAX_IN) return OptResult.TooLong
        val m = conn()
        if (m[0].isBlank() || m[3].isBlank()) return OptResult.NoModel
        //提示词本身：只讲「怎么改」，不塞任何工具定义，避免模型顺手给出多余内容
        val system = buildString {
            append("你是提示词改写器，只做一件事：把用户的一句话改写成模型能准确照做的指令。\n")
            append("硬性要求：\n")
            append("1) 只输出改写后的提示词正文，不要解释、不要加引号、不要加「优化后：」这类前缀；\n")
            append("2) 只优化表达，不新增用户没提的需求，不替他做技术选型或决定性取舍；\n")
            if (Cfg.promptOptKeep) append("3) 保持用户原本的意图和语言（中文就写中文），不要翻译；\n")
            append("4) 用户已经写清楚的部分原样保留，别改写成另一件事；\n")
            append("5) 信息不足以判断关键点时，把待确认点作为一行「请先确认：…」写在最后，不要自己编。\n")
            if (mode == AgentMode.CODE) {
                append("\n场景：代码。补全时优先写清——要改哪个文件/模块、期望行为、约束（技术栈与风格沿用仓库现状）、")
                append("验收方式（怎么验证改对了）。不要凭空指定库或版本，不确定就列为待确认。\n")
            } else {
                append("\n场景：日常任务。补全时优先写清——目标、已知背景、限制条件、期望的产出形式与长度。")
                append("不要替用户加需求，也不要写成客套话。\n")
            }
            //自定义方向：用户在设置里写死的风格要求，优先级高于上面的默认场景描述
            val style = Cfg.promptOptStyle.trim()
            if (style.isNotEmpty()) append("\n额外的写法要求（必须遵守）：\n").append(style).append('\n')
        } //系统提示
        val user = buildString {
            append("运行模式：").append(modeName.ifBlank { mode.id }).append('\n')
            if (attach.isNotEmpty()) {
                append("当前附着内容（只用来理解上下文，不要写进结果）：\n")
                attach.take(12).forEach { append("- ").append(it).append('\n') }
            }
            append("原话：\n").append(src)
        } //用户内容
        val pay = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", system))
            put(JSONObject().put("role", "user").put("content", user))
        }
        //温度略高一点点，只为了让句式自然，不追求创造性
        //网络必须离开主线程：LlmClient.chat 是阻塞的 OkHttp 调用，直接在 Compose 的 launch 里跑会抛
        //NetworkOnMainThreadException（用户实测就是这个），所以这里显式切到 IO
        val r = runCatching {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                LlmClient(m[0], m[1], Providers.byId(m[2]).auth)
                    .chat(m[3], pay, temperature = 0.4, tools = JSONArray())
            }
        }
        val msg = r.getOrElse { e ->
            //把真实原因带出去：HTTP 状态、鉴权失败、超时、DNS 各自可分辨
            val why = e.message?.trim().orEmpty().ifBlank { e::class.simpleName ?: "未知错误" }
            return OptResult.Failed(why.take(160))
        }
        val out = msg.optString("content").trim()
        return if (out.isBlank()) OptResult.EmptyOut else OptResult.Ok(out)
    } //跑一次优化

    /** 界面上显示的模型名：留空时说明是跟随当前聊天 */
    fun label(): String = FnModel.label(FnModel.PROMPT_OPT)
} //提示词优化
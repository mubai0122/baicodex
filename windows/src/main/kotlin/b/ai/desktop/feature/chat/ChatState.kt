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
 * Baicodex Windows - 聊天区 UI 状态
 * 只放「本轮输入相关的开关与面板可见性」，不含会话数据与消息数据。
 */

package b.ai.desktop.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import b.ai.desktop.core.model.RunOptions
import b.ai.desktop.data.Effort

/** 运行模式（对齐手机端 AgentMode 五档） */
enum class AgentMode(val label: String) {
    PLAN("计划"),
    STANDARD("标准"),
    CODE("代码"),
    MINIMAL("极简"),
    CREATE("创造"),
}

/** 工具结果预算档位（0 表示不限） */
val budgetSteps = listOf(0, 2000, 4000, 8000, 16000, 32000)

class ChatState {
    var agentMode by mutableStateOf(AgentMode.STANDARD)
    var thinkingEnabled by mutableStateOf(false)

    /** 思考强度档位值（low / medium / high / max …），档位表随模型变 */
    var thinkEffort by mutableStateOf("medium")
    var maxMode by mutableStateOf(false)
    var voiceEnabled by mutableStateOf(false)

    var nativeSearch by mutableStateOf(false)
    var toolCalls by mutableStateOf(true)
    var budgetIndex by mutableIntStateOf(0)

    /** 提示词优化前备份；非空表示当前处于「已优化」状态 */
    var promptBackup by mutableStateOf<String?>(null)

    /** 全屏输入 */
    var fullscreen by mutableStateOf(false)

    /** 面板可见性（同一时间只开一个） */
    var showConfig by mutableStateOf(false)
    var showAttach by mutableStateOf(false)

    val budgetText: String
        get() = budgetSteps[budgetIndex].let { if (it == 0) "max" else it.toString() }

    fun toggleConfig() {
        showConfig = !showConfig
        if (showConfig) showAttach = false
    }

    fun toggleAttach() {
        showAttach = !showAttach
        if (showAttach) showConfig = false
    }

    fun closePanels() {
        showConfig = false
        showAttach = false
    }

    /** 换模型后旧档位可能不合法：落到当前表上，免得显示与实际下发不一致 */
    fun ensureEffort(levels: List<String>) {
        if (levels.isEmpty()) return
        if (thinkEffort.lowercase() !in levels) {
            thinkEffort = levels[Effort.indexOf(levels, thinkEffort)]
        }
    }

    fun effortIndex(levels: List<String>): Int = Effort.indexOf(levels, thinkEffort)

    fun cycleBudget() {
        budgetIndex = (budgetIndex + 1) % budgetSteps.size
    }

    /**
     * 本轮真正下发的参数。滑块、开关都在这里落到请求层，
     * 少这一环的话界面改了也只是改了界面。
     */
    fun runOptions(): RunOptions = RunOptions(
        thinking = thinkingEnabled,
        effort = thinkEffort,
        nativeSearch = nativeSearch,
        toolCalls = toolCalls,
        toolBudget = budgetSteps[budgetIndex],
        maxMode = maxMode,
        mode = agentMode.label,
    )
}

/**
 * 初始开关来自设置里的「新会话默认值」：开了默认思考，新会话一进来就是开着的。
 * 之后的临时改动只影响当前会话，不回写设置。
 */
@Composable
fun rememberChatState(config: b.ai.desktop.core.config.AppConfig): ChatState = remember {
    ChatState().apply {
        thinkingEnabled = config.defaultThinking
        thinkEffort = config.defaultEffort.ifBlank { "medium" }
        maxMode = config.defaultMaxMode
        nativeSearch = config.defaultNativeSearch
    }
}

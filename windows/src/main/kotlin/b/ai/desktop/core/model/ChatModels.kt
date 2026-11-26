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
 * 内核数据模型：会话/消息/附件引用（可序列化，直接落盘）
 */

package b.ai.desktop.core.model

import kotlinx.serialization.Serializable
import java.util.UUID

/** 附件引用：只存元信息与本地路径，不内联文件内容 */
@Serializable
data class AttachmentRef(
    val kind: String,
    val name: String,
    val path: String? = null,
)

/** 消息角色：user / assistant / tool / system */
object MsgRole {
    const val USER = "user"
    const val ASSISTANT = "assistant"
    const val TOOL = "tool"
    const val SYSTEM = "system"
}

@Serializable
data class ChatMessage(
    val role: String,
    val text: String = "",
    /** 思考过程：开启深度思考且服务端返回时才有内容（reasoning_content 等） */
    val reasoning: String = "",
    /** 收藏（对齐手机端的长按菜单） */
    val starred: Boolean = false,
    val attachments: List<AttachmentRef> = emptyList(),
    /** 工具消息：工具名 */
    val toolName: String? = null,
    /** 工具消息：是否成功 */
    val toolOk: Boolean = true,
    /**
     * 下发/回放用的原始 JSON（含 tool_calls、tool_call_id）。
     * 有它就按原样回灌给服务端，保证多轮工具调用协议完整；没有就按 role+text 重建。
     */
    val raw: String? = null,
    /** 这条回复服务端回报的 token 用量（没有就是 0） */
    val tokens: Int = 0,
    /** 这条回复的生成耗时（毫秒），0 = 没记 */
    val durationMs: Int = 0,
    /** 生成这条回复的模型名（统计按模型分组用） */
    val model: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class Session(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "新会话",
    var updatedAt: Long = System.currentTimeMillis(),
    var messages: List<ChatMessage> = emptyList(),
    /** 置顶：会话列表里排在未置顶会话之前 */
    var pinned: Boolean = false,
) {
    /** 侧边栏预览：最后一条非工具消息 */
    val preview: String
        get() = messages.lastOrNull { it.role != MsgRole.TOOL }?.text
            ?.replace('\n', ' ')
            ?.take(40)
            ?: "暂无消息"
}

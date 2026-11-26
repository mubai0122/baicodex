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
 * Baicodex Windows - 消息气泡：用户 / 助手 / 工具 / 系统提示
 * 显示项（思考块、时间、最大宽度、紧凑）由设置下发。
 */

package b.ai.desktop.feature.chat

import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.desktop.core.model.ChatMessage
import b.ai.desktop.core.model.MsgRole
import b.ai.desktop.theme.LocalChatType
import b.ai.desktop.ui.menu.contextMenuTarget
import b.ai.desktop.ui.widget.MarkdownText
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 气泡显示参数：都来自设置页 */
data class BubbleViewStyle(
    val showReasoning: Boolean = true,
    val reasoningExpanded: Boolean = false,
    val showTimestamp: Boolean = true,
    /** 回复下方显示 耗时 / token / 模型名 */
    val showMeta: Boolean = false,
    val modelName: String = "",
    val maxWidth: Dp = 560.dp,
    val compact: Boolean = false,
    /** 消息头像（对齐手机端 avatarOn）：AI 与用户气泡旁 28dp 小头像 */
    val avatarOn: Boolean = true,
    /** 用户头像文件（自定义 > GitHub 缓存）；空 = 首字母圆 */
    val userAvatarFile: String? = null,
    /** 用户名首字母兜底 */
    val userInitial: String = "你",
    /** AI 自定义头像文件；空 = 应用图标 */
    val aiAvatarFile: String? = null,
    /** token / 耗时单独开关（对齐手机端 showUsage） */
    val showUsage: Boolean = true,
    /** 胶囊气泡 */
    val capsule: Boolean = false,
)

@Composable
fun MessageBubble(
    msg: ChatMessage,
    style: BubbleViewStyle = BubbleViewStyle(),
    onContextMenu: (Offset) -> Unit = {},
) {
    when (msg.role) {
        MsgRole.TOOL -> ToolCard(msg, style, onContextMenu)
        MsgRole.SYSTEM -> SystemNote(msg, style, onContextMenu)
        else -> NormalBubble(msg, style, onContextMenu)
    }
}

@Composable
private fun NormalBubble(msg: ChatMessage, style: BubbleViewStyle, onContextMenu: (Offset) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val type = LocalChatType.current
    val fromUser = msg.role == MsgRole.USER
    val padH = if (style.compact) 11.dp else 14.dp
    val padV = if (style.compact) 8.dp else 10.dp

    // 胶囊气泡（对齐手机端 bubbleCapsule）：完全圆润；否则按设置的圆角
    val bubbleShape = if (style.capsule) {
        androidx.compose.foundation.shape.RoundedCornerShape(50)
    } else {
        RoundedCornerShape(b.ai.desktop.theme.UiMetrics.bubbleCorner.dp)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start,
    ) {
        if (!fromUser && style.avatarOn) {
            // 与手机端同规格：28dp 圆 + 6dp 间距；自定义头像 > 应用图标
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = colors.surfaceVariant,
                modifier = Modifier.size(28.dp),
            ) {
                val aiBitmap = style.aiAvatarFile?.let { loadAvatar(it) }
                if (aiBitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = aiBitmap,
                        contentDescription = "AI 头像",
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(Modifier.fillMaxSize().padding(3.dp), Alignment.Center) {
                        b.ai.desktop.ui.component.AppIcon(size = 22.dp)
                    }
                }
            }
            Spacer(Modifier.width(6.dp))
        }
        if (fromUser && style.avatarOn) {
            UserAvatar(style)
            Spacer(Modifier.width(6.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = style.maxWidth),
            horizontalAlignment = if (fromUser) Alignment.End else Alignment.Start,
        ) {
            Surface(
                shape = bubbleShape,
                color = if (fromUser) colors.surfaceVariant else colors.surface,
                modifier = Modifier.contextMenuTarget(onContextMenu = onContextMenu),
            ) {
                Column(modifier = Modifier.padding(horizontal = padH, vertical = padV)) {
                    msg.attachments.forEach { attachment ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colors.primary.copy(alpha = 0.10f),
                            modifier = Modifier.padding(bottom = 6.dp),
                        ) {
                            Text(
                                "${attachment.kind}·${attachment.name}",
                                fontSize = 10.sp,
                                color = colors.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                    if (msg.role == MsgRole.ASSISTANT && msg.reasoning.isNotBlank() && style.showReasoning) {
                        ThinkingBlock(msg.reasoning, style.reasoningExpanded)
                        if (msg.text.isNotBlank()) Spacer(Modifier.height(8.dp))
                    }
                    if (msg.text.isNotBlank()) {
                        MarkdownText(
                            src = msg.text,
                            color = colors.onSurface,
                            fontSize = type.bodySize,
                            lineHeight = type.bodyLine,
                            codeFontSize = type.codeSize,
                        )
                    }
                }
            }
            val meta = metaLine(msg, style)
            if (meta != null) {
                Spacer(Modifier.height(3.dp))
                Text(
                    meta,
                    fontSize = 9.sp,
                    color = colors.onSurfaceVariant.copy(alpha = 0.75f),
                )
            }
        }
    }
}

/**
 * 回复下方那行小字：时间 · 模型 · tokens · 耗时。
 * 只有开了「回复信息」才出现；没有真实用量的项直接省略，不编数字。
 */
private fun metaLine(msg: ChatMessage, style: BubbleViewStyle): String? {
    if (!style.showMeta && !style.showTimestamp && !style.showUsage) return null
    val parts = mutableListOf<String>()
    if (style.showTimestamp) parts += clockOf(msg.createdAt)
    if (style.showMeta && style.modelName.isNotBlank() && msg.role == MsgRole.ASSISTANT) parts += style.modelName
    if (style.showUsage) {
        if (msg.tokens > 0) parts += "${msg.tokens} tok"
        if (msg.durationMs > 0) parts += String.format(java.util.Locale.US, "%.1fs", msg.durationMs / 1000f)
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

private val clockFormat = DateTimeFormatter.ofPattern("HH:mm")

private fun clockOf(epochMillis: Long): String = runCatching {
    LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault()).format(clockFormat)
}.getOrDefault("")

/** 工具调用：等宽小卡片，展示工具名与输出 */
@Composable
private fun ToolCard(msg: ChatMessage, style: BubbleViewStyle, onContextMenu: (Offset) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val name = msg.toolName ?: "tool"
    val tone = if (msg.toolOk) colors.primary else colors.error
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Spacer(Modifier.width(36.dp))
        Surface(
            shape = RoundedCornerShape(b.ai.desktop.theme.UiMetrics.bubbleCorner.dp),
            color = colors.surfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier
                .widthIn(max = style.maxWidth)
                .contextMenuTarget(onContextMenu = onContextMenu),
        ) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("●", color = tone, fontSize = 8.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        name,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = tone,
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (msg.toolOk) "成功" else "失败",
                        fontSize = 10.sp,
                        color = colors.onSurfaceVariant,
                    )
                }
                if (msg.text.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        msg.text.take(1200),
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = colors.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

/** 系统提示：居中小胶囊（未配置模型、调用失败等） */
@Composable
private fun SystemNote(msg: ChatMessage, style: BubbleViewStyle, onContextMenu: (Offset) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(b.ai.desktop.theme.UiMetrics.bubbleCorner.dp),
            color = colors.surfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier
                .widthIn(max = style.maxWidth)
                .contextMenuTarget(onContextMenu = onContextMenu),
        ) {
            Text(
                msg.text,
                fontSize = 11.sp,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * 思考过程（对齐手机端 ThinkBlock）：默认只给一行摘要 + 字数，点开看全文。
 * 开启深度思考后这里仍然为空，说明服务端没回 reasoning_content（不是界面吞了）。
 */
@Composable
private fun ThinkingBlock(text: String, initiallyExpanded: Boolean) {
    val colors = MaterialTheme.colorScheme
    var expanded by remember(text, initiallyExpanded) { mutableStateOf(initiallyExpanded) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(b.ai.desktop.theme.UiMetrics.bubbleCorner.dp))
            .background(colors.surfaceVariant.copy(alpha = 0.55f))
            .clickable { expanded = !expanded }
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("◇", fontSize = 10.sp, color = colors.primary)
            Spacer(Modifier.width(6.dp))
            Text("思考过程", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = colors.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(
                if (expanded) "收起" else "${text.length} 字 · 展开",
                fontSize = 10.sp,
                color = colors.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (expanded) text
            else text.replace('\n', ' ').take(90) + if (text.length > 90) "…" else "",
            fontSize = 11.sp,
            lineHeight = 17.sp,
            color = colors.onSurfaceVariant,
            modifier = if (expanded) {
                Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())
            } else {
                Modifier
            },
        )
    }
}

/** 头像位图缓存（同一路径只解码一次，消息多了也不卡） */
private val avatarCache = java.util.concurrent.ConcurrentHashMap<String, androidx.compose.ui.graphics.ImageBitmap>()

private fun loadAvatar(path: String): androidx.compose.ui.graphics.ImageBitmap? =
    avatarCache.getOrPut(path) {
        runCatching {
            org.jetbrains.skia.Image.makeFromEncoded(java.io.File(path).readBytes()).toComposeImageBitmap()
        }.getOrNull()
    }

/** 用户头像：GitHub 头像文件优先，没有就首字母圆（与手机端 AvatarView 同位） */
@Composable
private fun UserAvatar(style: BubbleViewStyle) {
    val colors = MaterialTheme.colorScheme
    val bitmap = style.userAvatarFile?.let { loadAvatar(it) }
    Surface(
        shape = androidx.compose.foundation.shape.CircleShape,
        color = colors.primary.copy(alpha = 0.85f),
        modifier = Modifier.size(28.dp),
    ) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap,
                contentDescription = "用户头像",
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(style.userInitial, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

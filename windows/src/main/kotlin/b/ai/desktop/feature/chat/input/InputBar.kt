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
 * Baicodex Windows - 输入区（对齐手机端 InputBar）
 * 白卡内两行：正文 + 底部一行（模型胶囊 / 配置 / 附件 / 提示词优化），右侧纸飞机发送键。
 */

package b.ai.desktop.feature.chat.input

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.desktop.data.Attachment
import b.ai.desktop.feature.chat.ChatState
import b.ai.desktop.ui.component.chevronRotation
import b.ai.desktop.ui.component.pressScale

@Composable
fun InputBar(
    draft: String,
    onDraftChange: (String) -> Unit,
    attachments: List<Attachment>,
    onRemoveAttachment: (Int) -> Unit,
    chat: ChatState,
    modelName: String,
    showModelSheet: Boolean,
    onToggleModelSheet: () -> Unit,
    onToggleConfig: () -> Unit,
    onToggleAttach: () -> Unit,
    optimizing: Boolean,
    optimized: Boolean,
    onOptimize: () -> Unit,
    onRevert: () -> Unit,
    onSend: () -> Unit,
    sttState: String = "idle",
    onMic: () -> Unit = {},
    promptTags: List<b.ai.desktop.core.config.PromptTag> = emptyList(),
    onInsertTag: (String) -> Unit = {},
    sendOnEnter: Boolean = true,
    placeholder: String = "",
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = colors.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.weight(1f),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                AttachmentChips(attachments = attachments, onRemove = onRemoveAttachment)

                BasicTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp, max = 120.dp)
                        .onKeyEvent { event ->
                            if (event.key != Key.Enter || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                            val trigger = if (sendOnEnter) {
                                !event.isShiftPressed
                            } else {
                                event.isCtrlPressed || event.isMetaPressed
                            }
                            if (trigger) {
                                onSend()
                                true
                            } else {
                                false
                            }
                        },
                    textStyle = TextStyle(color = colors.onSurface, fontSize = 14.sp),
                    decorationBox = { inner ->
                        if (draft.isEmpty()) {
                            Text(
                                placeholder.ifBlank {
                                    if (sendOnEnter) "输入消息，Enter 发送，Shift+Enter 换行"
                                    else "输入消息，Enter 换行，Ctrl+Enter 发送"
                                },
                                color = colors.onSurfaceVariant,
                                fontSize = 13.sp,
                            )
                        }
                        inner()
                    },
                )

                Spacer(Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    ModelCapsule(
                        modelName = modelName,
                        expanded = showModelSheet,
                        onClick = onToggleModelSheet,
                    )

                    Spacer(Modifier.weight(1f))

                    // 配置（三条杠）
                    IconButton(onClick = onToggleConfig, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = "配置",
                            tint = if (chat.showConfig) colors.primary else colors.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    // 附件（＋）
                    IconButton(onClick = onToggleAttach, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "附件",
                            tint = if (chat.showAttach) colors.primary else colors.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    // 语音识别（点一下开录，再点停止并转写；转写中转圈）
                    IconButton(onClick = onMic, modifier = Modifier.size(32.dp)) {
                        when (sttState) {
                            "transcribing" -> CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 1.5.dp,
                                color = colors.primary,
                            )
                            "recording" -> Icon(
                                Icons.Default.Stop,
                                contentDescription = "停止录音并转写",
                                tint = colors.error,
                                modifier = Modifier.size(18.dp),
                            )
                            else -> Icon(
                                Icons.Default.KeyboardVoice,
                                contentDescription = "语音输入",
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    // 提示词标签：设置 → 对话 → 提示词标签 里维护
                    if (promptTags.isNotEmpty()) {
                        var tagsOpen by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { tagsOpen = true }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    Icons.Default.Style,
                                    contentDescription = "插入提示词标签",
                                    tint = colors.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            androidx.compose.material3.DropdownMenu(
                                expanded = tagsOpen,
                                onDismissRequest = { tagsOpen = false },
                                containerColor = colors.surface,
                                modifier = Modifier.heightIn(max = 280.dp),
                            ) {
                                promptTags.forEach { tag ->
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(tag.label, fontSize = 12.sp, color = colors.onSurface) },
                                        onClick = {
                                            tagsOpen = false
                                            onInsertTag(tag.text)
                                        },
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    // 提示词优化（点一下优化，再点还原；忙碌时转圈）
                    val canOptimize = (draft.isNotBlank() || optimized) && !optimizing
                    IconButton(
                        onClick = { if (optimized) onRevert() else onOptimize() },
                        modifier = Modifier.size(32.dp),
                        enabled = canOptimize,
                    ) {
                        when {
                            optimizing -> CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 1.5.dp,
                                color = colors.primary,
                            )

                            optimized -> Icon(
                                Icons.Default.Check,
                                contentDescription = "还原原文",
                                tint = colors.primary,
                                modifier = Modifier.size(18.dp),
                            )

                            else -> Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = "优化提示词",
                                tint = if (draft.isNotBlank()) colors.onSurfaceVariant
                                else colors.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        val canSend = draft.isNotBlank() || attachments.isNotEmpty()
        FloatingActionButton(
            onClick = onSend,
            modifier = Modifier.size(48.dp),
            containerColor = if (canSend) colors.primary else colors.surfaceVariant,
            contentColor = if (canSend) Color.White else colors.onSurfaceVariant,
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送", modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ModelCapsule(
    modelName: String,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = pressScale(pressed)
    val arrow = chevronRotation(expanded)

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = colors.surfaceVariant,
        modifier = Modifier
            .widthIn(max = 180.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modelName,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(4.dp))
            Box {
                Text(
                    "▴",
                    fontSize = 9.sp,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.graphicsLayer { rotationZ = arrow },
                )
            }
        }
    }
}

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
 * 聊天区
 * 组合：标题栏 + 消息列表 + 胶囊工具栏 + 内联面板 + 输入区；全屏输入覆盖其上。
 * 负责：提示词优化（含失败原因提示与回滚）、拖入附件、面板互斥。
 */

package b.ai.desktop.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.desktop.app.AppState
import b.ai.desktop.core.auth.GitHubAuth
import b.ai.desktop.core.model.ChatMessage
import b.ai.desktop.core.model.MsgRole
import b.ai.desktop.core.model.RunOptions
import b.ai.desktop.data.Effort
import b.ai.desktop.data.attachmentFromPath
import b.ai.desktop.feature.chat.input.FullscreenInput
import b.ai.desktop.feature.chat.input.InputBar
import b.ai.desktop.feature.chat.input.InputToolbar
import b.ai.desktop.feature.chat.input.pickAttachment
import b.ai.desktop.feature.chat.prompt.OptResult
import b.ai.desktop.feature.chat.prompt.PromptOptimizer
import b.ai.desktop.feature.session.sessionDeleteConfirm
import b.ai.desktop.feature.session.sessionMenuEntries
import b.ai.desktop.ui.component.InlinePanel
import b.ai.desktop.ui.menu.ContextMenuHostState
import b.ai.desktop.ui.menu.LocalContextMenu
import b.ai.desktop.ui.menu.MenuEntry
import b.ai.desktop.ui.menu.contextMenuTarget
import b.ai.desktop.ui.panel.AttachSheet
import b.ai.desktop.ui.panel.ConfigSheet
import b.ai.desktop.ui.panel.MessageTextOverlay
import b.ai.desktop.ui.panel.ModelSheet
import b.ai.desktop.ui.panel.RegenHintSheet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun ChatScreen(
    state: AppState,
    modifier: Modifier = Modifier,
) {
    val chat = rememberChatState(state.config)
    val colors = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 定时任务到点：把提示词装进草稿直接发送（保持和手发完全一致的链路）
    LaunchedEffect(state.scheduledPrompt) {
        val prompt = state.scheduledPrompt ?: return@LaunchedEffect
        state.clearScheduled()
        state.draft = prompt
        scope.launch { state.send(chat.runOptions()) }
    }
    val menu = LocalContextMenu.current

    var optimizing by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }
    var dragOver by remember { mutableStateOf(false) }
    // 带提示重新生成：一句话要求 + 重跑这一轮
    var regenHintOpen by remember { mutableStateOf(false) }
    var regenHint by remember { mutableStateOf("") }
    // 复制部分内容：摊开整条消息让用户拖选
    var copyPartMsg by remember { mutableStateOf<ChatMessage?>(null) }
    // 顶栏预算环展开的小卡片
    var ctxOpen by remember { mutableStateOf(false) }

    // 思考强度档位随模型走（deepseek 三档 / gpt 四到五档）
    val effortLevels = remember(state.modelName) { Effort.levels(state.modelName) }
    LaunchedEffect(effortLevels) { chat.ensureEffort(effortLevels) }

    LaunchedEffect(state.messages.size) {
        if (state.config.autoScroll && state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    // 提示自动消失
    LaunchedEffect(hint) {
        if (hint != null) {
            delay(2800)
            hint = null
        }
    }

    val dropTarget = remember {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val files = (event.dragData() as? DragData.FilesList)?.readFiles().orEmpty()
                dragOver = false
                if (files.isEmpty()) return false
                files.forEach { state.addAttachment(attachmentFromPath(it)) }
                hint = "已添加 ${files.size} 个附件"
                return true
            }

            override fun onEntered(event: DragAndDropEvent) { dragOver = true }
            override fun onExited(event: DragAndDropEvent) { dragOver = false }
            override fun onEnded(event: DragAndDropEvent) { dragOver = false }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .dragAndDropTarget(
                shouldStartDragAndDrop = { it.dragData() is DragData.FilesList },
                target = dropTarget,
            ),
    ) {
        Column(Modifier.fillMaxSize()) {
            ChatHeader(
                state = state,
                title = state.currentTitle,
                modelName = state.modelName,
                ctxActive = ctxOpen,
                onCtxClick = { ctxOpen = !ctxOpen },
            )
            HorizontalDivider(color = colors.outlineVariant)

            MessageList(
                messages = state.messages,
                listState = listState,
                busy = state.busy,
                status = state.status,
                style = BubbleViewStyle(
                    showReasoning = state.config.showReasoning,
                    reasoningExpanded = state.config.reasoningExpanded,
                    showTimestamp = state.config.showTimestamp,
                    showMeta = state.config.showMeta,
                    modelName = state.modelName,
                    maxWidth = state.config.bubbleMaxWidth.dp,
                    compact = state.config.compact,
                    avatarOn = state.config.avatarOn,
                    userAvatarFile = state.config.avatarUserFile.takeIf { it.isNotBlank() }
                        ?.let { java.io.File(b.ai.desktop.core.config.ConfigStore.appDir(), "avatars/$it").takeIf { f -> f.exists() }?.absolutePath }
                        ?: state.gitAccount?.let { GitHubAuth.avatarPath()?.absolutePath },
                    userInitial = state.gitAccount?.display?.take(1) ?: "你",
                    aiAvatarFile = state.config.avatarAiFile.takeIf { it.isNotBlank() }
                        ?.let { java.io.File(b.ai.desktop.core.config.ConfigStore.appDir(), "avatars/$it").takeIf { f -> f.exists() }?.absolutePath },
                    showUsage = state.config.showUsage,
                    capsule = state.config.bubbleCapsule,
                ),
                onMessageMenu = { index, msg, position ->
                    menu.open(
                        position,
                        messageMenuEntries(
                            state = state,
                            index = index,
                            msg = msg,
                            options = chat.runOptions(),
                            scope = scope,
                            menu = menu,
                            onRegenWith = {
                                regenHint = ""
                                regenHintOpen = true
                            },
                            onCopyPart = { copyPartMsg = msg },
                        ),
                    )
                },
                modifier = Modifier.weight(1f),
            )

            HorizontalDivider(color = colors.outlineVariant)

            InputToolbar(
                chat = chat,
                onOpenFullscreen = { chat.fullscreen = true },
            )

            InlinePanel(visible = state.showModelSheet) {
                ModelSheet(
                    models = state.models,
                    currentIndex = state.modelIndex,
                    onSelect = { state.selectModel(it) },
                    onManage = {
                        state.closeModelSheet()
                        state.showSettings = true
                    },
                    onClose = { state.closeModelSheet() },
                )
            }

            InlinePanel(visible = chat.showAttach) {
                AttachSheet(
                    onPickKind = { kind ->
                        pickAttachment(kind)?.let { state.addAttachment(it) }
                        chat.showAttach = false
                    },
                    onPromptTag = { body ->
                        state.appendDraft(body)
                        chat.showAttach = false
                    },
                    onClose = { chat.showAttach = false },
                )
            }

            InlinePanel(visible = chat.showConfig) {
                ConfigSheet(
                    thinkingEnabled = chat.thinkingEnabled,
                    onThinkingChange = { on ->
                        chat.thinkingEnabled = on
                        // 关掉思考时搜索一起收掉（搜索不能脱离思考）
                        if (!on) chat.nativeSearch = false
                    },
                    effortLevels = effortLevels,
                    effortIndex = chat.effortIndex(effortLevels),
                    onEffortIndexChange = { index ->
                        chat.thinkEffort = effortLevels.getOrElse(index) { chat.thinkEffort }
                    },
                    nativeSearch = chat.nativeSearch,
                    onNativeSearchChange = { on ->
                        chat.nativeSearch = on
                        // 开搜索自动开思考
                        if (on) chat.thinkingEnabled = true
                    },
                    toolCalls = chat.toolCalls,
                    onToolCallsChange = { chat.toolCalls = it },
                    budgetText = chat.budgetText,
                    onBudgetCycle = { chat.cycleBudget() },
                    maxMode = chat.maxMode,
                    onMaxModeChange = { chat.maxMode = it },
                    onClose = { chat.showConfig = false },
                )
            }

            InlinePanel(visible = regenHintOpen) {
                RegenHintSheet(
                    value = regenHint,
                    onChange = { regenHint = it },
                    onConfirm = {
                        val note = regenHint.trim()
                        regenHintOpen = false
                        regenHint = ""
                        if (note.isNotEmpty()) {
                            scope.launch { state.regenerate(chat.runOptions(), note) }
                        }
                    },
                    onClose = {
                        regenHintOpen = false
                        regenHint = ""
                    },
                )
            }

            // 优化提示（替代手机端的 Toast：失败原因给准话）
            AnimatedVisibility(
                visible = hint != null,
                enter = fadeIn(tween(90)),
                exit = fadeOut(tween(90)),
            ) {
                hint?.let { text ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colors.primary.copy(alpha = 0.10f),
                        ) {
                            Text(
                                text,
                                fontSize = 11.sp,
                                color = colors.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
            }

            InputBar(
                draft = state.draft,
                onDraftChange = { state.draft = it },
                attachments = state.attachments,
                onRemoveAttachment = { state.removeAttachment(it) },
                chat = chat,
                modelName = state.modelName,
                showModelSheet = state.showModelSheet,
                onToggleModelSheet = {
                    if (!state.showModelSheet) chat.closePanels()
                    state.toggleModelSheet()
                },
                onToggleConfig = {
                    if (!chat.showConfig) state.closeModelSheet()
                    chat.toggleConfig()
                },
                onToggleAttach = {
                    if (!chat.showAttach) state.closeModelSheet()
                    chat.toggleAttach()
                },
                optimizing = optimizing,
                optimized = chat.promptBackup != null,
                onOptimize = {
                    val origin = state.draft
                    if (origin.isBlank()) {
                        hint = "先写点内容再优化"
                    } else {
                        scope.launch {
                            optimizing = true
                            val result = PromptOptimizer.run(
                                text = origin,
                                mode = chat.agentMode,
                                attach = state.attachments.map { it.badge },
                                modeName = chat.agentMode.label,
                            )
                            optimizing = false
                            when (result) {
                                is OptResult.Ok -> {
                                    chat.promptBackup = origin
                                    state.draft = result.text
                                }

                                OptResult.EmptyInput -> hint = "先写点内容再优化"
                                OptResult.TooLong -> hint = "超过 ${PromptOptimizer.MAX_IN} 字，先精简再优化"
                                OptResult.NoModel -> hint = "还没配优化模型：设置 → 模型 → 提示词优化"
                                OptResult.EmptyOut -> hint = "模型没返回内容，换个优化模型或稍后再试"
                                is OptResult.Failed -> hint = "优化失败：${result.reason}"
                            }
                        }
                    }
                },
                onRevert = {
                    chat.promptBackup?.let { state.draft = it }
                    chat.promptBackup = null
                },
                onSend = {
                    chat.promptBackup = null
                    scope.launch { state.send(chat.runOptions()) }
                },
                sttState = state.sttState,
                onMic = { state.toggleStt() },
                promptTags = state.config.promptTags,
                onInsertTag = { tag -> state.draft = (state.draft + if (state.draft.isBlank()) "" else "\n") + tag },
                sendOnEnter = state.config.sendOnEnter,
                placeholder = state.config.inputPlaceholder,
            )
        }

        // 预算卡：贴顶栏右下方，点外面关闭
        AnimatedVisibility(
            visible = ctxOpen,
            enter = fadeIn(tween(90)),
            exit = fadeOut(tween(80)),
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            CtxCard(
                stat = state.ctxStat(),
                state = state,
                onClose = { ctxOpen = false },
                onNewSession = {
                    ctxOpen = false
                    state.newConversation()
                },
                onOpenSettings = {
                    ctxOpen = false
                    state.settingsGroup = "CHAT"
                    state.settingsPage = "chat_ctx"
                    state.showSettings = true
                },
            )
        }

        // 拖拽悬停提示
        AnimatedVisibility(
            visible = dragOver,
            enter = fadeIn(tween(90)),
            exit = fadeOut(tween(90)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.primary.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.surface,
                    shadowElevation = 6.dp,
                ) {
                    Text(
                        "松开即可添加附件",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.primary,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
            }
        }

        // 全屏输入：覆盖整个聊天区
        AnimatedVisibility(
            visible = chat.fullscreen,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(90)),
        ) {
            FullscreenInput(
                draft = state.draft,
                onDraftChange = { state.draft = it },
                attachments = state.attachments,
                onSend = {
                    chat.fullscreen = false
                    scope.launch { state.send(chat.runOptions()) }
                },
                onClose = { chat.fullscreen = false },
            )
        }

        // 复制部分内容：摊开整条消息，拖选 + Ctrl+C（放最上层，盖住全屏输入）
        copyPartMsg?.let { target ->
            MessageTextOverlay(msg = target, onClose = { copyPartMsg = null })
        }
    }
}

@Composable
private fun ChatHeader(
    state: AppState,
    title: String,
    modelName: String,
    ctxActive: Boolean,
    onCtxClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val menu = LocalContextMenu.current
    val session = state.currentSession

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(colors.background)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            color = colors.onSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .contextMenuTarget(
                    enabled = session != null,
                    onContextMenu = { position ->
                        session?.let { target ->
                            menu.open(
                                position,
                                sessionMenuEntries(
                                    session = target,
                                    onRename = { state.beginRename(target.id) },
                                    onTogglePin = { state.togglePinned(target.id) },
                                    onCopy = { state.copySession(target) },
                                    onExport = { state.exportSession(target) },
                                    onDelete = {
                                        if (state.config.confirmDelete) {
                                            menu.replace(
                                                sessionDeleteConfirm(
                                                    session = target,
                                                    onConfirm = { state.deleteConversation(target.id) },
                                                    onCancel = { menu.dismiss() },
                                                ),
                                            )
                                        } else {
                                            state.deleteConversation(target.id)
                                        }
                                    },
                                ),
                            )
                        }
                    },
                )
                .padding(vertical = 2.dp),
        )
        Spacer(Modifier.width(8.dp))
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(7.dp))
                .clickable { state.showWorkspace = true }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("<>", fontSize = 10.sp, color = colors.onSurfaceVariant)
            Spacer(Modifier.width(5.dp))
            Text("工作区", fontSize = 10.sp, color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.width(6.dp))
        CtxRing(stat = state.ctxStat(), active = ctxActive, onClick = onCtxClick)
        Spacer(Modifier.width(10.dp))
        Text("模型：$modelName", color = colors.onSurfaceVariant, fontSize = 11.sp)
    }
}

/**
 * 消息右键菜单。删除类操作只做「协议安全」的两种：
 * 单条（带 tool_calls 的助手消息会连带工具回执一起删）与尾部截断。
 */
private fun messageMenuEntries(
    state: AppState,
    index: Int,
    msg: ChatMessage,
    options: RunOptions,
    scope: CoroutineScope,
    menu: ContextMenuHostState,
    onRegenWith: () -> Unit,
    onCopyPart: () -> Unit,
): List<MenuEntry> {
    val list = state.messages
    val entries = mutableListOf<MenuEntry>()

    entries += MenuEntry.Action(
        label = "复制内容",
        enabled = msg.text.isNotBlank(),
        onClick = { state.copyMessage(msg) },
    )
    entries += MenuEntry.Action(
        label = "复制部分…",
        hint = "拖选片段",
        enabled = msg.text.isNotBlank(),
        onClick = onCopyPart,
    )
    entries += MenuEntry.Divider

    when (msg.role) {
        MsgRole.USER -> entries += MenuEntry.Action(
            label = "编辑后重发",
            onClick = { state.editAndResend(index) },
        )

        MsgRole.ASSISTANT -> {
            val lastAssistant = list.indexOfLast { it.role == MsgRole.ASSISTANT }
            val canRegen = lastAssistant == index && !state.busy
            entries += MenuEntry.Action(
                label = "重新生成",
                hint = when {
                    state.busy -> "运行中"
                    lastAssistant != index -> "仅最后一条"
                    else -> null
                },
                enabled = canRegen,
                onClick = { scope.launch { state.regenerate(options) } },
            )
            entries += MenuEntry.Action(
                label = "带提示重新生成…",
                enabled = canRegen,
                onClick = onRegenWith,
            )
        }

        else -> Unit
    }

    entries += MenuEntry.Action(
        label = if (msg.starred) "取消收藏" else "收藏这条",
        onClick = { state.toggleStar(index) },
    )
    if (msg.text.isNotBlank()) {
        entries += MenuEntry.Action(label = "朗读", onClick = { state.speakMessage(msg) })
    }

    entries += MenuEntry.Divider
    entries += MenuEntry.Action(
        label = "删除这条消息",
        hint = if (msg.role == MsgRole.TOOL) "需连带上文" else null,
        danger = true,
        enabled = msg.role != MsgRole.TOOL,
        keepOpen = state.config.confirmDelete,
        onClick = {
            if (state.config.confirmDelete) {
                menu.replace(
                    listOf(
                        MenuEntry.Action(
                            label = "确认删除这条消息",
                            danger = true,
                            onClick = { state.deleteMessage(index) },
                        ),
                        MenuEntry.Action(label = "取消", onClick = { menu.dismiss() }),
                    ),
                )
            } else {
                state.deleteMessage(index)
            }
        },
    )

    return entries
}

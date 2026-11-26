/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27 02:50:00
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * 首次启动引导：四屏走完，语义对齐手机端的 onboarding。
 * 装完第一次打开就能看见；「关于」里可以重看。
 */

package b.ai.desktop.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.desktop.app.AppState
import b.ai.desktop.core.approval.ApprovalMode
import b.ai.desktop.core.sandbox.SandboxMode
import b.ai.desktop.ui.component.AppIcon

private data class Slide(
    val eyebrow: String,
    val title: String,
    val body: String,
)

private val slides = listOf(
    Slide(
        eyebrow = "欢迎",
        title = "Baicodex，装在你电脑上的 Agent 工作台",
        body = "不用把代码贴到网页里。给它一个工作区，它会自己读文件、改文件、跑命令，" +
            "每一步都在你眼前发生，随时可以打断。",
    ),
    Slide(
        eyebrow = "工作区",
        title = "它只在你划定的那块地方动手",
        body = "工作区就是一个普通文件夹。所有读写都被锁在里面，越界路径直接拒绝；" +
            "Windows 系统目录、盘符根、保留设备名也在黑名单里。",
    ),
    Slide(
        eyebrow = "确认",
        title = "写文件、删东西、跑命令，先问你",
        body = "读文件不打扰你，动磁盘才弹卡片。可以只允许这一次，也可以让某个工具以后不再问，" +
            "系统级破坏性命令则一律拦住。",
    ),
    Slide(
        eyebrow = "开始",
        title = "填一条模型连接就能用",
        body = "支持任何 OpenAI 兼容接口：DeepSeek、自建网关、本地 vLLM 都行。" +
            "现在去设置里填 baseUrl、API Key 和模型名，或者先逛逛界面。",
    ),
)

@Composable
fun OnboardingScreen(
    state: AppState,
    onFinish: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var index by remember { mutableIntStateOf(0) }
    val last = index == slides.lastIndex

    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(
            Modifier
                .align(Alignment.Center)
                .widthIn(max = 620.dp)
                .padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 品牌
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Baicodex", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                    Text(
                        "Windows 0.1.0 · 桌面端",
                        fontSize = 10.sp,
                        color = colors.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(30.dp))

            AnimatedContent(
                targetState = index,
                transitionSpec = {
                    val forward = targetState > initialState
                    val offset = if (forward) 1 else -1
                    (slideInHorizontally(tween(220)) { it / 6 * offset } + fadeIn(tween(220)))
                        .togetherWith(slideOutHorizontally(tween(180)) { -it / 8 * offset } + fadeOut(tween(160)))
                },
                label = "onboarding",
            ) { current ->
                val slide = slides[current]
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        slide.eyebrow,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.primary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        slide.title,
                        fontSize = 25.sp,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface,
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        slide.body,
                        fontSize = 13.sp,
                        lineHeight = 22.sp,
                        color = colors.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )

                    // 第二、三屏顺手把两个关键开关摆出来，省得装完还要去设置里找
                    if (current == 1) {
                        Spacer(Modifier.height(22.dp))
                        SandboxPicker(state)
                    }
                    if (current == 2) {
                        Spacer(Modifier.height(22.dp))
                        ApprovalPicker(state)
                    }
                }
            }

        }

        // 圆点：窗口底部居中
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            slides.indices.forEach { dot ->
                Box(
                    Modifier
                        .size(if (dot == index) 18.dp else 6.dp, 6.dp)
                        .clip(CircleShape)
                        .background(if (dot == index) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.32f)),
                )
            }
        }

        // 按钮：贴窗口右下沿（和手机引导一致）
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .widthIn(max = 700.dp)
                .padding(start = 40.dp, end = 40.dp, bottom = 44.dp, top = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
                Spacer(Modifier.weight(1f))
                if (!last) {
                    Text(
                        "跳过",
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onFinish() }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    OnboardButton("下一步", primary = true) { index++ }
                } else {
                    OnboardButton("先去设置模型", primary = false) {
                        onFinish()
                        onOpenSettings()
                    }
                    Spacer(Modifier.width(8.dp))
                    OnboardButton("开始使用", primary = true) { onFinish() }
                }
        }
    }
}

@Composable
private fun SandboxPicker(state: AppState) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Text("沙箱档位", fontSize = 11.sp, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SandboxMode.entries.forEach { mode ->
                val active = state.sandboxMode == mode
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (active) colors.primary.copy(alpha = 0.10f) else colors.surfaceVariant.copy(alpha = 0.5f))
                        .border(
                            1.dp,
                            if (active) colors.primary.copy(alpha = 0.4f) else colors.outlineVariant,
                            RoundedCornerShape(9.dp),
                        )
                        .clickable { state.setSandboxMode(mode) }
                        .padding(9.dp),
                ) {
                    Text(
                        mode.label,
                        fontSize = 12.sp,
                        fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                        color = if (active) colors.primary else colors.onSurface,
                    )
                    Text(
                        mode.hint,
                        fontSize = 9.sp,
                        lineHeight = 13.sp,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ApprovalPicker(state: AppState) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Text("确认策略", fontSize = 11.sp, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        ApprovalMode.entries.forEach { mode ->
            val active = state.approvalMode == mode
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) colors.primary.copy(alpha = 0.08f) else colors.surfaceVariant.copy(alpha = 0f))
                    .clickable { state.setApprovalMode(mode) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (active) colors.primary else colors.outline),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    mode.label,
                    fontSize = 12.sp,
                    color = if (active) colors.primary else colors.onSurface,
                )
                Spacer(Modifier.width(8.dp))
                Text(mode.hint, fontSize = 10.sp, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun OnboardButton(text: String, primary: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (primary) colors.primary else colors.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(
            text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (primary) androidx.compose.ui.graphics.Color.White else colors.onSurface,
        )
    }
}

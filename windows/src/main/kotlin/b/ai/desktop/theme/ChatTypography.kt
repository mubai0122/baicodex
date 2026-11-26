/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27 01:15:17
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baicodex Windows - 聊天排版参数
 * 设置里调的字号/行距从这里下发，气泡与 Markdown 都读同一份。
 */

package b.ai.desktop.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

data class ChatType(
    val fontSize: Int = 13,
    val codeFontSize: Int = 12,
    val lineHeightPercent: Int = 155,
) {
    val bodySize: TextUnit get() = fontSize.sp
    val bodyLine: TextUnit get() = (fontSize * lineHeightPercent / 100f).sp
    val codeSize: TextUnit get() = codeFontSize.sp
}

val LocalChatType = staticCompositionLocalOf { ChatType() }

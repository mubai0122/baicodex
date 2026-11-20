/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:25
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * 全局字体样式：把 Material3 的 Typography 抽成可按字体族重建的函数。
 * BaiTheme 每次组合按用户选定的字体文件重建一份，没选就回系统默认（FontFamily.Default）。
 */
package b.ai.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** 按指定字体族生成一套 Typography；字体族为空时就是系统默认那套 */
fun appTypography(family: FontFamily = FontFamily.Default): Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
) //按字体族生成 Typography

/** 默认那套：没接线字体的地方（预览、测试）直接用这个 */
val Typography = appTypography()
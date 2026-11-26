/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27 03:40:00
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baicodex Windows - 主题
 * 亮色：白色 + 淡灰；暗色：深灰。默认跟随系统，可手动覆盖。
 * 主题色（accent）可换：由 Accent 色板生成两套 colorScheme。
 */

package b.ai.desktop.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 主题色板：每档给出亮/暗两套主色与容器色，中性色不变 */
enum class Accent(
    val id: String,
    val label: String,
    val lightPrimary: Color,
    val lightContainer: Color,
    val lightOnContainer: Color,
    val darkPrimary: Color,
    val darkContainer: Color,
) {
    BLUE("blue", "晨蓝", Color(0xFF4C6B9E), Color(0xFFDCE5F3), Color(0xFF1F2E45), Color(0xFF7B9CD0), Color(0xFF2C3B52)),
    TEAL("teal", "青碧", Color(0xFF2E7D74), Color(0xFFD3EAE7), Color(0xFF173B37), Color(0xFF6FB8AE), Color(0xFF24403C)),
    GREEN("green", "森绿", Color(0xFF3E7A45), Color(0xFFD8E9D8), Color(0xFF1E3620), Color(0xFF83B98A), Color(0xFF253B27)),
    VIOLET("violet", "堇紫", Color(0xFF6A5AA8), Color(0xFFE2DCF3), Color(0xFF2A2347), Color(0xFFA49BE0), Color(0xFF322B52)),
    ROSE("rose", "玫红", Color(0xFFA8506B), Color(0xFFF3DCE4), Color(0xFF47202E), Color(0xFFDA9AB0), Color(0xFF452833)),
    ORANGE("orange", "暖橙", Color(0xFFB06030), Color(0xFFF3E2D3), Color(0xFF47301E), Color(0xFFDFA379), Color(0xFF453222)),
    SLATE("slate", "石墨", Color(0xFF55606E), Color(0xFFDFE3E9), Color(0xFF252B33), Color(0xFF9AA6B5), Color(0xFF303843)),
    COFFEE("coffee", "咖啡", Color(0xFF7A5B44), Color(0xFFEDE1D6), Color(0xFF362A20), Color(0xFFC1A388), Color(0xFF3B2F25)),
    ;

    companion object {
        fun byId(id: String): Accent = entries.firstOrNull { it.id == id } ?: BLUE
    }
}

// 中性色固定；主色随 accent 换
private fun lightColors(a: Accent) = lightColorScheme(
    primary = a.lightPrimary,
    onPrimary = Color.White,
    primaryContainer = a.lightContainer,
    onPrimaryContainer = a.lightOnContainer,
    secondary = Color(0xFF5A6472),
    onSecondary = Color.White,
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F242C),
    surfaceVariant = Color(0xFFF1F3F6),
    onSurfaceVariant = Color(0xFF8A919C),
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF1F242C),
    outline = Color(0xFFE2E6EC),
    outlineVariant = Color(0xFFE8EBF0),
)

private fun darkColors(a: Accent) = darkColorScheme(
    primary = a.darkPrimary,
    onPrimary = Color(0xFF14161A),
    primaryContainer = a.darkContainer,
    onPrimaryContainer = Color(0xFFDCE5F3),
    secondary = Color(0xFF9AA4B4),
    onSecondary = Color(0xFF14161A),
    surface = Color(0xFF1E2127),
    onSurface = Color(0xFFE6E9EF),
    surfaceVariant = Color(0xFF272B33),
    onSurfaceVariant = Color(0xFF8B93A0),
    background = Color(0xFF14161A),
    onBackground = Color(0xFFE6E9EF),
    outline = Color(0xFF333842),
    outlineVariant = Color(0xFF2A2F38),
)

enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色"),
}

@Composable
fun BaicodexTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    accent: Accent = Accent.BLUE,
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (dark) darkColors(accent) else lightColors(accent)
    val base = MaterialTheme.typography
    val typography = if (fontFamily == null) base else base.withFontFamily(fontFamily)

    MaterialTheme(
        colorScheme = colors,
        typography = typography,
        content = content,
    )
}

/** 把整套 Typography 换成指定字体族（默认字体除外，等宽保持系统等宽） */
private fun androidx.compose.material3.Typography.withFontFamily(family: androidx.compose.ui.text.font.FontFamily) = copy(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)

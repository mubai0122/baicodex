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

package b.ai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import b.ai.core.cfg.app.Cfg

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun BaiTheme(content: @Composable () -> Unit) {
    //先定深浅再取色：颜色 getter 读的是 DarkColors，顺序不能反
    val dark = when (Cfg.darkTheme) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    DarkColors = dark
    //色系同样要在取色前写进去（BgGray / AccentBlue 等 getter 直接读它）
    ThemeTint = Cfg.themeTint
    val scheme = if (dark) {
        darkColorScheme(
            primary = AccentBlue,
            onPrimary = Color(0xFF0B0E13),
            primaryContainer = AccentBlueSoft,
            onPrimaryContainer = AccentBlue,
            secondary = TextSecondary,
            onSecondary = Color(0xFF0B0E13),
            background = BgGray,
            onBackground = TextPrimary,
            surface = SurfaceWhite,
            onSurface = TextPrimary,
            surfaceVariant = SurfaceMuted,
            onSurfaceVariant = TextSecondary,
            outline = BorderGray,
            outlineVariant = BorderGray,
            error = DangerRed,
            onError = Color(0xFF0B0E13),
            errorContainer = DangerRedSoft,
            onErrorContainer = DangerRed
        )
    } else {
        lightColorScheme(
            primary = AccentBlue,
            onPrimary = SurfaceWhite,
            primaryContainer = AccentBlueSoft,
            onPrimaryContainer = AccentBlue,
            secondary = TextSecondary,
            onSecondary = SurfaceWhite,
            background = BgGray,
            onBackground = TextPrimary,
            surface = SurfaceWhite,
            onSurface = TextPrimary,
            surfaceVariant = SurfaceMuted,
            onSurfaceVariant = TextSecondary,
            outline = BorderGray,
            outlineVariant = BorderGray,
            error = DangerRed,
            onError = SurfaceWhite,
            errorContainer = DangerRedSoft,
            onErrorContainer = DangerRed
        )
    }
    //字号缩放：只改 fontScale，不动 density，dp 尺寸不受影响
    val base = LocalDensity.current
    val scale = Cfg.fontScale.coerceIn(0.8f, 1.4f)
    //字体接线：全局字体 → LocalTextStyle（所有没显式指定 fontFamily 的 Text 都跟它走）；
    //我方气泡 / AI 回复各有一套自己的 Local，留空时一律继承全局字体
    val uiFam = FontStore.family(Cfg.uiFontFile) ?: FontFamily.Default
    val aiFam = FontStore.family(Cfg.aiFontFile) ?: uiFam
    val userFam = FontStore.family(Cfg.userFontFile) ?: uiFam
    val baseStyle = LocalTextStyle.current
    CompositionLocalProvider(
        LocalDensity provides Density(base.density, base.fontScale * scale),
        LocalTextStyle provides baseStyle.copy(fontFamily = uiFam),
        LocalAiFont provides aiFam,
        LocalUserFont provides userFam
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = appTypography(uiFam),
            shapes = AppShapes,
            content = content
        )
    }
} //统一主题入口：深浅两套配色 + 全局字号缩放 + 全局字体
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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import b.ai.core.cfg.app.Cfg

/** 当前是否深色：由 BaiTheme 按「跟随系统 / 浅色 / 深色」写进来，下面所有颜色 getter 跟着变 */
internal var DarkColors by mutableStateOf(false)

/** 当前色系：同样由 BaiTheme 写进来（0 默认蓝，1 冰川，2 春野，3 紫罗兰，4 珊瑚，5 石墨，6 玫红，7 青柠） */
internal var ThemeTint by mutableStateOf(0)

/** 一套色系：浅色强调 / 深色强调 / 渐变起止（0 表示这套是纯色，不铺渐变） */
private data class Tint(
    val light: Long,
    val dark: Long,
    val g1: Long = 0L,
    val g2: Long = 0L
)

private val Tints = listOf(
    Tint(0xFF2F6BFF, 0xFF7AA2FF),
    Tint(0xFF3E9AA8, 0xFF83D6E2, 0xFF8FE0DE, 0xFFF6C6DA),
    Tint(0xFF5AA34C, 0xFF9BD98A, 0xFFA8E5A0, 0xFFF0DE86),
    Tint(0xFF7A5AF8, 0xFFB4A2FF),
    Tint(0xFFE07A3C, 0xFFFFB27A),
    Tint(0xFF5B6672, 0xFF9EAAB8),
    Tint(0xFFD9558C, 0xFFFF9EC0),
    Tint(0xFF2FA37B, 0xFF7FDDBB)
)

/** 由种色推导的一套色系：浅色强调压暗保证白底可读，深色强调提亮保证黑底可读，渐变取色相 ±28° */
private fun tintOf(seed: Int): Tint {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(seed, hsv)
    val l = hsv.copyOf().apply {
        this[1] = maxOf(this[1], 0.55f)
        this[2] = 0.50f
    }
    val d = hsv.copyOf().apply {
        this[1] = this[1].coerceIn(0.40f, 0.85f)
        this[2] = 0.68f
    }
    val g1 = hsv.copyOf().apply {
        this[0] = (this[0] + 26f) % 360f
        this[1] = this[1].coerceAtLeast(0.45f)
        this[2] = 0.72f
    }
    val g2 = hsv.copyOf().apply {
        this[0] = (this[0] + 334f) % 360f
        this[1] = this[1].coerceAtLeast(0.40f)
        this[2] = 0.58f
    }
    fun argb(v: FloatArray): Long = android.graphics.Color.HSVToColor(v).toLong() and 0xFFFFFFFFL
    return Tint(argb(l), argb(d), argb(g1), argb(g2))
} //种色推导色系

//壁纸色系缓存：种色不变就不重复算，避免每帧重组都跑一遍 HSV 换算
private var seedCache = 0
private var seedTint: Tint? = null

/** 当前生效的色系：壁纸取色开着且取到了种色就用壁纸色，否则用预设的 8 套 */
private val tint: Tint
    get() {
        val seed = if (Cfg.wallTint) Cfg.wallTintSeed else 0
        if (seed != 0) {
            val c = seedTint
            if (c != null && seedCache == seed) return c
            val t = tintOf(seed)
            seedCache = seed
            seedTint = t
            return t
        }
        return Tints[ThemeTint.coerceIn(0, Tints.lastIndex)]
    } //当前色系

/** 当前色系的渐变两端；纯色色系返回 null */
val TintGradient: List<Color>?
    get() {
        val t = tint
        return if (t.g1 == 0L) null else listOf(Color(t.g1), Color(t.g2))
}

//浅色
private val LBg = Color(0xFFF4F5F7)
private val LSurface = Color(0xFFFFFFFF)
private val LSurfaceMuted = Color(0xFFEDEFF2)
private val LBorder = Color(0xFFE2E5E9)
private val LText = Color(0xFF1A1C1E)
private val LTextSub = Color(0xFF71767C)
private val LAccent = Color(0xFF2F6BFF)
private val LAccentSoft = Color(0xFFE9F0FF)
private val LDanger = Color(0xFFD0342C)
private val LDangerSoft = Color(0xFFFDECEA)
private val LWarn = Color(0xFFB57614)
private val LWarnSoft = Color(0xFFFDF4E3)
private val LOk = Color(0xFF2E7D4F)

//深色：底色压到近黑，卡片抬一层，强调色提亮保证对比度够
private val DBg = Color(0xFF0F1115)
private val DSurface = Color(0xFF191C21)
private val DSurfaceMuted = Color(0xFF23272D)
private val DBorder = Color(0xFF343A42)
private val DText = Color(0xFFECEEF1)
private val DTextSub = Color(0xFF9AA1AA)
private val DAccent = Color(0xFF7AA2FF)
private val DAccentSoft = Color(0xFF1D2A47)
private val DDanger = Color(0xFFFF7066)
private val DDangerSoft = Color(0xFF3B211E)
private val DWarn = Color(0xFFE3AE55)
private val DWarnSoft = Color(0xFF352B12)
private val DOk = Color(0xFF6FC894)

val BgGray: Color
    get() = if (DarkColors) lerp(DBg, Color(tint.dark), 0.05f) else lerp(LBg, Color(tint.light), 0.04f)
val SurfaceWhite: Color get() = if (DarkColors) DSurface else LSurface
val SurfaceMuted: Color get() = if (DarkColors) DSurfaceMuted else LSurfaceMuted
val BorderGray: Color get() = if (DarkColors) DBorder else LBorder
val TextPrimary: Color get() = if (DarkColors) DText else LText
val TextSecondary: Color get() = if (DarkColors) DTextSub else LTextSub
val AccentBlue: Color get() = if (DarkColors) Color(tint.dark) else Color(tint.light)
val AccentBlueSoft: Color
    get() = if (DarkColors) lerp(DBg, Color(tint.dark), 0.22f) else lerp(LSurface, Color(tint.light), 0.12f)
val DangerRed: Color get() = if (DarkColors) DDanger else LDanger
val DangerRedSoft: Color get() = if (DarkColors) DDangerSoft else LDangerSoft
val WarnAmber: Color get() = if (DarkColors) DWarn else LWarn
val WarnAmberSoft: Color get() = if (DarkColors) DWarnSoft else LWarnSoft
val OkGreen: Color get() = if (DarkColors) DOk else LOk
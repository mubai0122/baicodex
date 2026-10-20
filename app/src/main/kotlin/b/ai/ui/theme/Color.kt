package b.ai.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** 当前是否深色：由 BaiTheme 按「跟随系统 / 浅色 / 深色」写进来，下面所有颜色 getter 跟着变 */
internal var DarkColors by mutableStateOf(false)

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

val BgGray: Color get() = if (DarkColors) DBg else LBg
val SurfaceWhite: Color get() = if (DarkColors) DSurface else LSurface
val SurfaceMuted: Color get() = if (DarkColors) DSurfaceMuted else LSurfaceMuted
val BorderGray: Color get() = if (DarkColors) DBorder else LBorder
val TextPrimary: Color get() = if (DarkColors) DText else LText
val TextSecondary: Color get() = if (DarkColors) DTextSub else LTextSub
val AccentBlue: Color get() = if (DarkColors) DAccent else LAccent
val AccentBlueSoft: Color get() = if (DarkColors) DAccentSoft else LAccentSoft
val DangerRed: Color get() = if (DarkColors) DDanger else LDanger
val DangerRedSoft: Color get() = if (DarkColors) DDangerSoft else LDangerSoft
val WarnAmber: Color get() = if (DarkColors) DWarn else LWarn
val WarnAmberSoft: Color get() = if (DarkColors) DWarnSoft else LWarnSoft
val OkGreen: Color get() = if (DarkColors) DOk else LOk
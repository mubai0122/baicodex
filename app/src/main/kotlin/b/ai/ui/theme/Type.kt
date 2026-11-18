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
package b.ai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import b.ai.core.Cfg

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
    CompositionLocalProvider(
        LocalDensity provides Density(base.density, base.fontScale * scale)
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography,
            shapes = AppShapes,
            content = content
        )
    }
} //统一主题入口：深浅两套配色 + 全局字号缩放
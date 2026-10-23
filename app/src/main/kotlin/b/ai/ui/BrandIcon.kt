package b.ai.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.core.Brands
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** 厂商图标：有 favicon 就用图，抓不到就按名字首字生成一枚稳定淡色徽章 */
@Composable
internal fun BrandIcon(
    id: String,
    baseUrl: String,
    name: String,
    side: Dp = 18.dp
) {
    val ctx = LocalContext.current
    var img by remember(id) { mutableStateOf<ImageBitmap?>(Brands.cached(id)) }
    //抓图标要走网络，别在主线程干；失败时保持 null 走徽章
    LaunchedEffect(id, baseUrl) {
        if (img == null) {
            img = withContext(Dispatchers.IO) { Brands.ensure(ctx, id, baseUrl) }
        }
    }
    val bmp = img
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = null,
            modifier = Modifier.size(side).clip(CircleShape),
            contentScale = ContentScale.Fit
        )
    } else {
        //首字母徽章：色相按名字哈希，同一个厂商每次都是同一色
        val hue = (abs(name.hashCode()) % 360).toFloat()
        Box(
            Modifier
                .size(side)
                .clip(CircleShape)
                .background(Color.hsl(hue, 0.32f, 0.62f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                name.trim().take(1).uppercase().ifBlank { "?" },
                fontSize = (side.value * 0.62f).sp,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
} //厂商图标
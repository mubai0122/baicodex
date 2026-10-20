package b.ai.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.core.Images
import b.ai.ui.theme.AccentBlueSoft
import b.ai.ui.theme.DangerRed
import b.ai.ui.theme.TextSecondary

/**图片缩略图排：输入框、全屏输入、消息气泡共用一条。onRemove非空带删除角标，onOpen非空能点开；宽了横向滑 */
@Composable
fun ImgThumbs(
    images: List<String>,
    size: Dp = 52.dp,
    maxWidth: Dp = Dp.Unspecified,
    corner: Dp = 9.dp,
    onRemove: ((String) -> Unit)? = null,
    onOpen: ((String) -> Unit)? = null
) {
    if (images.isEmpty()) return
    val target = (size.value * 3).toInt()
    Row(
        Modifier
            .then(if (maxWidth != Dp.Unspecified) Modifier.widthIn(max = maxWidth) else Modifier)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        images.forEach { p ->
            val bmp = remember(p, target) { Images.thumb(p, target) }
            Box(Modifier.size(size)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(corner))
                        .background(AccentBlueSoft)
                        .then(if (onOpen != null) Modifier.tap { onOpen(p) } else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    if (bmp != null) {
                        Image(
                            bitmap = bmp,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        //文件被清掉时留个记号，别显示成一块空白
                        Text("图", fontSize = 10.sp, color = TextSecondary)
                    }
                }
                if (onRemove != null) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 5.dp, y = (-5).dp)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(DangerRed)
                            .tap { onRemove(p) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("×", fontSize = 11.sp, color = Color.White)
                    }
                }
            }
        }
    }
} //缩略图排
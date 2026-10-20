package b.ai.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.core.Images
import b.ai.ui.theme.AccentBlueSoft
import b.ai.ui.theme.DangerRed
import b.ai.ui.theme.TextSecondary

/**图片缩略图排：输入框、全屏输入共用一条。onRemove非空带删除角标，onOpen非空能点开；宽了横向滑 */
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

/**发出去的气泡里按图片原始比例完整展示：单图等比放到气泡最大宽，多图各自等比横排，都不裁切 */
@Composable
fun ImgFull(
    images: List<String>,
    maxWidth: Dp = 240.dp,
    maxHeight: Dp = 340.dp,
    corner: Dp = 10.dp,
    onOpen: ((String) -> Unit)? = null
) {
    if (images.isEmpty()) return
    val density = LocalDensity.current
    val single = images.size == 1
    Row(
        Modifier.widthIn(max = maxWidth).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        images.forEach { p ->
            //按原图宽高算显示尺寸：单图先吃满宽再看高度，多图先定高度再算宽
            val dim = remember(p, single, maxWidth, maxHeight) {
                val sz = Images.sizeOf(p)
                if (sz == null || sz.second <= 0) {
                    val s = if (single) maxWidth else 150.dp
                    s to s
                } else {
                    val ar = sz.first.toFloat() / sz.second.toFloat()
                    if (single) {
                        var w = maxWidth.value
                        var h = w / ar
                        if (h > maxHeight.value) {
                            h = maxHeight.value
                            w = h * ar
                        }
                        Dp(w) to Dp(h)
                    } else {
                        var h = 150f
                        var w = h * ar
                        if (w > maxWidth.value) {
                            w = maxWidth.value
                            h = w / ar
                        }
                        Dp(w) to Dp(h)
                    }
                }
            }
            //解码边长按 dp 换 px，留 20% 余量，避免大图被采样糊掉
            val sidePx = (maxOf(dim.first.value, dim.second.value) * density.density * 1.2f).toInt().coerceAtLeast(96)
            val bmp = remember(p, sidePx) { Images.big(p, sidePx) }
            Box(
                Modifier
                    .width(dim.first)
                    .height(dim.second)
                    .clip(RoundedCornerShape(corner))
                    .background(AccentBlueSoft)
                    .then(if (onOpen != null) Modifier.tap { onOpen(p) } else Modifier),
                contentAlignment = Alignment.Center
            ) {
                if (bmp != null) {
                    //容器比例已是原图比例，Fit 不裁切也不变形
                    Image(
                        bitmap = bmp,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text("图", fontSize = 12.sp, color = TextSecondary)
                }
            }
        }
    }
} //气泡内按原始比例展示图片

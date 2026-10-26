package b.ai.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.core.Images
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.AccentBlueSoft
import b.ai.ui.theme.DangerRed
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import androidx.compose.ui.res.stringResource
import b.ai.R

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
                        Text(stringResource(R.string.th_image), fontSize = 10.sp, color = TextSecondary)
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

/** 文件附件排：选中就直接挂着一条，图标 + 文件名 + ×，多了横向滑 */
@Composable
fun FileChips(
    files: List<String>,
    onRemove: ((String) -> Unit)? = null,
    onOpen: ((String) -> Unit)? = null
) {
    if (files.isEmpty()) return
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        files.forEach { p ->
            val name = remember(p) { p.substringAfterLast('/') }
            Row(
                Modifier
                    .height(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(AccentBlueSoft)
                    .then(if (onOpen != null) Modifier.tap { onOpen(p) } else Modifier)
                    .padding(start = 8.dp, end = if (onRemove != null) 4.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IcFile(AccentBlue, side = 13.dp)
                Spacer(Modifier.width(5.dp))
                Text(
                    name,
                    fontSize = 10.sp,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 150.dp)
                )
                if (onRemove != null) {
                    Spacer(Modifier.width(4.dp))
                    Box(
                        Modifier.size(16.dp).clip(CircleShape).tap { onRemove(p) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("×", fontSize = 12.sp, color = TextSecondary)
                    }
                }
            }
        }
    }
} //附件文件排

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
            //长图（高超过宽的 2.5 倍）单独一套：按原比例算必然被压成细条，改成铺宽 + 封顶高 + 顶部裁切
            val sz = remember(p) { Images.sizeOf(p) }
            val ar = if (sz == null || sz.second <= 0) 1f else sz.first.toFloat() / sz.second.toFloat()
            val long = ar < 0.4f
            //按图类型算显示尺寸：单图先吃满宽再看高度，多图先定高度再算宽
            val dim = remember(p, single, maxWidth, maxHeight, ar, long) {
                if (sz == null || sz.second <= 0) {
                    val s = if (single) maxWidth else 150.dp
                    s to s
                } else if (long) {
                    if (single) maxWidth to maxHeight else 90.dp to 150.dp
                } else if (single) {
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
                    //普通图容器已是原图比例，Fit 不裁切；长图容器不是原比例，Crop + 顶部对齐只露顶部
                    Image(
                        bitmap = bmp,
                        contentDescription = null,
                        contentScale = if (long) ContentScale.Crop else ContentScale.Fit,
                        alignment = Alignment.TopCenter,
                        modifier = Modifier.fillMaxSize()
                    )
                    //长图角标：告诉用户这张被裁了，点开能看全图
                    if (long) {
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black.copy(alpha = 0.45f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(stringResource(R.string.th_long), fontSize = 9.sp, color = Color.White)
                        }
                    }
                } else {
                    Text(stringResource(R.string.th_image), fontSize = 12.sp, color = TextSecondary)
                }
            }
        }
    }
} //气泡内按原始比例展示图片
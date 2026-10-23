package b.ai.ui

import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import b.ai.core.Images

/** 图片查看器的全局开关：气泡里的图点一下把路径塞进来，挂载点在 ChatScreen 的根弹窗层 */
object ImgView {
    val path = mutableStateOf<String?>(null)

    fun open(p: String) {
        path.value = p
    }

    fun close() {
        path.value = null
    }
}

/** 全屏看图：按屏宽等比铺开，长截图分段解码（BitmapRegionDecoder），内存不随图高上涨 */
@Composable
fun ImgViewerDialog(path: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val screenW = ctx.resources.displayMetrics.widthPixels.coerceAtLeast(1)
    val sz = remember(path) { Images.sizeOf(path) }
    val iw = (sz?.first ?: screenW).coerceAtLeast(1)
    val ih = (sz?.second ?: screenW).coerceAtLeast(1)
    //整图按屏宽等比后的像素高度，再按「一段 = 屏宽 × 1」切块
    val dispH = (ih.toLong() * screenW / iw).toInt().coerceAtLeast(1)
    val tile = screenW
    val count = (dispH + tile - 1) / tile
    val srcTile = (tile.toLong() * iw / screenW).toInt().coerceAtLeast(1)
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.95f))) {
        LazyColumn(Modifier.fillMaxSize().padding(top = 52.dp)) {
            items(count) { index ->
                val hDisp = minOf(tile, dispH - index * tile).coerceAtLeast(1)
                val hDp = with(density) { hDisp.toDp() }
                val bmp = remember(path, index) { region(path, iw, ih, index, srcTile) }
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxWidth().height(hDp)
                    )
                } else {
                    Spacer(Modifier.fillMaxWidth().height(hDp))
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.End
        ) {
            CircleIconBox(Icons.Filled.Close, desc = "关闭", size = 32.dp) { onClose() }
        }
    }
} //全屏看图

/** 解一段：只把这一屏高度的矩形读进内存，长截图才不会一次解码几十 MB */
private fun region(path: String, iw: Int, ih: Int, index: Int, srcTile: Int): ImageBitmap? {
    val top = (index.toLong() * srcTile).toInt().coerceIn(0, (ih - 1).coerceAtLeast(0))
    val h = minOf(srcTile, ih - top).coerceAtLeast(1)
    val dec = runCatching { BitmapRegionDecoder.newInstance(path, false) }.getOrNull() ?: return null
    val bmp = runCatching {
        dec.decodeRegion(Rect(0, top, iw, top + h), BitmapFactory.Options())
    }.getOrNull()
    dec.recycle()
    return bmp?.asImageBitmap()
} //分段解码

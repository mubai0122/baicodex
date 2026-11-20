/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:26
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package b.ai.ui.widget

import androidx.compose.ui.res.stringResource
import b.ai.R
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import b.ai.core.media.img.Images

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
    //返回键直接关：全屏看图时用户第一反应就是按返回
    BackHandler { onClose() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
            //关键：状态栏区域不参与本层布局，关闭键才不会顶到电池图标下面被系统吞掉点击
            .statusBarsPadding()
    ) {
        LazyColumn(Modifier.fillMaxSize().padding(top = 64.dp)) {
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
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            //视觉还是 32dp 的圆钮，外面套 48dp 命中区，手指歪一点也点得中
            Box(
                Modifier.size(48.dp).tap { onClose() },
                contentAlignment = Alignment.Center
            ) {
                CircleIconBox(Icons.Filled.Close, desc = stringResource(R.string.tx_b15d9127), size = 32.dp) { onClose() }
            }
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
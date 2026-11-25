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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import b.ai.R
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
} //查看器开关

/**
 * 全屏看图：黑底铺满整屏，关闭键贴在右上角，双指捏合缩放、单指拖动平移、单击退出。
 *
 * 交互约定：
 * 1. 单击（手指没有位移、也没有缩放）＝退出，跟系统相册一致；
 * 2. 缩放范围 1x~6x，以捏合质心为锚点，缩放过程中图不会突然跳位；
 * 3. 放大后拖动有边界约束，拖不出黑边；缩回 1x 时位移自动归零；
 * 4. 返回键同样退出。
 *
 * 解码走 Images.view()：按最长边采样，长截图不会一次吃几十 MB 内存。
 */
@Composable
fun ImgViewerDialog(path: String, onClose: () -> Unit) {
    //采样解码：只按最长边要这么多像素，长截图 / 巨图都不会把内存打满
    val bmp = remember(path) { Images.view(path) }
    //缩放与位移：只在 graphicsLayer 的 lambda 里读，走绘制阶段，拖动时不触发重组与重新排版
    var scale by remember(path) { mutableFloatStateOf(1f) }
    var offX by remember(path) { mutableFloatStateOf(0f) }
    var offY by remember(path) { mutableFloatStateOf(0f) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    //返回键直接关：全屏看图时用户第一反应就是按返回
    BackHandler { onClose() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { box = it }
            //手势：捏合缩放 + 拖动平移 + 单击退出，一个 awaitEachGesture 里按有无位移区分
            .pointerInput(path, box) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    //moved 用来区分「单击」和「拖动/缩放」：全程没动过才算单击
                    var moved = false
                    while (true) {
                        val e = awaitPointerEvent()
                        val zoom = e.calculateZoom()
                        val pan = e.calculatePan()
                        if (zoom != 1f || pan.getDistance() > 2f) {
                            moved = true
                            val ns = (scale * zoom).coerceIn(1f, 6f)
                            val k = ns / scale
                            //以捏合质心为锚点：质心相对中心的向量（再减掉当前位移）按比例换算，
                            //放大时内容朝远离质心的方向铺开，视觉上就是「捏哪里放哪里」
                            val c = e.calculateCentroid(useCurrent = false)
                            val cx = box.width / 2f
                            val cy = box.height / 2f
                            val nx = (offX + pan.x) + (c.x - cx - offX) * (1f - k)
                            val ny = (offY + pan.y) + (c.y - cy - offY) * (1f - k)
                            val cl = clampPan(nx, ny, ns, box, bmp)
                            scale = ns
                            offX = cl.x
                            offY = cl.y
                            //消费掉位移，免得下层列表/页面同时跟着动
                            e.changes.forEach { if (it.pressed) it.consume() }
                        }
                        if (e.changes.none { it.pressed }) break
                    }
                    //单击退出：没位移、没缩放
                    if (!moved) onClose()
                }
            }
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offX
                        translationY = offY
                    }
            )
        }
        //关闭键：贴右上角，只在按钮这一行吃状态栏高度；
        //整层 statusBarsPadding 会把图往下压、键也不在真正的右上角
        Row(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)) {
            //视觉还是 32dp 的圆钮，外面套 48dp 命中区，手指歪一点也点得中
            Box(Modifier.size(48.dp).tap { onClose() }, contentAlignment = Alignment.Center) {
                CircleIconBox(
                    Icons.Filled.Close,
                    desc = stringResource(R.string.tx_b15d9127),
                    size = 32.dp
                ) { onClose() }
            }
        }
    }
} //全屏看图

/**
 * 平移边界：ContentScale.Fit 之后的内容尺寸按容器宽高比算出来，
 * 最多只能拖到「内容比容器多出来的那部分」的一半，1 倍时两个方向都是 0（图锁在中间）。
 */
private fun clampPan(x: Float, y: Float, scale: Float, box: IntSize, bmp: ImageBitmap?): Offset {
    if (box.width <= 0 || box.height <= 0) return Offset.Zero
    val bw = (bmp?.width ?: 1).toFloat()
    val bh = (bmp?.height ?: 1).toFloat()
    val boxW = box.width.toFloat()
    val boxH = box.height.toFloat()
    //Fit 后的缩放系数：取宽高两个方向里更小的那个
    val fit = minOf(boxW / bw, boxH / bh)
    val mx = ((bw * fit * scale - boxW) / 2f).coerceAtLeast(0f)
    val my = ((bh * fit * scale - boxH) / 2f).coerceAtLeast(0f)
    return Offset(x.coerceIn(-mx, mx), y.coerceIn(-my, my))
} //边界约束

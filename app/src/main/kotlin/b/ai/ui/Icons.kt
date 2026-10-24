package b.ai.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 自绘加号，不引图标库 */
@Composable
fun IcPlus(color: Color, side: Dp = 18.dp, stroke: Dp = 1.9.dp) {
    Canvas(Modifier.size(side)) {
        val w = stroke.toPx()
        val r = size.minDimension / 2 - w
        val cx = size.width / 2
        val cy = size.height / 2
        drawLine(color, Offset(cx - r, cy), Offset(cx + r, cy), w, StrokeCap.Round)
        drawLine(color, Offset(cx, cy - r), Offset(cx, cy + r), w, StrokeCap.Round)
    }
} //矢量加号

/** 自绘汉堡按钮 */
@Composable
fun IcMenu(color: Color, side: Dp = 18.dp, stroke: Dp = 1.9.dp) {
    Canvas(Modifier.size(side)) {
        val w = stroke.toPx()
        val r = size.minDimension / 2 - w
        val cx = size.width / 2
        val cy = size.height / 2
        listOf(-r * 0.72f, 0f, r * 0.72f).forEach { dy ->
            drawLine(color, Offset(cx - r, cy + dy), Offset(cx + r, cy + dy), w, StrokeCap.Round)
        }
    }
} //矢量汉堡

/** 自绘关闭叉号 */
@Composable
fun IcClose(color: Color, side: Dp = 16.dp, stroke: Dp = 1.8.dp) {
    Canvas(Modifier.size(side)) {
        val w = stroke.toPx()
        val r = size.minDimension / 2 - w
        val cx = size.width / 2
        val cy = size.height / 2
        drawLine(color, Offset(cx - r, cy - r), Offset(cx + r, cy + r), w, StrokeCap.Round)
        drawLine(color, Offset(cx + r, cy - r), Offset(cx - r, cy + r), w, StrokeCap.Round)
    }
} //矢量叉号

/** 自绘返回箭头 */
@Composable
fun IcBack(color: Color, side: Dp = 18.dp, stroke: Dp = 1.9.dp) {
    Canvas(Modifier.size(side)) {
        val w = stroke.toPx()
        val r = size.minDimension / 2 - w
        val cx = size.width / 2
        val cy = size.height / 2
        drawLine(color, Offset(cx - r * 0.6f, cy), Offset(cx + r, cy), w, StrokeCap.Round)
        drawLine(color, Offset(cx - r * 0.6f, cy), Offset(cx + r * 0.3f, cy - r * 0.62f), w, StrokeCap.Round)
        drawLine(color, Offset(cx - r * 0.6f, cy), Offset(cx + r * 0.3f, cy + r * 0.62f), w, StrokeCap.Round)
    }
} //矢量返回箭头

/** 自绘全屏展开：四角折线，用在输入区右下角开全屏编辑 */
@Composable
fun IcExpand(color: Color, side: Dp = 14.dp, stroke: Dp = 1.6.dp) {
    Canvas(Modifier.size(side)) {
        val w = stroke.toPx()
        val s = size.minDimension
        val p = w / 2
        val l = s * 0.34f
        //四角各画一条折线，转角朝外
        drawLine(color, Offset(p, p + l), Offset(p, p), w, StrokeCap.Round)
        drawLine(color, Offset(p, p), Offset(p + l, p), w, StrokeCap.Round)
        drawLine(color, Offset(s - p - l, p), Offset(s - p, p), w, StrokeCap.Round)
        drawLine(color, Offset(s - p, p), Offset(s - p, p + l), w, StrokeCap.Round)
        drawLine(color, Offset(p, s - p - l), Offset(p, s - p), w, StrokeCap.Round)
        drawLine(color, Offset(p, s - p), Offset(p + l, s - p), w, StrokeCap.Round)
        drawLine(color, Offset(s - p - l, s - p), Offset(s - p, s - p), w, StrokeCap.Round)
        drawLine(color, Offset(s - p, s - p), Offset(s - p, s - p - l), w, StrokeCap.Round)
    }
} //矢量全屏展开

/** 自绘纸飞机（实心），发送键用 */
@Composable
fun IcSend(color: Color, side: Dp = 17.dp) {
    Canvas(Modifier.size(side)) {
        val s = size.minDimension
        //按 Material send 图标 24×24 的轮廓缩放，机头朝右
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(s * 0.084f, s * 0.875f)
            lineTo(s * 0.958f, s * 0.5f)
            lineTo(s * 0.084f, s * 0.125f)
            lineTo(s * 0.083f, s * 0.417f)
            lineTo(s * 0.708f, s * 0.5f)
            lineTo(s * 0.083f, s * 0.583f)
            close()
        }
        drawPath(p, color)
    }
} //矢量纸飞机

/** 自绘思考灯泡（实心圆 + 底座），用于思考过程标记 */
@Composable
fun IcSpark(color: Color, side: Dp = 14.dp) {
    Canvas(Modifier.size(side)) {
        val w = size.minDimension
        drawCircle(color, radius = w * 0.26f, center = Offset(w / 2, w * 0.36f))
        drawLine(
            color,
            Offset(w * 0.36f, w * 0.72f),
            Offset(w * 0.64f, w * 0.72f),
            w * 0.12f,
            StrokeCap.Round
        )
        drawLine(
            color,
            Offset(w * 0.42f, w * 0.86f),
            Offset(w * 0.58f, w * 0.86f),
            w * 0.1f,
            StrokeCap.Round
        )
    }
} //矢量思考标记

/** 自绘齿轮（设置） */
@Composable
fun IcGear(color: Color, side: Dp = 18.dp, stroke: Dp = 1.9.dp) {
    Canvas(Modifier.size(side)) {
        val w = stroke.toPx()
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension / 2 - w
        drawCircle(color, radius = r * 0.5f, center = c, style = Stroke(w))
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            val cs = kotlin.math.cos(a).toFloat()
            val sn = kotlin.math.sin(a).toFloat()
            drawLine(
                color,
                Offset(c.x + cs * r * 0.7f, c.y + sn * r * 0.7f),
                Offset(c.x + cs * r, c.y + sn * r),
                w,
                StrokeCap.Round
            )
        }
    }
} //矢量齿轮

/** 自绘放大镜（搜索） */
@Composable
fun IcSearch(color: Color, side: Dp = 15.dp, stroke: Dp = 1.8.dp) {
    Canvas(Modifier.size(side)) {
        val w = stroke.toPx()
        val r = size.minDimension / 2 - w
        val c = Offset(size.width / 2 - r * 0.12f, size.height / 2 - r * 0.12f)
        drawCircle(color, radius = r * 0.62f, center = c, style = Stroke(w))
        drawLine(
            color,
            Offset(c.x + r * 0.45f, c.y + r * 0.45f),
            Offset(c.x + r * 0.95f, c.y + r * 0.95f),
            w,
            StrokeCap.Round
        )
    }
} //矢量放大镜

/** 自绘导出（下箭头 + 底座） */
@Composable
fun IcExport(color: Color, side: Dp = 18.dp, stroke: Dp = 1.9.dp) {
    Canvas(Modifier.size(side)) {
        val w = stroke.toPx()
        val r = size.minDimension / 2 - w
        val cx = size.width / 2
        val cy = size.height / 2
        drawLine(color, Offset(cx, cy - r), Offset(cx, cy + r * 0.45f), w, StrokeCap.Round)
        drawLine(color, Offset(cx, cy + r * 0.45f), Offset(cx - r * 0.42f, cy), w, StrokeCap.Round)
        drawLine(color, Offset(cx, cy + r * 0.45f), Offset(cx + r * 0.42f, cy), w, StrokeCap.Round)
        drawLine(color, Offset(cx - r, cy + r), Offset(cx + r, cy + r), w, StrokeCap.Round)
    }
} //矢量导出

/** 自绘相册/图片（山 + 太阳） */
@Composable
fun IcImage(color: Color, side: Dp = 15.dp, stroke: Dp = 1.6.dp) {
    Canvas(Modifier.size(side)) {
        val w = stroke.toPx()
        val pad = w
        drawRoundRect(
            color,
            topLeft = Offset(pad, pad),
            size = androidx.compose.ui.geometry.Size(size.width - pad * 2, size.height - pad * 2),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.18f),
            style = Stroke(w)
        )
        drawCircle(
            color,
            radius = size.width * 0.09f,
            center = Offset(size.width * 0.66f, size.height * 0.34f)
        )
        drawLine(
            color,
            Offset(size.width * 0.18f, size.height * 0.74f),
            Offset(size.width * 0.44f, size.height * 0.48f),
            w,
            StrokeCap.Round
        )
        drawLine(
            color,
            Offset(size.width * 0.44f, size.height * 0.48f),
            Offset(size.width * 0.78f, size.height * 0.74f),
            w,
            StrokeCap.Round
        )
    }
} //矢量图片

/** 自绘话筒 */
@Composable
fun IcMic(color: Color, side: Dp = 15.dp, stroke: Dp = 1.6.dp) {
    Canvas(Modifier.size(side)) {
        val w = stroke.toPx()
        val cx = size.width / 2
        drawRoundRect(
            color,
            topLeft = Offset(cx - size.width * 0.16f, size.height * 0.12f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.32f, size.height * 0.44f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.16f),
            style = Stroke(w)
        )
        drawLine(
            color,
            Offset(cx, size.height * 0.56f),
            Offset(cx, size.height * 0.84f),
            w,
            StrokeCap.Round
        )
        drawLine(
            color,
            Offset(size.width * 0.32f, size.height * 0.86f),
            Offset(size.width * 0.68f, size.height * 0.86f),
            w,
            StrokeCap.Round
        )
    }
} //矢量话筒

/** 自绘文件（折角纸） */
@Composable
fun IcFile(color: Color, side: Dp = 15.dp, stroke: Dp = 1.6.dp) {
    Canvas(Modifier.size(side)) {
        val w = stroke.toPx()
        val p = w
        val r = size.width * 0.22f
        drawLine(color, Offset(size.width * 0.28f, size.height * 0.14f), Offset(size.width * 0.62f, size.height * 0.14f), w, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.62f, size.height * 0.14f), Offset(size.width * 0.62f + r, size.height * 0.14f + r), w, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.62f + r, size.height * 0.14f + r), Offset(size.width * 0.62f + r, size.height * 0.86f), w, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.62f + r, size.height * 0.86f), Offset(size.width * 0.28f, size.height * 0.86f), w, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.28f, size.height * 0.86f), Offset(size.width * 0.28f, size.height * 0.14f), w, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.36f, size.height * 0.52f), Offset(size.width * 0.62f, size.height * 0.52f), w, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.36f, size.height * 0.66f), Offset(size.width * 0.62f, size.height * 0.66f), w, StrokeCap.Round)
        if (p > 0) Unit
    }
} //矢量文件

/** 自绘滑块组（模型参数 tab） */
@Composable
fun IcSliders(color: Color, side: Dp = 17.dp, stroke: Dp = 1.6.dp) {
    Canvas(Modifier.size(side)) {
        val w = stroke.toPx()
        val x0 = size.width * 0.14f
        val x1 = size.width * 0.86f
        listOf(0.26f to 0.62f, 0.5f to 0.36f, 0.74f to 0.56f).forEach { (y, kx) ->
            drawLine(color, Offset(x0, size.height * y), Offset(x1, size.height * y), w, StrokeCap.Round)
            drawCircle(color, radius = size.width * 0.11f, center = Offset(size.width * kx, size.height * y))
        }
    }
} //矢量滑块

/** 自绘对话气泡（对话 tab） */
@Composable
fun IcBubble(color: Color, side: Dp = 17.dp, stroke: Dp = 1.6.dp) {
    Canvas(Modifier.size(side)) {
        val w = stroke.toPx()
        drawRoundRect(
            color,
            topLeft = Offset(w, size.height * 0.14f),
            size = androidx.compose.ui.geometry.Size(size.width - w * 2, size.height * 0.56f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.24f),
            style = Stroke(w)
        )
        drawLine(
            color,
            Offset(size.width * 0.34f, size.height * 0.7f),
            Offset(size.width * 0.28f, size.height * 0.88f),
            w,
            StrokeCap.Round
        )
        drawLine(
            color,
            Offset(size.width * 0.28f, size.height * 0.88f),
            Offset(size.width * 0.52f, size.height * 0.7f),
            w,
            StrokeCap.Round
        )
    }
} //矢量气泡

/** 自绘盾牌（权限 tab） */
@Composable
fun IcShield(color: Color, side: Dp = 17.dp, stroke: Dp = 1.6.dp) {
    Canvas(Modifier.size(side)) {
        val w = stroke.toPx()
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * 0.5f, size.height * 0.1f)
            lineTo(size.width * 0.86f, size.height * 0.26f)
            lineTo(size.width * 0.86f, size.height * 0.52f)
            quadraticTo(size.width * 0.86f, size.height * 0.82f, size.width * 0.5f, size.height * 0.92f)
            quadraticTo(size.width * 0.14f, size.height * 0.82f, size.width * 0.14f, size.height * 0.52f)
            lineTo(size.width * 0.14f, size.height * 0.26f)
            close()
        }
        drawPath(p, color, style = Stroke(w))
    }
} //矢量盾牌

/** 自绘模块栅格（扩展 tab） */
@Composable
fun IcGrid(color: Color, side: Dp = 17.dp, stroke: Dp = 1.6.dp) {
    Canvas(Modifier.size(side)) {
        val w = stroke.toPx()
        val gap = size.width * 0.1f
        val cell = (size.width - w * 2 - gap) / 2
        listOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f).forEach { (ix, iy) ->
            drawRoundRect(
                color,
                topLeft = Offset(w + ix * (cell + gap), w + iy * (cell + gap)),
                size = androidx.compose.ui.geometry.Size(cell, cell),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cell * 0.3f),
                style = Stroke(w)
            )
        }
    }
} //矢量栅格

/** 自绘代码尖括号（工作区绑定入口） */
@Composable
fun IcCode(color: Color, side: Dp = 18.dp, stroke: Dp = 1.8.dp) {
    Canvas(Modifier.size(side)) {
        val w = stroke.toPx()
        val h = size.height
        val left = size.width * 0.18f
        val right = size.width * 0.82f
        val midY = h / 2
        drawLine(color, Offset(left + size.width * 0.18f, h * 0.22f), Offset(left, midY), w, StrokeCap.Round)
        drawLine(color, Offset(left, midY), Offset(left + size.width * 0.18f, h * 0.78f), w, StrokeCap.Round)
        drawLine(color, Offset(right - size.width * 0.18f, h * 0.22f), Offset(right, midY), w, StrokeCap.Round)
        drawLine(color, Offset(right, midY), Offset(right - size.width * 0.18f, h * 0.78f), w, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.58f, h * 0.18f), Offset(size.width * 0.42f, h * 0.82f), w, StrokeCap.Round)
    }
} //矢量尖括号

/** 自绘立方体（构建卡） */
@Composable
fun IcCube(color: Color, side: Dp = 20.dp, stroke: Dp = 1.6.dp) {
    Canvas(Modifier.size(side)) {
        val w = stroke.toPx()
        val cx = size.width / 2
        val top = size.height * 0.12f
        val bottom = size.height * 0.86f
        val leftX = size.width * 0.12f
        val rightX = size.width * 0.88f
        val upY = size.height * 0.36f
        val lowY = size.height * 0.62f
        drawLine(color, Offset(cx, top), Offset(rightX, upY), w, StrokeCap.Round)
        drawLine(color, Offset(rightX, upY), Offset(rightX, lowY), w, StrokeCap.Round)
        drawLine(color, Offset(rightX, lowY), Offset(cx, bottom), w, StrokeCap.Round)
        drawLine(color, Offset(cx, bottom), Offset(leftX, lowY), w, StrokeCap.Round)
        drawLine(color, Offset(leftX, lowY), Offset(leftX, upY), w, StrokeCap.Round)
        drawLine(color, Offset(leftX, upY), Offset(cx, top), w, StrokeCap.Round)
        drawLine(color, Offset(cx, top), Offset(cx, size.height * 0.46f), w, StrokeCap.Round)
        drawLine(color, Offset(cx, size.height * 0.46f), Offset(rightX, lowY), w, StrokeCap.Round)
        drawLine(color, Offset(cx, size.height * 0.46f), Offset(leftX, lowY), w, StrokeCap.Round)
    }
} //矢量立方体

/** 自绘分层（计划卡） */
@Composable
fun IcLayers(color: Color, side: Dp = 20.dp, stroke: Dp = 1.6.dp) {
    Canvas(Modifier.size(side)) {
        val w = stroke.toPx()
        val cx = size.width / 2
        listOf(0.24f, 0.48f, 0.72f).forEach { fy ->
            val y = size.height * fy
            drawLine(color, Offset(size.width * 0.1f, y), Offset(cx, y - size.height * 0.12f), w, StrokeCap.Round)
            drawLine(color, Offset(cx, y - size.height * 0.12f), Offset(size.width * 0.9f, y), w, StrokeCap.Round)
            drawLine(color, Offset(size.width * 0.9f, y), Offset(cx, y + size.height * 0.12f), w, StrokeCap.Round)
            drawLine(color, Offset(cx, y + size.height * 0.12f), Offset(size.width * 0.1f, y), w, StrokeCap.Round)
        }
    }
} //矢量分层

/** 自绘列车（行程卡） */
@Composable
fun IcTrain(color: Color, side: Dp = 20.dp, stroke: Dp = 1.6.dp) {
    Canvas(Modifier.size(side)) {
        val w = stroke.toPx()
        val l = size.width * 0.22f
        val r = size.width * 0.78f
        val t = size.height * 0.12f
        val b = size.height * 0.68f
        drawRoundRect(
            color,
            topLeft = Offset(l, t),
            size = androidx.compose.ui.geometry.Size(r - l, b - t),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.14f),
            style = Stroke(w)
        )
        drawLine(color, Offset(l + (r - l) * 0.3f, size.height * 0.42f), Offset(r - (r - l) * 0.3f, size.height * 0.42f), w, StrokeCap.Round)
        drawLine(color, Offset(l + (r - l) * 0.2f, b), Offset(l, size.height * 0.9f), w, StrokeCap.Round)
        drawLine(color, Offset(r - (r - l) * 0.2f, b), Offset(r, size.height * 0.9f), w, StrokeCap.Round)
    }
} //矢量列车

/** 自绘手指点击（操作助手卡） */
@Composable
fun IcTap(color: Color, side: Dp = 20.dp, stroke: Dp = 1.6.dp) {
    Canvas(Modifier.size(side)) {
        val w = stroke.toPx()
        val cx = size.width * 0.44f
        val cy = size.height * 0.34f
        val r = size.width * 0.16f
        drawCircle(color, r, Offset(cx, cy), style = Stroke(w))
        listOf(-1f to 0f, 1f to 0f, 0f to -1f, 0f to 1f).forEach { (dx, dy) ->
            drawLine(
                color,
                Offset(cx + dx * r * 1.6f, cy + dy * r * 1.6f),
                Offset(cx + dx * r * 2.3f, cy + dy * r * 2.3f),
                w,
                StrokeCap.Round
            )
        }
        drawLine(color, Offset(cx, cy + r * 1.8f), Offset(cx, size.height * 0.9f), w, StrokeCap.Round)
    }
} //矢量点击

/** 自绘时钟（定时任务） */
@Composable
fun IcClock(color: Color, side: Dp = 20.dp, stroke: Dp = 1.7.dp) {
    Canvas(Modifier.size(side)) {
        val w = stroke.toPx()
        val r = size.minDimension / 2 - w
        val c = Offset(size.width / 2, size.height / 2)
        drawCircle(color, r, c, style = Stroke(w))
        drawLine(color, c, Offset(c.x, c.y - r * 0.55f), w, StrokeCap.Round)
        drawLine(color, c, Offset(c.x + r * 0.45f, c.y), w, StrokeCap.Round)
    }
} //矢量时钟

/** 自绘人形（角色卡图标，替代 emoji） */
@Composable
fun IcPersona(color: Color, side: Dp = 18.dp, stroke: Dp = 1.6.dp) {
    Canvas(Modifier.size(side)) {
        val w = stroke.toPx()
        val cx = size.width / 2
        val hr = size.width * 0.2f
        drawCircle(color, hr, Offset(cx, size.height * 0.34f), style = Stroke(w))
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * 0.16f, size.height * 0.9f)
            cubicTo(
                size.width * 0.2f, size.height * 0.62f,
                size.width * 0.8f, size.height * 0.62f,
                size.width * 0.84f, size.height * 0.9f
            )
        }
        drawPath(path, color, style = Stroke(w, cap = StrokeCap.Round))
    }
} //矢量人形

/** 自绘字号图标：一个大 A 加一条底线，用作「外观」分类 */
@Composable
fun IcFont(color: Color, side: Dp = 17.dp, stroke: Dp = 1.6.dp) {
    Canvas(Modifier.size(side)) {
        val w = stroke.toPx()
        val top = size.height * 0.12f
        val bottom = size.height * 0.78f
        val half = size.width * 0.3f
        val cx = size.width / 2
        drawLine(color, Offset(cx - half, bottom), Offset(cx, top), w, StrokeCap.Round)
        drawLine(color, Offset(cx + half, bottom), Offset(cx, top), w, StrokeCap.Round)
        drawLine(
            color,
            Offset(cx - half * 0.55f, bottom - (bottom - top) * 0.34f),
            Offset(cx + half * 0.55f, bottom - (bottom - top) * 0.34f),
            w,
            StrokeCap.Round
        )
        drawLine(
            color,
            Offset(size.width * 0.14f, size.height * 0.9f),
            Offset(size.width * 0.86f, size.height * 0.9f),
            w,
            StrokeCap.Round
        )
    }
} //矢量字号

/** 自绘复制（两张叠起来的纸），用在群号右侧 */
@Composable
fun IcCopy(color: Color, side: Dp = 13.dp, stroke: Dp = 1.5.dp) {
    Canvas(Modifier.size(side)) {
        val w = stroke.toPx()
        val back = androidx.compose.ui.geometry.Size(size.width * 0.56f, size.height * 0.56f)
        drawRoundRect(
            color,
            topLeft = Offset(size.width * 0.1f, size.height * 0.1f),
            size = back,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.16f),
            style = Stroke(w)
        )
        drawRoundRect(
            color,
            topLeft = Offset(size.width * 0.34f, size.height * 0.34f),
            size = back,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.16f),
            style = Stroke(w)
        )
    }
} //矢量复制

/** 自绘对勾，用在自绘复选框里 */
@Composable
fun IcTick(color: Color, side: Dp = 10.dp, stroke: Dp = 1.7.dp) {
    Canvas(Modifier.size(side)) {
        val w = stroke.toPx()
        drawLine(color, Offset(size.width * 0.16f, size.height * 0.54f), Offset(size.width * 0.42f, size.height * 0.8f), w, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.42f, size.height * 0.8f), Offset(size.width * 0.86f, size.height * 0.22f), w, StrokeCap.Round)
    }
} //矢量对勾
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

package b.ai.core.media.wall

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import b.ai.core.cfg.app.Cfg
import java.io.File
import kotlin.math.max

/**
 * 壁纸取色：从背景图里挑出一个「看起来最像主题色」的种色。
 *
 * 思路（参考 Material You 的源色选取，但实现压到能跑在低端机上）：
 * 1. 位图缩到 64px 长边，几十万像素变几千个，采样代价可忽略；
 * 2. 丢掉近黑 / 近白 / 接近灰的像素（这些当主题色会直接糊掉）；
 * 3. 按色相切成 12 个桶，权重取 饱和度² —— 越鲜艳的颜色越有机会当选；
 * 4. 取权重最高的桶，桶内按饱和度加权平均，得到该色相下的代表色。
 *
 * 结果 0 表示这张图取不到可用颜色（纯黑白照片等）。
 */
object TintPick {
    //采样边长：64 足够代表整体色调，再大只是白耗 CPU
    private const val SAMPLE = 64
    //色相桶数量：30° 一桶
    private const val BUCKETS = 12

    /** 按当前设置重新取色并落盘：优先 App 内背景图，没有就用系统壁纸。返回种色（0 = 失败） */
    fun refresh(ctx: Context): Int {
        val seed = seedFrom(Cfg.bgImg).takeIf { it != 0 } ?: systemSeed(ctx)
        if (seed != 0) Cfg.wallTintSeed = seed
        return seed
    } //重新取色

    /** 从本地图片文件取种色；文件不存在 / 解码失败返回 0 */
    fun seedFrom(path: String): Int {
        if (path.isBlank()) return 0
        val f = File(path)
        if (!f.isFile || f.length() <= 0L) return 0
        //先量尺寸再采样解码，避免一张 1600px 的背景图直接解成全尺寸
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, o)
        if (o.outWidth <= 0 || o.outHeight <= 0) return 0
        var sample = 1
        while (max(o.outWidth, o.outHeight) / (sample * 2) >= SAMPLE) sample *= 2
        val bmp = runCatching {
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull() ?: return 0
        val seed = seedOf(bmp)
        //采样用的位图用完即回收：背景图动辄 1600px，堆里多留几份很容易触发 GC 抖动
        runCatching { if (!bmp.isRecycled) bmp.recycle() }
        return seed
    } //从文件取种色

    /** 系统壁纸取种色：部分 ROM 拿不到壁纸时返回 0，不做重试 */
    fun systemSeed(ctx: Context): Int {
        val d = runCatching { WallpaperManager.getInstance(ctx).drawable }.getOrNull() ?: return 0
        val bmp = (d as? BitmapDrawable)?.bitmap ?: runCatching {
            //非 BitmapDrawable（动态壁纸/一层 shell）就画一遍到小画布上再取样
            val w = max(1, d.intrinsicWidth)
            val h = max(1, d.intrinsicHeight)
            val side = SAMPLE
            val k = side.toFloat() / max(w, h)
            val out = Bitmap.createBitmap(
                max(1, (w * k).toInt()),
                max(1, (h * k).toInt()),
                Bitmap.Config.ARGB_8888
            )
            val c = Canvas(out)
            d.setBounds(0, 0, c.width, c.height)
            d.draw(c)
            out
        }.getOrNull() ?: return 0
        return seedOf(bmp)
    } //系统壁纸取种色

    /** 核心：按 色相桶 + 饱和度权重 选出种色 */
    fun seedOf(src: Bitmap): Int {
        if (src.width <= 0 || src.height <= 0) return 0
        val small = Bitmap.createScaledBitmap(src, SAMPLE, SAMPLE, false)
        val px = IntArray(SAMPLE * SAMPLE)
        small.getPixels(px, 0, SAMPLE, 0, 0, SAMPLE, SAMPLE)
        val sumW = DoubleArray(BUCKETS)
        val sumR = DoubleArray(BUCKETS)
        val sumG = DoubleArray(BUCKETS)
        val sumB = DoubleArray(BUCKETS)
        val hsv = FloatArray(3)
        var total = 0.0
        for (c in px) {
            //透明像素与近黑近白都跳过：它们当强调色会看不出内容
            if ((c ushr 24) < 200) continue
            android.graphics.Color.colorToHSV(c, hsv)
            if (hsv[2] < 0.14f || hsv[2] > 0.95f) continue
            if (hsv[1] < 0.18f) continue
            val w = (hsv[1] * hsv[1]).toDouble() //饱和度平方：鲜艳色权重高
            val b = (hsv[0] / (360f / BUCKETS)).toInt().coerceIn(0, BUCKETS - 1)
            sumW[b] += w
            sumR[b] += ((c shr 16) and 0xFF) * w
            sumG[b] += ((c shr 8) and 0xFF) * w
            sumB[b] += (c and 0xFF) * w
            total += w
        } //逐像素统计
        //一个可用像素都没有：纯黑白图，交给上一层回退
        if (total <= 0.0) return 0
        var best = 0
        for (i in 1 until BUCKETS) if (sumW[i] > sumW[best]) best = i
        val w = sumW[best]
        if (w <= 0.0) return 0
        val r = (sumR[best] / w).toInt().coerceIn(0, 255)
        val g = (sumG[best] / w).toInt().coerceIn(0, 255)
        val b = (sumB[best] / w).toInt().coerceIn(0, 255)
        //把选中桶的代表色提一点饱和度，避免平均后被灰化
        android.graphics.Color.colorToHSV(android.graphics.Color.rgb(r, g, b), hsv)
        hsv[1] = (hsv[1] * 1.15f).coerceAtMost(1f)
        hsv[2] = hsv[2].coerceIn(0.35f, 0.85f)
        return android.graphics.Color.HSVToColor(hsv)
    } //按色相桶选种色
} //壁纸取色
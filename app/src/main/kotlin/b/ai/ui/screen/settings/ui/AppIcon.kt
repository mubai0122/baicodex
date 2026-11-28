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

package b.ai.ui.screen.settings.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import b.ai.MainActivity
import b.ai.R
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.TextSecondary
import b.ai.ui.widget.CardBox
import b.ai.ui.widget.GhostButton
import b.ai.ui.widget.SectionTitle
import b.ai.ui.widget.tap

/** 自定义图标的实际执行者：解码相册图 → 裁方 → 钉桌面快捷方式 */
object AppIcons {
    //启动器对图标最大按 192dp 取图（xxxhdpi 48dp×4）；给 256 留余量，缩放后不糊
    private const val SIDE = 256

    /** 相册 URI 解码成正方形位图；解码失败返回 null */
    fun square(ctx: Context, uri: Uri): Bitmap? {
        //先量尺寸再按需采样，避免一张 8000px 的原图直接吃几百 MB
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } }
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
        var sample = 1
        while (opts.outWidth / (sample * 2) >= SIDE && opts.outHeight / (sample * 2) >= SIDE) sample *= 2
        val src = runCatching {
            ctx.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }.getOrNull() ?: return null
        //居中裁正方形：取短边，再等比缩到 SIDE
        val side = minOf(src.width, src.height)
        val left = (src.width - side) / 2
        val top = (src.height - side) / 2
        val out = Bitmap.createBitmap(SIDE, SIDE, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        //先铺白：相册里带透明通道的 png 直接钉桌面会变成黑底
        c.drawColor(android.graphics.Color.WHITE)
        c.drawBitmap(
            src,
            Rect(left, top, left + side, top + side),
            Rect(0, 0, SIDE, SIDE),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        )
        if (src !== out) src.recycle()
        return out
    } //相册图转方形

    /**
     * 把图片钉到桌面。返回 true 表示请求已发出（桌面是否弹确认框由 launcher 决定）。
     * 每次用独立 shortcut id，用户可以同时保留多个不同图标的入口。
     */
    fun pin(ctx: Context, bmp: Bitmap): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(ctx)) return false
        val label = ctx.getString(R.string.app_name)
        val info = ShortcutInfoCompat.Builder(ctx, "appicon_" + System.currentTimeMillis())
            .setShortLabel(label)
            .setLongLabel(label)
            .setIcon(IconCompat.createWithBitmap(bmp))
            //点击行为和桌面图标一致：走 LAUNCHER 入口
            .setIntent(
                Intent(ctx, MainActivity::class.java)
                    .setAction(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
            )
            .build()
        return runCatching { ShortcutManagerCompat.requestPinShortcut(ctx, info, null) }.getOrDefault(false)
    } //钉桌面
} //自定义图标

/**
 * 设置 → 外观 → 应用图标（LazyListScope 扩展，跟其它设置分节保持一致）。
 * Android 不允许运行时替换应用图标，所以这里做的是「钉一个带自定义图标的桌面快捷方式」。
 */
internal fun LazyListScope.appIconSec() {
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.tx_icon_title), stringResource(R.string.tx_icon_sub))
        CardBox {
            AppIconBody()
        }
    }
} //应用图标分节

/** 分节内容：说明 + 预览 + 选图按钮 */
@Composable
private fun AppIconBody() {
    val ctx = LocalContext.current
    //选完的预览：只给用户确认用了哪张，不落盘
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bmp = AppIcons.square(ctx, uri)
        if (bmp == null) {
            Toast.makeText(ctx, R.string.tx_icon_decodefail, Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        preview = bmp
        //选完直接钉：用户点按钮的意图已经很明确，不再多一步确认
        if (!AppIcons.pin(ctx, bmp)) {
            Toast.makeText(ctx, R.string.tx_icon_unsupported, Toast.LENGTH_LONG).show()
        }
    } //选图回调

    Text(
        stringResource(R.string.tx_icon_tip),
        fontSize = 11.sp,
        color = TextSecondary
    )
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val p = preview
        if (p != null) {
            androidx.compose.foundation.Image(
                bitmap = p.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
            )
        } else {
            //没选过：一块灰底占位，避免空着看不出这里将来是图标
            androidx.compose.foundation.layout.Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(SurfaceMuted),
                contentAlignment = Alignment.Center
            ) {
                Text("ICON", fontSize = 8.sp, color = TextSecondary, maxLines = 1)
            }
        }
        Column(Modifier.weight(1f)) {
            GhostButton(stringResource(R.string.tx_icon_pick), modifier = Modifier.fillMaxWidth().height(36.dp)) {
                pick.launch("image/*")
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.tx_icon_note),
                fontSize = 10.sp,
                color = TextSecondary
            )
        }
    }
} //应用图标内容

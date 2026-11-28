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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import b.ai.R
import b.ai.core.media.img.Images
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import java.io.File

/**
 * 正文图片路径扫描器。
 *
 * 认两种写法：
 * 1. markdown 图片语法 `![alt](路径)`；
 * 2. 裸路径 `file:///storage/…` / `/storage/…` / `/sdcard/…` / `/data/…`，后缀是图片格式。
 *
 * 只保留**磁盘上真实存在且非空**的文件，避免正文里贴的示例路径渲染成破图。
 */
object MsgPics {
    //只认这些后缀：正文里出现 png 指令名之类不会误判
    private val EXTS = arrayOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "heic", "avif")
    //markdown 图片语法：alt 里可能有方括号，所以 [^\]]* 足以覆盖常规场景
    private val MD = Regex("""!\[[^\]]*]\(\s*<?([^)\s>]+)>?\s*\)""")
    //裸路径：以图片后缀收尾；遇到空白 / 引号 / 括号 / 中文标点即视为路径结束
    private val RAW = Regex(
        """(file://)?(/(?:storage|sdcard|data|mnt)/[^\s"'<>()\[\]，。；：、,;]+\.(?:png|jpg|jpeg|webp|gif|bmp|heic|avif))""",
        RegexOption.IGNORE_CASE
    )

    /** 扫正文取图片路径；limit 控制单条消息最多渲染几张，避免长列表里塞几百张缩略图 */
    fun scan(text: String, limit: Int = 6): List<String> {
        if (text.isBlank()) return emptyList()
        //快速剪枝：正文里连一个图片后缀都没有，就不用跑两条正则
        if (EXTS.none { text.contains(it, ignoreCase = true) }) return emptyList()
        val out = ArrayList<String>(4)
        val seen = HashSet<String>()
        //先扫 markdown（带 alt 的写法更可信），再扫裸路径
        for (r in listOf(MD, RAW)) {
            r.findAll(text).forEach { m ->
                if (out.size >= limit) return@forEach
                //MD 的路径在 group1；RAW 的路径在 group2（group1 是 file:// 前缀）
                val raw = (if (r === MD) m.groupValues[1] else m.groupValues[2]).trim()
                if (raw.isEmpty()) return@forEach
                val p = if (raw.startsWith("file://")) Uri.parse(raw).path ?: return@forEach else raw
                val f = File(p)
                //必须真实存在且不是空文件，否则不渲染
                if (!f.isFile || f.length() <= 0L) return@forEach
                if (seen.add(f.absolutePath)) out.add(f.absolutePath)
            } //每种写法的匹配
        }
        return out
    } //扫图片路径

    /** 文件大小：显示成 KB / MB，正文卡片里当副标题用 */
    fun sizeText(len: Long): String = when {
        len >= 1 shl 20 -> String.format(java.util.Locale.US, "%.1f MB", len / 1048576f)
        len >= 1 shl 10 -> String.format(java.util.Locale.US, "%.0f KB", len / 1024f)
        else -> "$len B"
    } //大小文案

    /**
     * 用系统应用打开任意本地文件。
     * 私有目录与外部存储都走 FileProvider（file_paths.xml 已开 files / cache / external），
     * 找不到能处理的应用时只提示，不崩溃。
     */
    fun openFile(ctx: Context, path: String) {
        val f = File(path)
        val ext = f.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "image/*"
        val ok = runCatching {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
            val i = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        }.isSuccess
        if (!ok) Toast.makeText(ctx, R.string.tx_pic_openfail, Toast.LENGTH_SHORT).show()
    } //系统打开文件

    /** 定位：把完整路径塞进剪贴板，用户可以直接粘到文件管理器的地址栏 */
    fun copyPath(ctx: Context, path: String) {
        runCatching {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("path", path))
        }
        Toast.makeText(ctx, ctx.getString(R.string.tx_pic_copied) + "\n" + path, Toast.LENGTH_LONG).show()
    } //复制路径
} //正文图片工具

/**
 * 正文图片卡片：一行一张，左边缩略图，右边文件名 / 大小 / 三个动作。
 * 点缩略图 = 看大图（回到 ImgView 全屏），点「路径」= 复制定位，点「打开」= 系统应用打开。
 */
@Composable
fun MsgPicCards(paths: List<String>, onOpen: (String) -> Unit) {
    if (paths.isEmpty()) return
    val ctx = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        paths.forEach { p ->
            val f = remember(p) { File(p) }
            //缩略图解码走 Images.thumb 的 LRU（同路径只解一次），列表滚动不会反复解码大图
            val bmp = remember(p) { Images.thumb(p, 240) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceMuted)
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.25f))
                        .tap { onOpen(p) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (bmp != null) {
                        Image(
                            bitmap = bmp,
                            contentDescription = f.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(64.dp)
                        )
                    } else {
                        Text("IMG", fontSize = 10.sp, color = TextSecondary)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        f.name,
                        fontSize = 12.sp,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        MsgPics.sizeText(f.length()),
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        //查看：全屏看图
                        MiniBtn(stringResource(R.string.tx_pic_view)) { onOpen(p) }
                        //路径：复制完整路径（定位文件位置）
                        MiniBtn(stringResource(R.string.tx_pic_locate)) { MsgPics.copyPath(ctx, p) }
                        //打开：交给系统应用（相册 / 看图 / 编辑器）
                        MiniBtn(stringResource(R.string.tx_pic_open)) { MsgPics.openFile(ctx, p) }
                    } //三个动作
                } //右侧信息
            } //一张图
        }
    }
} //正文图片卡片

/** 卡片里的迷你按钮：玻璃底 + 主题色文字，尺寸压到 24dp 高不抢正文 */
@Composable
private fun MiniBtn(text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .tap { onClick() }
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text, fontSize = 10.sp, color = AccentBlue)
    }
} //迷你按钮
/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27 04:20:00
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * 全局外观度量：圆角 / 缩放 / 字体族。
 * AppState 从 config 写进来（mutableStateOf），所有读它的 Composable 会跟着重组。
 */

package b.ai.desktop.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import java.io.File

object UiMetrics {
    /** 气泡圆角 dp */
    var bubbleCorner by mutableStateOf(8)
    /** 卡片圆角 dp（设置卡、气泡里的代码块等） */
    var cardCorner by mutableStateOf(12)
    /** 弹窗圆角 dp */
    var dialogCorner by mutableStateOf(14)
    /** 全局缩放：1.0 = 默认；0.8–1.5 */
    var uiScale by mutableStateOf(1f)
    /** 界面字体族；null = Compose 默认 */
    var fontFamily by mutableStateOf<FontFamily?>(null)
    /** 字体族 id（设置页显示用） */
    var fontFamilyId by mutableStateOf("")

    fun apply(
        bubbleCorner: Int = this.bubbleCorner,
        cardCorner: Int = this.cardCorner,
        dialogCorner: Int = this.dialogCorner,
        uiScale: Int = (this.uiScale * 100).toInt(),
        fontId: String = this.fontFamilyId,
    ) {
        this.bubbleCorner = bubbleCorner
        this.cardCorner = cardCorner
        this.dialogCorner = dialogCorner
        this.uiScale = uiScale.coerceIn(80, 150) / 100f
        val family = UiFonts.load(fontId)
        this.fontFamily = family
        this.fontFamilyId = fontId
    }
}

/** Windows 常见字体：按文件加载，加载不了回退默认 */
object UiFonts {
    class Entry(val id: String, val label: String, val file: String)

    val options = listOf(
        Entry("", "默认", ""),
        Entry("msyh", "微软雅黑", "msyh.ttc"),
        Entry("dengxian", "等线", "Deng.ttf"),
        Entry("simhei", "黑体", "simhei.ttf"),
        Entry("simsun", "宋体", "simsun.ttc"),
        Entry("kaiti", "楷体", "simkai.ttf"),
        Entry("fangsong", "仿宋", "simfang.ttf"),
        Entry("cascadia", "Cascadia Code", "CascadiaCode.ttf"),
        Entry("consolas", "Consolas", "consola.ttf"),
        Entry("segoe", "Segoe UI", "segoeui.ttf"),
        Entry("arial", "Arial", "arial.ttf"),
        Entry("calibri", "Calibri", "calibri.ttf"),
    )

    private val cache = HashMap<String, FontFamily?>()

    fun byId(id: String): Entry = options.firstOrNull { it.id == id } ?: options.first()

    fun load(id: String): FontFamily? {
        if (cache.containsKey(id)) return cache[id]
        val entry = byId(id)
        val family = if (entry.file.isBlank()) null else runCatching {
            val file = File(System.getenv("WINDIR") ?: "C:\\Windows", "Fonts\\${entry.file}")
            if (!file.exists()) null else FontFamily(Font(file))
        }.getOrNull()
        cache[id] = family
        return family
    }
}

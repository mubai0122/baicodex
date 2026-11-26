/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27 01:08:13
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baicodex Windows - Markdown 正文
 * 手机端 ui/widget/Markdown.kt 的桌面移植：代码块（带复制）、标题、有序/无序列表、
 * 引用、分割线、表格（横向滚动 + 实测列宽）、段落与行内样式（代码/粗/斜/链接）。
 * 去掉了表情包与字号配置，颜色统一走主题。
 */

package b.ai.desktop.ui.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.desktop.core.util.copyToClipboard
import kotlinx.coroutines.delay

/** Markdown 的一个块：标题 / 段落 / 列表 / 引用 / 代码 / 表格 / 分割线 */
private sealed interface MdBlock {
    data class Code(val lang: String, val code: String) : MdBlock
    data class Head(val level: Int, val text: String) : MdBlock
    data class Bul(val level: Int, val text: String) : MdBlock
    data class Num(val level: Int, val num: Int, val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Para(val text: String) : MdBlock
    data class Table(val head: List<String>, val rows: List<List<String>>) : MdBlock
    data object Rule : MdBlock
}

/** 把文本切成块：只认代码块、表格、标题、列表、引用、分割线，其余按段落合并 */
private fun parseMarkdown(src: String): List<MdBlock> {
    val out = ArrayList<MdBlock>()
    val lines = src.replace("\r\n", "\n").split("\n")
    val buf = StringBuilder()

    fun flush() {
        if (buf.isNotBlank()) out.add(MdBlock.Para(buf.toString().trim()))
        buf.setLength(0)
    }

    val head = Regex("^(#{1,6})\\s+(.*)$")
    val bul = Regex("^(\\s*)[-*+]\\s+(.*)$")
    val num = Regex("^(\\s*)(\\d+)[.)]\\s+(.*)$")

    var i = 0
    while (i < lines.size) {
        val raw = lines[i]
        val t = raw.trim()
        when {
            t.startsWith("```") -> {
                flush()
                val lang = t.removePrefix("```").trim()
                val sb = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    sb.append(lines[i]).append('\n')
                    i++
                }
                i++
                out.add(MdBlock.Code(lang, sb.toString().trimEnd('\n')))
            }

            t.isEmpty() -> {
                flush()
                i++
            }

            t == "---" || t == "***" || t == "___" -> {
                flush()
                out.add(MdBlock.Rule)
                i++
            }

            else -> {
                // 表格：本行有竖线且下一行是 |---|---| 分隔行时整块吃下来
                if (t.contains('|') && i + 1 < lines.size && isSepRow(lines[i + 1].trim())) {
                    flush()
                    val (block, next) = readTable(lines, i)
                    out.add(block)
                    i = next
                    continue
                }
                val h = head.find(t)
                val b = bul.find(raw)
                val o = num.find(raw)
                when {
                    h != null -> {
                        flush()
                        out.add(MdBlock.Head(h.groupValues[1].length, h.groupValues[2]))
                        i++
                    }

                    t.startsWith("> ") || t == ">" -> {
                        flush()
                        out.add(MdBlock.Quote(t.removePrefix(">").trim()))
                        i++
                    }

                    b != null -> {
                        flush()
                        out.add(MdBlock.Bul(b.groupValues[1].length / 2, b.groupValues[2]))
                        i++
                    }

                    o != null -> {
                        flush()
                        out.add(
                            MdBlock.Num(
                                o.groupValues[1].length / 2,
                                o.groupValues[2].toIntOrNull() ?: 1,
                                o.groupValues[3],
                            ),
                        )
                        i++
                    }

                    else -> {
                        if (buf.isNotEmpty()) buf.append('\n')
                        buf.append(t)
                        i++
                    }
                }
            }
        }
    }
    flush()
    return out
}

/** 表格分隔行：形如 | --- | :--: | ---: | */
private fun isSepRow(t: String): Boolean {
    if (!t.contains('-')) return false
    if (!t.contains('|') && !t.contains(':')) return false
    val cells = splitRow(t)
    return cells.isNotEmpty() && cells.all { c -> c.isNotEmpty() && c.all { it == '-' || it == ':' } }
}

/** 按竖线切单元格：首尾的 | 不算空列，\| 是字面竖线 */
private fun splitRow(t: String): List<String> {
    var s = t.trim()
    if (s.startsWith("|")) s = s.substring(1)
    if (s.endsWith("|")) s = s.dropLast(1)
    val out = ArrayList<String>()
    val sb = StringBuilder()
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c == '\\' && i + 1 < s.length && s[i + 1] == '|') {
            sb.append('|')
            i += 2
            continue
        }
        if (c == '|') {
            out.add(sb.toString().trim())
            sb.setLength(0)
            i++
            continue
        }
        sb.append(c)
        i++
    }
    out.add(sb.toString().trim())
    return out
}

private fun readTable(lines: List<String>, start: Int): Pair<MdBlock.Table, Int> {
    val head = splitRow(lines[start].trim())
    val rows = ArrayList<List<String>>()
    var i = start + 2
    while (i < lines.size) {
        val r = lines[i].trim()
        if (r.isEmpty() || !r.contains('|')) break
        if (isSepRow(r)) {
            i++
            continue
        }
        rows.add(splitRow(r))
        i++
    }
    return MdBlock.Table(head, rows) to i
}

/** 行内样式：`代码`、**粗**、*斜*、[文字](链接)，其余原样 */
private fun inlineMarkdown(text: String, codeBg: Color, link: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    val n = text.length
    while (i < n) {
        val c = text[i]
        if (c == '`') {
            val end = text.indexOf('`', i + 1)
            if (end > i) {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)) {
                    append(text.substring(i + 1, end))
                }
                i = end + 1
                continue
            }
        }
        if (c == '*' && i + 1 < n && text[i + 1] == '*') {
            val end = text.indexOf("**", i + 2)
            if (end > i) {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(text.substring(i + 2, end)) }
                i = end + 2
                continue
            }
        }
        if (c == '*') {
            val end = text.indexOf('*', i + 1)
            if (end > i) {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }
                i = end + 1
                continue
            }
        }
        if (c == '[') {
            val close = text.indexOf(']', i + 1)
            if (close > i && close + 1 < n && text[close + 1] == '(') {
                val e2 = text.indexOf(')', close + 2)
                if (e2 > close) {
                    withStyle(SpanStyle(color = link)) { append(text.substring(i + 1, close)) }
                    i = e2 + 1
                    continue
                }
            }
        }
        append(c)
        i++
    }
}

/**
 * Markdown 正文。行内样式按原文缓存：列表滚动时会反复重组，
 * 每次重建 AnnotatedString 就是白烧帧。
 */
@Composable
fun MarkdownText(
    src: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fontSize: TextUnit = 13.sp,
    lineHeight: TextUnit = 20.sp,
    codeFontSize: TextUnit = 12.sp,
) {
    val colors = MaterialTheme.colorScheme
    val codeBg = colors.surfaceVariant
    val blocks = remember(src) { parseMarkdown(src) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Code -> CodeBlockView(block, codeFontSize)
                is MdBlock.Head -> Text(
                    remember(block.text, codeBg, colors.primary) {
                        inlineMarkdown(block.text, codeBg, colors.primary)
                    },
                    fontSize = when (block.level) {
                        1 -> (fontSize.value * 1.31f).sp
                        2 -> (fontSize.value * 1.19f).sp
                        else -> (fontSize.value * 1.08f).sp
                    },
                    lineHeight = (fontSize.value * 1.6f).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = color,
                )

                is MdBlock.Bul -> Row(Modifier.padding(start = (block.level * 14).dp)) {
                    Text("•", fontSize = fontSize, color = colors.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    InlineLine(block.text, fontSize, lineHeight, color, codeBg, colors.primary)
                }

                is MdBlock.Num -> Row(Modifier.padding(start = (block.level * 14).dp)) {
                    Text("${block.num}.", fontSize = fontSize, color = colors.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    InlineLine(block.text, fontSize, lineHeight, color, codeBg, colors.primary)
                }

                is MdBlock.Quote -> Row(
                    Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                ) {
                    InlineLine(
                        block.text,
                        fontSize,
                        lineHeight,
                        colors.onSurfaceVariant,
                        codeBg,
                        colors.primary,
                    )
                }

                is MdBlock.Rule -> Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .height(1.dp)
                        .background(colors.outlineVariant),
                )

                is MdBlock.Table -> TableBlock(block, color, fontSize, lineHeight, codeBg, colors.primary)

                is MdBlock.Para -> InlineLine(block.text, fontSize, lineHeight, color, codeBg, colors.primary)
            }
        }
    }
}

@Composable
private fun InlineLine(
    text: String,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    color: Color,
    codeBg: Color,
    link: Color,
) {
    val annotated = remember(text, codeBg, link) { inlineMarkdown(text, codeBg, link) }
    Text(annotated, fontSize = fontSize, lineHeight = lineHeight, color = color)
}

/** 代码块：语言标签 + 复制按钮，内容不折行、横向滚动 */
@Composable
private fun CodeBlockView(block: MdBlock.Code, codeFontSize: TextUnit) {
    val colors = MaterialTheme.colorScheme
    var copied by remember(block.code) { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1600)
            copied = false
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceVariant),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 6.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                block.lang.ifBlank { "code" },
                fontSize = 10.sp,
                color = colors.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.surface)
                    .clickable {
                        if (copyToClipboard(block.code)) copied = true
                    }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    if (copied) "已复制" else "复制",
                    fontSize = 10.sp,
                    color = if (copied) colors.primary else colors.onSurface,
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 10.dp),
        ) {
            Text(
                block.code,
                fontFamily = FontFamily.Monospace,
                fontSize = codeFontSize,
                lineHeight = (codeFontSize.value * 1.5f).sp,
                color = colors.onSurface,
                softWrap = false,
            )
        }
    }
}

/**
 * 表格：外层固定占满宽度并持有底色，内层横向滚动。
 * 列宽按「该列最宽单元格不折行的实测像素」算，单列 44–240dp。
 */
@Composable
private fun TableBlock(
    block: MdBlock.Table,
    color: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    codeBg: Color,
    link: Color,
) {
    val colors = MaterialTheme.colorScheme
    val cols = maxOf(block.head.size, block.rows.maxOfOrNull { it.size } ?: 0).coerceAtLeast(1)
    val meter = rememberTextMeasurer()
    val density = LocalDensity.current
    val cellStyle = TextStyle(fontSize = fontSize, lineHeight = lineHeight)
    val padW = 10.dp
    val widths = (0 until cols).map { c ->
        val cells = listOf(block.head.getOrNull(c).orEmpty()) + block.rows.map { it.getOrNull(c).orEmpty() }
        val px = cells.maxOfOrNull { t ->
            meter.measure(text = AnnotatedString(t), style = cellStyle, maxLines = 1).size.width
        } ?: 0
        (with(density) { px.toDp() } + padW).coerceIn(48.dp, 260.dp)
    }

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(codeBg.copy(alpha = 0.6f))
            .padding(6.dp),
    ) {
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            Column {
                Row(Modifier.background(colors.onSurfaceVariant.copy(alpha = 0.10f), RoundedCornerShape(4.dp))) {
                    for (c in 0 until cols) {
                        TableCell(block.head.getOrNull(c).orEmpty(), widths[c], fontSize, lineHeight, color, codeBg, link, head = true)
                    }
                }
                block.rows.forEach { row ->
                    Row {
                        for (c in 0 until cols) {
                            TableCell(row.getOrNull(c).orEmpty(), widths[c], fontSize, lineHeight, color, codeBg, link, head = false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TableCell(
    text: String,
    width: Dp,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    color: Color,
    codeBg: Color,
    link: Color,
    head: Boolean,
) {
    Box(Modifier.width(width).padding(horizontal = 5.dp, vertical = 4.dp)) {
        val annotated = remember(text, codeBg, link) { inlineMarkdown(text, codeBg, link) }
        Text(
            annotated,
            fontSize = fontSize,
            lineHeight = lineHeight,
            color = color,
            fontWeight = if (head) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

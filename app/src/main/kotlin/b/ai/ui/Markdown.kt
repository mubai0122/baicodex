package b.ai.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.DarkColors
import b.ai.ui.theme.OkGreen
import b.ai.ui.theme.SurfaceWhite
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import androidx.compose.ui.res.stringResource
import b.ai.R

//代码块底色：深色模式下不能还是浅灰，否则整块糊成一片
private val CodeBg get() = if (DarkColors) Color(0xFF23272F) else Color(0xFFF3F4F6)

/**Markdown 的一个块：标题 / 段落 / 列表 / 引用 / 代码 / 分割线 */
private sealed interface MdBlock {
    data class Code(val lang: String, val code: String) : MdBlock
    data class Head(val level: Int, val text: String) : MdBlock
    data class Bul(val level: Int, val text: String) : MdBlock
    data class Num(val level: Int, val num: Int, val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Para(val text: String) : MdBlock
    data object Rule : MdBlock
} //Markdown块类型

/**把文本切成块：只认代码块、标题、列表、引用、分割线，其余按段落合并 */
private fun parseMd(src: String): List<MdBlock> {
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
                        out.add(MdBlock.Num(o.groupValues[1].length / 2, o.groupValues[2].toIntOrNull() ?: 1, o.groupValues[3]))
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
} //切块

/**行内样式：`代码`、**粗**、*斜*、[文字](链接)，其余原样 */
private fun inlineMd(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    val n = text.length
    while (i < n) {
        val c = text[i]
        if (c == '`') {
            val end = text.indexOf('`', i + 1)
            if (end > i) {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = CodeBg)) {
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
                    withStyle(SpanStyle(color = AccentBlue)) { append(text.substring(i + 1, close)) }
                    i = e2 + 1
                    continue
                }
            }
        }
        append(c)
        i++
    }
} //行内样式

/**给代码块挂一个复制按钮，点了换成「已复制」两秒后复原 */
@Composable
private fun CodeBlock(b: MdBlock.Code) {
    val ctx = LocalContext.current
    var done by remember(b.code) { mutableStateOf(false) }
    if (done) {
        androidx.compose.runtime.LaunchedEffect(b.code) {
            kotlinx.coroutines.delay(2000)
            done = false
        }
    }
    Column(Modifier.fillMaxWidth().background(CodeBg, RoundedCornerShape(10.dp))) {
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 6.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(b.lang.ifBlank { "code" }, fontSize = 10.sp, color = TextSecondary)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .liquidBg(RoundedCornerShape(6.dp))
                    .tap {
                        mdCopy(ctx, b.code)
                        done = true
                    }
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(if (done) stringResource(R.string.md_copied) else stringResource(R.string.md_copy), fontSize = 10.sp, color = if (done) OkGreen else TextPrimary)
            }
        }
        Text(
            b.code,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.5.sp,
            color = TextPrimary,
            modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 10.dp)
        )
    }
} //代码块 + 复制

/**剪贴板：Markdown 组件自带一份，避免依赖页面的私有函数 */
private fun mdCopy(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText("bai", text))
}

/**Markdown 正文渲染：块级排版 + 行内样式，代码块自带复制按钮 */
@Composable
fun MdText(src: String, color: Color = TextPrimary, size: TextUnit = 13.sp) {
    val blocks = remember(src) { parseMd(src) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { b ->
            when (b) {
                is MdBlock.Code -> CodeBlock(b)
                is MdBlock.Head -> Text(
                    inlineMd(b.text),
                    fontSize = when (b.level) {
                        1 -> 17.sp
                        2 -> 15.5.sp
                        else -> 14.sp
                    },
                    fontWeight = FontWeight.SemiBold,
                    color = color,
                    lineHeight = 22.sp
                )
                is MdBlock.Bul -> Row(Modifier.padding(start = (b.level * 12).dp)) {
                    Text("•", fontSize = size, color = TextSecondary)
                    Spacer(Modifier.width(6.dp))
                    Text(inlineMd(b.text), fontSize = size, color = color, lineHeight = 20.sp)
                }
                is MdBlock.Num -> Row(Modifier.padding(start = (b.level * 12).dp)) {
                    Text("${b.num}.", fontSize = size, color = TextSecondary)
                    Spacer(Modifier.width(6.dp))
                    Text(inlineMd(b.text), fontSize = size, color = color, lineHeight = 20.sp)
                }
                is MdBlock.Quote -> Row(
                    Modifier
                        .fillMaxWidth()
                        .background(CodeBg, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        inlineMd(b.text),
                        fontSize = size,
                        color = TextSecondary,
                        lineHeight = 20.sp
                    )
                }
                is MdBlock.Rule -> Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .background(TextSecondary.copy(alpha = 0.25f), RoundedCornerShape(1.dp))
                        .padding(top = 0.5.dp)
                )
                is MdBlock.Para -> Text(
                    inlineMd(b.text),
                    fontSize = size,
                    color = color,
                    lineHeight = 20.sp
                )
            }
        }
    }
} //Markdown 正文
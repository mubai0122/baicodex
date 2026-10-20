package b.ai.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.agent.Agent
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.TextSecondary

/**一条快捷指令：名字 + 一句说明 */
data class SlashCmd(val name: String, val desc: String)

/**快捷指令表：输入框敲 / 就列出来，点一条直接执行，内容不会发给模型 */
val SlashCmds = listOf(
    SlashCmd("/新会话", "开一个干净的新对话"),
    SlashCmd("/清空", "清掉当前会话的消息，会话本身保留"),
    SlashCmd("/压缩", "把前面的历史压成摘要，省上下文"),
    SlashCmd("/导出", "把当前会话导出成 Markdown，路径写进剪贴板"),
    SlashCmd("/统计", "跳到设置里的用量统计"),
    SlashCmd("/角色", "换智能体角色，下一句开始生效"),
    SlashCmd("/语音", "把草稿读出来再插入输入框")
)

/**输入框以 / 开头且还没打空格时返回待补全片段，否则 null */
fun slashToken(input: String): String? {
    val t = input.trimStart()
    if (!t.startsWith("/")) return null
    if (t.contains(' ') || t.contains('\n')) return null
    if (t.length > 12) return null
    return t
} //斜杠片段

/**命令候选：按前缀过滤，一条都不匹配就不弹 */
@Composable
fun SlashPanel(token: String, onPick: (SlashCmd) -> Unit) {
    val rows = SlashCmds.filter { token == "/" || it.name.startsWith(token, true) }
    if (rows.isEmpty()) return
    CardBox {
        SectionTitle("快捷指令", "点一条直接执行，不会发给模型")
        rows.forEach { c ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .tap { onPick(c) }
                    .padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(c.name, fontSize = 13.sp, color = AccentBlue, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(10.dp))
                Text(c.desc, fontSize = 11.sp, color = TextSecondary, maxLines = 1)
            }
        }
    }
} //快捷指令面板

/**执行一条指令：返回一句给用户看的反馈，空串表示不用提示 */
fun execSlash(
    cmd: SlashCmd,
    ctx: Context,
    openTab: (Int) -> Unit,
    speak: (() -> Unit)? = null,
    persona: (() -> Unit)? = null
): String {
    return when (cmd.name) {
        "/新会话" -> {
            Agent.newSession()
            ""
        }
        "/清空" -> {
            Agent.clearCurrent()
            "已清空当前会话"
        }
        "/压缩" -> Agent.compressNow()
        "/导出" -> {
            val p = Agent.exportCurrent()
            if (p == null) {
                "还没有内容可导出"
            } else {
                (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                    ?.setPrimaryClip(ClipData.newPlainText("bai", p))
                "已导出：$p"
            }
        }
        "/统计" -> {
            openTab(4)
            ""
        }
        "/语音" -> {
            speak?.invoke()
            ""
        }
        "/角色" -> {
            persona?.invoke()
            ""
        }
        else -> ""
    }
} //执行快捷指令
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.R
import b.ai.agent.Agent
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.TextSecondary

/**一条快捷指令：id 是匹配用的固定标识，name / desc 是给人看的可翻译文案 */
data class SlashCmd(val id: String, val name: String, val desc: String)

/**快捷指令表：输入框敲 / 就列出来，点一条直接执行，内容不会发给模型 */
fun slashCmds(ctx: Context) = listOf(
    SlashCmd("new", ctx.getString(R.string.sl_new), ctx.getString(R.string.sl_new_d)),
    SlashCmd("clear", ctx.getString(R.string.sl_clear), ctx.getString(R.string.sl_clear_d)),
    SlashCmd("comp", ctx.getString(R.string.sl_comp), ctx.getString(R.string.sl_comp_d)),
    SlashCmd("export", ctx.getString(R.string.sl_exp), ctx.getString(R.string.sl_exp_d)),
    SlashCmd("stat", ctx.getString(R.string.sl_stat), ctx.getString(R.string.sl_stat_d)),
    SlashCmd("persona", ctx.getString(R.string.sl_persona), ctx.getString(R.string.sl_persona_d)),
    SlashCmd("voice", ctx.getString(R.string.sl_voice), ctx.getString(R.string.sl_voice_d))
) //快捷指令表

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
    val cmds = slashCmds(LocalContext.current)
    val q = token.removePrefix("/")
    val rows = cmds.filter { q.isEmpty() || it.name.contains(q, true) }
    if (rows.isEmpty()) return
    CardBox {
        SectionTitle(stringResource(R.string.sl_title), stringResource(R.string.sl_sub))
        rows.forEach { c ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .tap { onPick(c) }
                    .padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("/" + c.name, fontSize = 13.sp, color = AccentBlue, fontWeight = FontWeight.Medium)
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
    return when (cmd.id) {
        "new" -> {
            Agent.newSession()
            ""
        }
        "clear" -> {
            Agent.clearCurrent()
            ctx.getString(R.string.sl_cleared)
        }
        "comp" -> Agent.compressNow()
        "export" -> {
            val p = Agent.exportCurrent()
            if (p == null) {
                ctx.getString(R.string.sl_nothing)
            } else {
                (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                    ?.setPrimaryClip(ClipData.newPlainText("bai", p))
                ctx.getString(R.string.sl_exported, p)
            }
        }
        "stat" -> {
            openTab(4)
            ""
        }
        "voice" -> {
            speak?.invoke()
            ""
        }
        "persona" -> {
            persona?.invoke()
            ""
        }
        else -> ""
    }
} //执行快捷指令
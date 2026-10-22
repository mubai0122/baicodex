package b.ai.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.R
import b.ai.agent.Agent
import b.ai.agent.PlanReq
import b.ai.agent.PlanVerdict
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.BorderGray
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.SurfaceWhite
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary

/**
 * 计划确认面板：计划模式下模型给出的正文先在这里过一遍。
 * 确定执行 -> 退出计划模式并当场动手；继续确认 -> 把意见回填，模型重出一版计划，直到确认。
 */
@Composable
fun PlanSheet(req: PlanReq) {
    var note by remember(req) { mutableStateOf("") }
    val blank = stringResource(R.string.pl_blank_note)
    val revise: () -> Unit = {
        Agent.answerPlan(PlanVerdict.Revise(note.trim().ifBlank { blank }))
    }
    //返回键等于「先不执行」，不退出应用
    BackHandler(enabled = true) { Agent.answerPlan(PlanVerdict.Dismiss) }
    ScrimCard(onDismiss = { Agent.answerPlan(PlanVerdict.Dismiss) }) {
        Text(stringResource(R.string.pl_title), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(Modifier.height(3.dp))
        Text(stringResource(R.string.pl_desc), fontSize = 10.5.sp, color = TextSecondary)
        Spacer(Modifier.height(9.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
                .background(SurfaceMuted, RoundedCornerShape(12.dp))
                .padding(horizontal = 11.dp, vertical = 9.dp)
        ) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                MdText(req.text, size = 12.5.sp)
            }
        }
        Spacer(Modifier.height(9.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .background(SurfaceWhite, RoundedCornerShape(11.dp))
                .border(1.dp, BorderGray, RoundedCornerShape(11.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            if (note.isEmpty()) {
                Text(stringResource(R.string.pl_note_hint), fontSize = 12.sp, color = TextSecondary)
            }
            BasicTextField(
                value = note,
                onValueChange = { note = it },
                textStyle = TextStyle(fontSize = 12.5.sp, color = TextPrimary),
                cursorBrush = SolidColor(AccentBlue),
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.pl_cancel),
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier
                    .tap { Agent.answerPlan(PlanVerdict.Dismiss) }
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            )
            Spacer(Modifier.weight(1f))
            GhostButton(stringResource(R.string.pl_revise), modifier = Modifier.height(40.dp), onClick = revise)
            Spacer(Modifier.width(8.dp))
            PrimaryButton(stringResource(R.string.pl_confirm), modifier = Modifier.height(40.dp)) {
                Agent.answerPlan(PlanVerdict.Confirm)
            }
        }
    }
} //计划确认面板
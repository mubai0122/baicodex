/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:21
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package b.ai.ui.screen.chat.sheet
import b.ai.ui.screen.chat.at.*
import b.ai.ui.screen.chat.dialog.*
import b.ai.ui.screen.chat.list.*
import b.ai.ui.screen.chat.main.*
import b.ai.ui.dialog.ScrimCard
import b.ai.ui.glass.glassScrollState
import b.ai.ui.glass.liquidBg
import b.ai.ui.widget.GhostButton
import b.ai.ui.widget.MdText
import b.ai.ui.widget.PrimaryButton
import b.ai.ui.widget.tap
import b.ai.net.api.llm.text

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
import b.ai.agent.run.Agent
import b.ai.agent.run.PlanReq
import b.ai.agent.run.PlanVerdict
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
    //计划面板不接受外部关闭：返回键吞掉、点遮罩无效，只能从三个按钮里选一个
    BackHandler(enabled = true) { }
    ScrimCard(onDismiss = null) {
        Text(stringResource(R.string.pl_title), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(Modifier.height(3.dp))
        Text(stringResource(R.string.pl_desc), fontSize = 10.5.sp, color = TextSecondary)
        Spacer(Modifier.height(9.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
                .liquidBg(RoundedCornerShape(12.dp), tint = SurfaceMuted)
                .padding(horizontal = 11.dp, vertical = 9.dp)
        ) {
            Column(Modifier.verticalScroll(glassScrollState())) {
                MdText(req.text, size = 12.5.sp)
            }
        }
        Spacer(Modifier.height(9.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .liquidBg(RoundedCornerShape(11.dp))
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
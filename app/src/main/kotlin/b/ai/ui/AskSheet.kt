package b.ai.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import b.ai.agent.AskReq
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.AccentBlueSoft
import b.ai.ui.theme.BorderGray
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.SurfaceWhite
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary

/** 选项前的圆点：选中填色带对勾，未选中只有描边 */
@Composable
private fun PickDot(on: Boolean) {
    Box(
        Modifier
            .size(18.dp)
            .liquidBg(CircleShape, tint = if (on) AccentBlue else SurfaceWhite)
            .border(1.5.dp, if (on) AccentBlue else BorderGray, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (on) {
            androidx.compose.material3.Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = SurfaceWhite,
                modifier = Modifier.size(12.dp)
            )
        }
    }
} //选项圆点

/**
 * 提问面板：模型调用 ask_user 时弹出。
 * 一页一题，页码形如 ‹ 1/3 ›，可上一题 / 下一题；每题选项可单选或多选，末尾可自己填写。
 */
@Composable
fun AskSheet(req: AskReq) {
    val qs = req.questions
    var page by remember(req) { mutableStateOf(0) }
    //每题一份作答：选中下标 + 自定义文本 + 自定义是否展开；下标表必须是快照列表，普通 MutableList 改了不触发重组（点了没反应）
    val picked = remember(req) { qs.map { mutableStateListOf<Int>() } }
    val custom = remember(req) { qs.map { mutableStateOf("") } }
    val customOn = remember(req) { qs.map { mutableStateOf(false) } }
    val cur = qs.getOrNull(page) ?: return

    val submit: () -> Unit = {
        val answers = qs.mapIndexed { i, q ->
            val labels = picked[i].sorted().mapNotNull { q.options.getOrNull(it) }
            val extra = custom[i].value.trim()
            when {
                labels.isNotEmpty() && extra.isNotEmpty() -> labels.joinToString("、") + "；补充：" + extra
                labels.isNotEmpty() -> labels.joinToString("、")
                extra.isNotEmpty() -> extra
                else -> ""
            }
        }
        Agent.answerAsk(answers)
    }

    //返回键：先退到上一题，第一页就跳过（不让它落到退出应用）
    BackHandler(enabled = true) { if (page > 0) page-- else submit() }

    ScrimCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.qa_title),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .liquidBg(RoundedCornerShape(8.dp), tint = AccentBlueSoft)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "‹ ${page + 1} / ${qs.size} ›",
                    fontSize = 11.sp,
                    color = AccentBlue,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        if (qs.size > 1) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                qs.indices.forEach { k ->
                    val on = k == page
                    Box(
                        Modifier
                            .size(26.dp)
                            .liquidBg(CircleShape, tint = if (on) AccentBlue else SurfaceMuted)
                            .border(1.dp, if (on) AccentBlue else BorderGray, CircleShape)
                            .tap { page = k },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${k + 1}",
                            fontSize = 11.sp,
                            color = if (on) SurfaceWhite else TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        } //多题时给页码圆点，点一下直接跳题
        Spacer(Modifier.height(10.dp))
        Text(cur.title, fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
        if (cur.multi) {
            Spacer(Modifier.height(3.dp))
            Text(stringResource(R.string.qa_multi), fontSize = 10.5.sp, color = TextSecondary)
        }
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier
                .heightIn(max = 320.dp)
                .verticalScroll(glassScrollState())
        ) {
            cur.options.forEachIndexed { i, opt ->
                val on = picked[page].contains(i)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .liquidBg(RoundedCornerShape(12.dp), tint = if (on) AccentBlueSoft else SurfaceMuted)
                        .border(1.dp, if (on) AccentBlue else BorderGray, RoundedCornerShape(12.dp))
                        .tap {
                            if (cur.multi) {
                                if (on) picked[page].remove(i) else picked[page].add(i)
                            } else {
                                picked[page].clear()
                                picked[page].add(i)
                                customOn[page].value = false
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PickDot(on)
                    Spacer(Modifier.width(9.dp))
                    Text(
                        ('A' + i).toString() + ". " + opt,
                        fontSize = 13.sp,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (cur.other) {
                val on = customOn[page].value
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .liquidBg(RoundedCornerShape(12.dp), tint = if (on) AccentBlueSoft else SurfaceMuted)
                        .border(1.dp, if (on) AccentBlue else BorderGray, RoundedCornerShape(12.dp))
                        .tap {
                            customOn[page].value = !on
                            if (!on && !cur.multi) picked[page].clear()
                        }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PickDot(on)
                    Spacer(Modifier.width(9.dp))
                    Text(
                        ('A' + cur.options.size).toString() + ". " + stringResource(R.string.qa_other),
                        fontSize = 13.sp,
                        color = TextPrimary
                    )
                }
                if (on) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .liquidBg(RoundedCornerShape(10.dp))
                            .border(1.dp, BorderGray, RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        if (custom[page].value.isEmpty()) {
                            Text(stringResource(R.string.qa_hint), fontSize = 12.sp, color = TextSecondary)
                        }
                        BasicTextField(
                            value = custom[page].value,
                            onValueChange = { custom[page].value = it },
                            textStyle = TextStyle(fontSize = 13.sp, color = TextPrimary),
                            cursorBrush = SolidColor(AccentBlue),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (page > 0) {
                GhostButton(stringResource(R.string.qa_prev), modifier = Modifier.height(40.dp)) { page-- }
                Spacer(Modifier.width(8.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.qa_skip),
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier
                    .tap { submit() }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            )
            Spacer(Modifier.width(4.dp))
            PrimaryButton(
                if (page + 1 < qs.size) stringResource(R.string.qa_next) else stringResource(R.string.qa_submit),
                modifier = Modifier.height(40.dp)
            ) {
                if (page + 1 < qs.size) page++ else submit()
            }
        }
    }
} //提问面板
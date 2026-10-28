package b.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.agent.FlowRunner
import b.ai.core.Flow
import b.ai.core.FlowStep
import b.ai.core.Flows
import b.ai.exec.Alarms
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.AccentBlueSoft
import b.ai.ui.theme.DangerRed
import b.ai.ui.theme.DangerRedSoft
import b.ai.ui.theme.OkGreen
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 编辑中的一步：只存用户能改的三样，title 用序号自动生成 */
private data class StepDraft(val task: String, val retry: Int, val onFail: String)

@Composable
fun FlowSheet(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val fmtTime = remember { SimpleDateFormat("HH:mm", Locale.CHINA) }
    val fmtDate = remember { SimpleDateFormat("yyyy-MM-dd", Locale.CHINA) }
    val now = remember { Date() }
    var name by remember { mutableStateOf("") }
    var steps by remember { mutableStateOf(listOf(StepDraft("", 1, "stop"))) }
    var time by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(fmtDate.format(now)) }
    var daily by remember { mutableStateOf(true) }
    var auto by remember { mutableStateOf(true) }
    var editId by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    val flows = Flows.list

    ScrimCard(onDismiss = onClose) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (editId.isBlank()) "工作流" else "编辑工作流",
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary
            )
            Spacer(Modifier.weight(1f))
            Text(
                "关闭",
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier
                    .liquidBg(RoundedCornerShape(8.dp), tint = SurfaceMuted)
                    .tap { onClose() }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 430.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "多步按顺序跑，每步交给一个分智能体做完再进下一步。每步独立，拿不到上一步的结论，所以指令要写全。",
                fontSize = 10.sp, color = TextSecondary
            )
            Field("名称", name, { name = it }, "例如：每日巡检")
            Spacer(Modifier.height(6.dp))
            Text("步骤（最多 ${Flows.MAX_STEPS} 步）", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
            steps.forEachIndexed { i, s ->
                StepCard(
                    index = i,
                    draft = s,
                    canDel = steps.size > 1,
                    onTask = { v -> steps = steps.toMutableList().also { it[i] = s.copy(task = v) } },
                    onRetry = { r -> steps = steps.toMutableList().also { it[i] = s.copy(retry = r) } },
                    onFail = { f -> steps = steps.toMutableList().also { it[i] = s.copy(onFail = f) } },
                    onDel = { steps = steps.filterIndexed { k, _ -> k != i } }
                )
            }
            if (steps.size < Flows.MAX_STEPS) {
                GhostButton("+ 再加一步", Modifier.fillMaxWidth()) {
                    steps = steps + StepDraft("", 1, "stop")
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                Box(Modifier.weight(1f)) {
                    Field("定时（留空就不定时）", time, { time = it }, "08:30")
                }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    Field("日期", date, { date = it }, "2026-01-01")
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PolicyChip("每天", daily) { daily = true }
                PolicyChip("仅一次", !daily) { daily = false }
                PolicyChip("到点自动跑", auto) { auto = !auto }
            }
            if (err.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(err, fontSize = 11.sp, color = DangerRed)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                if (editId.isBlank()) "保存工作流" else "保存修改",
                fontSize = 13.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidBg(RoundedCornerShape(10.dp), tint = AccentBlue)
                    .tap {
                        val valid = steps.filter { it.task.isNotBlank() }
                        val at = if (time.isBlank()) 0L else flowParseAt(date, time, daily)
                        when {
                            name.isBlank() -> err = "给这条流起个名字"
                            valid.isEmpty() -> err = "至少写清一步要做什么"
                            time.isNotBlank() && at == null -> err = "时间格式不对，写成 08:30 这种"
                            else -> {
                                err = ""
                                val list = valid.mapIndexed { i, s ->
                                    FlowStep("步骤" + (i + 1), s.task.trim(), s.retry, s.onFail)
                                }
                                val f = if (editId.isBlank()) {
                                    Flows.add(name, list, at ?: 0L, daily, auto)
                                } else {
                                    val old = Flows.byId(editId)
                                    val merged = (old ?: Flow(editId, name, list)).copy(
                                        name = name, steps = list, auto = auto,
                                        at = at ?: 0L, daily = daily, on = true
                                    )
                                    Flows.update(merged)
                                    merged
                                }
                                if (f.at > 0L) Alarms.scheduleFlow(ctx, f) else Alarms.cancelFlow(ctx, f.id)
                                name = ""
                                steps = listOf(StepDraft("", 1, "stop"))
                                time = ""
                                editId = ""
                                android.widget.Toast
                                    .makeText(ctx, "已保存：" + f.name, android.widget.Toast.LENGTH_SHORT)
                                    .show()
                            }
                        }
                    }
                    .padding(vertical = 10.dp)
            )
            if (editId.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                GhostButton("取消编辑", Modifier.fillMaxWidth()) {
                    editId = ""
                    name = ""
                    steps = listOf(StepDraft("", 1, "stop"))
                    time = ""
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("已有工作流", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
            if (flows.isEmpty()) {
                Text(
                    "还没有。上面写几步保存下来，就能一键跑或定时跑。",
                    fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(vertical = 6.dp)
                )
            } else {
                flows.forEach { f ->
                    FlowRow(
                        f = f,
                        onEdit = {
                            editId = f.id
                            name = f.name
                            steps = f.steps.map { StepDraft(it.task, it.retry, it.onFail) }
                                .ifEmpty { listOf(StepDraft("", 1, "stop")) }
                            time = if (f.at > 0L) fmtTime.format(Date(f.at)) else ""
                            date = if (f.at > 0L) fmtDate.format(Date(f.at)) else fmtDate.format(now)
                            daily = f.daily
                            auto = f.auto
                        }
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "到点能不能准时，取决于「设置 → 扩展 → 定时任务」里的精确闹钟 / 电池白名单 / 通知三项权限，状态在那边看。",
                fontSize = 10.sp, color = TextSecondary
            )
        }
    }
} //工作流面板

@Composable
private fun StepCard(
    index: Int,
    draft: StepDraft,
    canDel: Boolean,
    onTask: (String) -> Unit,
    onRetry: (Int) -> Unit,
    onFail: (String) -> Unit,
    onDel: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .liquidBg(RoundedCornerShape(10.dp), tint = SurfaceMuted)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("第 ${index + 1} 步", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            if (canDel) {
                Text(
                    "删除",
                    fontSize = 10.sp,
                    color = DangerRed,
                    modifier = Modifier
                        .background(DangerRedSoft, RoundedCornerShape(8.dp))
                        .tap { onDel() }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
        Field(
            label = "",
            value = draft.task,
            onValueChange = onTask,
            placeholder = "这一步要做什么、在哪做、要什么格式的结果",
            singleLine = false,
            minLines = 2
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("失败重试", fontSize = 10.sp, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
            PolicyChip("0", draft.retry == 0) { onRetry(0) }
            PolicyChip("1", draft.retry == 1) { onRetry(1) }
            PolicyChip("2", draft.retry == 2) { onRetry(2) }
            PolicyChip("3", draft.retry == 3) { onRetry(3) }
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("重试后仍失败", fontSize = 10.sp, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
            PolicyChip("停住", draft.onFail.equals("stop", true)) { onFail("stop") }
            PolicyChip("跳过", draft.onFail.equals("skip", true)) { onFail("skip") }
        }
    }
} //一步的编辑卡

@Composable
private fun FlowRow(f: Flow, onEdit: () -> Unit) {
    val ctx = LocalContext.current
    var running by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .liquidBg(RoundedCornerShape(10.dp), tint = SurfaceMuted)
            .tap { onEdit() }
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    f.name + if (f.on) "" else "（已关）",
                    fontSize = 12.sp,
                    color = if (f.on) TextPrimary else TextSecondary
                )
                val head = StringBuilder().append(f.steps.size).append(" 步")
                if (f.at > 0L) {
                    head.append(" · ").append(if (f.daily) "每天 " else "单次 ").append(Alarms.fmt(f.at))
                    if (!f.auto) head.append(" · 只提醒")
                } else {
                    head.append(" · 未定时")
                }
                Text(head.toString(), fontSize = 10.sp, color = TextSecondary)
                if (f.lastAt > 0L) {
                    Text(
                        "上次 " + Alarms.fmt(f.lastAt) + if (f.lastOk) " 成功" else " 失败" +
                            if (f.lastNote.isBlank()) "" else "：" + f.lastNote.replace("\n", " ").take(28),
                        fontSize = 10.sp,
                        color = if (f.lastOk) OkGreen else DangerRed,
                        maxLines = 1
                    )
                }
            }
            Switch(
                checked = f.on,
                onCheckedChange = { on ->
                    Flows.toggle(f.id, on)
                    if (on) Alarms.scheduleFlow(ctx, f.copy(on = true)) else Alarms.cancelFlow(ctx, f.id)
                },
                colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue),
                modifier = Modifier.height(28.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (running) "跑着呢…" else "跑一次",
                fontSize = 11.sp,
                color = if (running) TextSecondary else AccentBlue,
                modifier = Modifier
                    .liquidBg(RoundedCornerShape(8.dp), tint = AccentBlueSoft)
                    .tap(enabled = !running) {
                        running = true
                        FlowRunner.launchManual(f) { out ->
                            running = false
                            android.widget.Toast.makeText(
                                ctx,
                                if (out.ok) "「" + f.name + "」全部通过" else "「" + f.name + "」有步骤失败，详情见对话",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            )
            Text(
                "删除",
                fontSize = 11.sp,
                color = DangerRed,
                modifier = Modifier
                    .background(DangerRedSoft, RoundedCornerShape(8.dp))
                    .tap {
                        Alarms.cancelFlow(ctx, f.id)
                        Flows.remove(f.id)
                    }
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            )
        }
    }
} //一条工作流

/** "yyyy-MM-dd HH:mm" → 时间戳；含时分的地道写法过期顺延（每天）或返 null（仅一次） */
private fun flowParseAt(date: String, time: String, daily: Boolean): Long? {
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
    fmt.isLenient = false
    val d = runCatching { fmt.parse("${date.trim()} ${time.trim()}") }.getOrNull() ?: return null
    val at = d.time
    val now = System.currentTimeMillis()
    if (at > now) return at
    return if (daily) b.ai.core.Schedules.rollForward(at, true, now) else null
} //定时时间解析
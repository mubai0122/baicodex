package b.ai.ui

import android.Manifest
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
import b.ai.core.Perms
import b.ai.core.Schedules
import b.ai.core.Task
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

/** 通知点开时要自动填进输入框的内容：由 MainActivity 写入，ChatScreen 取走 */
object IntentInbox {
    var prompt by mutableStateOf<String?>(null)
}

@Composable
fun TaskDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val fmtTime = remember { SimpleDateFormat("HH:mm", Locale.CHINA) }
    val fmtDate = remember { SimpleDateFormat("yyyy-MM-dd", Locale.CHINA) }
    val now = remember { Date() }
    var prompt by remember { mutableStateOf("") }
    var time by remember { mutableStateOf(fmtTime.format(now)) }
    var date by remember { mutableStateOf(fmtDate.format(now)) }
    var daily by remember { mutableStateOf(true) }
    var auto by remember { mutableStateOf(true) }
    var err by remember { mutableStateOf("") }
    val tasks = Schedules.list
    val exact = Alarms.canExact(ctx)
    val battery = Alarms.batteryOk(ctx)
    val notice = android.os.Build.VERSION.SDK_INT < 33 ||
        Perms.granted(ctx, Manifest.permission.POST_NOTIFICATIONS)

    ScrimCard(onDismiss = onClose) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("定时任务", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            Text(
                "关闭",
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier
                    .background(SurfaceMuted, RoundedCornerShape(8.dp))
                    .tap { onClose() }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 380.dp)
                .verticalScroll(rememberScrollState())
        ) {
            //到点能不能准时，全看这三项：缺哪个系统都会把唤醒往后推
            PermRow("精确闹钟", exact, "去授权") { Alarms.openExactAlarmSettings(ctx) }
            PermRow("免电池优化", battery, "去设置") { Alarms.openBatterySettings(ctx) }
            PermRow("通知", notice, "去设置") { Perms.openAppSettings(ctx) }

            Field(
                label = "到点做什么",
                value = prompt,
                onValueChange = { prompt = it },
                placeholder = "例：查一下今天杭州到上海的余票，汇总给我",
                singleLine = false,
                minLines = 3
            )
            Row(Modifier.fillMaxWidth()) {
                Box(Modifier.weight(1f)) {
                    Field("时间 HH:mm", time, { time = it }, "08:30")
                }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    //重复任务只看时分，日期框只在「仅一次」时有意义
                    Field("日期（仅一次）", date, { date = it }, "2026-01-01")
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PolicyChip("每天重复", daily) { daily = true }
                PolicyChip("仅一次", !daily) { daily = false }
                PolicyChip("到点自动执行", auto) { auto = !auto }
            }
            if (err.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(err, fontSize = 11.sp, color = DangerRed)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "保存",
                fontSize = 13.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AccentBlue, RoundedCornerShape(10.dp))
                    .tap {
                        val at = parseAt(date, time, daily)
                        if (prompt.isBlank()) {
                            err = "先写到点要做什么"
                        } else if (at == null) {
                            err = "时间格式不对，要 HH:mm（如 08:30）"
                        } else {
                            err = ""
                            val t = Schedules.add(prompt, at, daily, auto)
                            Alarms.schedule(ctx, t)
                            prompt = ""
                        }
                    }
                    .padding(vertical = 10.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text("已排的任务", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
            if (tasks.isEmpty()) {
                Text("还没有任务", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(vertical = 6.dp))
            } else {
                tasks.forEach { t -> TaskRow(t, exact) }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "说明：到点会发通知；勾了「自动执行」且应用进程活着、模型空闲时，会直接开一轮，" +
                    "需要确认的高危工具会留在应用里等你点同意。想在息屏也准点，务必给本应用开自启动白名单" +
                    "（各 ROM 在 设置 → 应用 → 自启动 里）。",
                fontSize = 10.sp,
                color = TextSecondary
            )
        }
    }
} //定时任务面板

@Composable
private fun PermRow(label: String, ok: Boolean, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 11.sp, color = TextPrimary)
        Spacer(Modifier.width(6.dp))
        Text(if (ok) "已就绪" else "未开启", fontSize = 10.sp, color = if (ok) OkGreen else DangerRed)
        Spacer(Modifier.weight(1f))
        if (!ok) {
            Text(
                action,
                fontSize = 10.sp,
                color = AccentBlue,
                modifier = Modifier
                    .background(AccentBlueSoft, RoundedCornerShape(8.dp))
                    .tap { onClick() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
} //权限状态行

@Composable
private fun TaskRow(t: Task, exact: Boolean) {
    val ctx = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(SurfaceMuted, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "${if (t.daily) "每天 " else ""}${Alarms.fmt(t.at)}" + if (t.on) "" else "（已停）",
                fontSize = 12.sp,
                color = if (!t.on) TextSecondary else if (exact) TextPrimary else DangerRed
            )
            Text(t.prompt.replace("\n", " ").take(46), fontSize = 10.sp, color = TextSecondary, maxLines = 1)
        }
        Switch(
            checked = t.on,
            onCheckedChange = { on ->
                Schedules.toggle(t.id, on)
                if (on) Alarms.schedule(ctx, t) else Alarms.cancel(ctx, t.id)
            },
            colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue),
            modifier = Modifier.height(28.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "删",
            fontSize = 11.sp,
            color = DangerRed,
            modifier = Modifier
                .background(DangerRedSoft, RoundedCornerShape(8.dp))
                .tap {
                    Alarms.cancel(ctx, t.id)
                    Schedules.remove(t.id)
                }
                .padding(horizontal = 8.dp, vertical = 5.dp)
        )
    }
} //单条任务

/** 拼成时间戳：重复任务过期就顺延到下一个点，单次任务过期直接算错误 */
private fun parseAt(date: String, time: String, daily: Boolean): Long? {
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
    fmt.isLenient = false
    val d = runCatching { fmt.parse("${date.trim()} ${time.trim()}") }.getOrNull() ?: return null
    val now = System.currentTimeMillis()
    val at = d.time
    if (at > now) return at
    return if (daily) Schedules.rollForward(at, true, now) else null
} //解析触发时间
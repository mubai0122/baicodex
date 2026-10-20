package b.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.core.DayUse
import b.ai.core.Stat
import b.ai.core.StatSnap
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import b.ai.R

//柱状图与方格图共用的两色渐变：淡蓝 → 淡粉
private val ToneBlue = Color(0xFF9FC3FF)
private val TonePink = Color(0xFFFFB3CC)

/** token 数折成 1.2k / 3.40M，太长的数字在窄屏放不下 */
private fun tk(v: Long): String = when {
    v >= 1_000_000 -> "%.2fM".format(v / 1_000_000.0)
    v >= 10_000 -> "%.1fk".format(v / 1000.0)
    else -> v.toString()
}

/** 统计页：总计、范围柱状图、本月方格图、模型分类、月活跃 */
@Composable
fun StatTab() {
    var snap by remember { mutableStateOf(StatSnap()) }
    var range by remember { mutableIntStateOf(2) }
    var fromKey by remember { mutableStateOf("") }
    var toKey by remember { mutableStateOf("") }
    var pick by remember { mutableStateOf<DayUse?>(null) }

    //只进页时扫一次库；切范围只是从已聚合好的天里截，不重复查库
    LaunchedEffect(Unit) { snap = withContext(Dispatchers.IO) { Stat.load() } }

    //0=1天 1=7天 2=30天 3=90天 4=自定义
    val shown = when (range) {
        0 -> snap.days.takeLast(1)
        1 -> snap.days.takeLast(7)
        2 -> snap.days.takeLast(30)
        3 -> snap.days.takeLast(90)
        else -> {
            val f = fromKey.trim()
            val t = toKey.trim()
            if (f.isEmpty() || t.isEmpty()) snap.days.takeLast(30)
            else snap.days.filter { it.day >= f && it.day <= t }
        }
    }

    SectionTitle(stringResource(R.string.st_total), stringResource(R.string.st_total_d))
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tk(snap.total), fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(stringResource(R.string.st_total_tok), fontSize = 10.sp, color = TextSecondary)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.st_rounds, snap.msgCount), fontSize = 12.sp, color = TextPrimary)
                Text(stringResource(R.string.st_active, snap.activeDays), fontSize = 10.sp, color = TextSecondary)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniStat(stringResource(R.string.st_in), snap.totalPrompt, Modifier.weight(1f))
            MiniStat(stringResource(R.string.st_in_cache), snap.totalCached, Modifier.weight(1f))
            MiniStat(stringResource(R.string.st_out), snap.totalCompletion, Modifier.weight(1f))
        }
        if (snap.totalPrompt > 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.st_cache_rate).format(snap.totalCached * 100.0 / snap.totalPrompt),
                fontSize = 9.sp,
                color = TextSecondary
            )
        }
    }

    Spacer(Modifier.height(14.dp))
    SectionTitle(stringResource(R.string.st_range), stringResource(R.string.st_range_d))
    CardBox {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(stringResource(R.string.st_d1), stringResource(R.string.st_d7), stringResource(R.string.st_d30), stringResource(R.string.st_d90), stringResource(R.string.st_custom)).forEachIndexed { i, label ->
                PolicyChip(label, range == i) { range = i }
            }
        }
        if (range == 4) {
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DayField(fromKey, stringResource(R.string.st_from), Modifier.weight(1f)) { fromKey = it }
                Text("→", fontSize = 11.sp, color = TextSecondary)
                DayField(toKey, stringResource(R.string.st_to), Modifier.weight(1f)) { toKey = it }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(tk(shown.sumOf { it.total }), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.st_tok_days, shown.size),
                fontSize = 10.sp,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            stringResource(
                R.string.st_split,
                tk(shown.sumOf { it.prompt }),
                tk(shown.sumOf { it.cached }),
                tk(shown.sumOf { it.completion })
            ),
            fontSize = 9.sp,
            color = TextSecondary
        )
        Spacer(Modifier.height(8.dp))
        BarChart(shown) { pick = it }
        Spacer(Modifier.height(6.dp))
        Text(dayRangeLabel(shown), fontSize = 9.sp, color = TextSecondary)
        pick?.let { d ->
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.st_day_tip, d.day, tk(d.total), tk(d.prompt), tk(d.cached), tk(d.completion)),
                fontSize = 10.sp,
                color = AccentBlue
            )
        }
    }

    Spacer(Modifier.height(14.dp))
    SectionTitle(stringResource(R.string.st_grid), stringResource(R.string.st_grid_d, snap.monthLen))
    CardBox {
        MonthGrid(snap.monthDay, pick) { pick = it }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.st_less), fontSize = 9.sp, color = TextSecondary)
            Spacer(Modifier.width(5.dp))
            listOf(0.15f, 0.4f, 0.65f, 1f).forEach { a ->
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            Brush.linearGradient(listOf(ToneBlue.copy(alpha = a), TonePink.copy(alpha = a))),
                            RoundedCornerShape(3.dp)
                        )
                )
                Spacer(Modifier.width(3.dp))
            }
            Text(stringResource(R.string.st_more), fontSize = 9.sp, color = TextSecondary)
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.st_grid_note, snap.monthDay.count { it.total > 0 }, snap.monthLen),
                fontSize = 9.sp,
                color = TextSecondary
            )
        }
    }

    Spacer(Modifier.height(14.dp))
    SectionTitle(stringResource(R.string.st_models), stringResource(R.string.st_models_d))
    CardBox {
        if (snap.models.isEmpty()) {
            Text(stringResource(R.string.st_empty), fontSize = 11.sp, color = TextSecondary)
        } else {
            //条形长度按第一名归一，不然小模型会缩成一条线看不见
            val maxModel = snap.models.first().second.coerceAtLeast(1L)
            val all = snap.total.coerceAtLeast(1L)
            snap.models.take(8).forEach { (name, v) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        fontSize = 11.sp,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(tk(v), fontSize = 10.sp, color = TextSecondary)
                    Spacer(Modifier.width(6.dp))
                    Text("%.0f%%".format(v * 100.0 / all), fontSize = 10.sp, color = AccentBlue)
                }
                Spacer(Modifier.height(3.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(SurfaceMuted, RoundedCornerShape(3.dp))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((v.toFloat() / maxModel).coerceIn(0.02f, 1f))
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(listOf(ToneBlue, TonePink)),
                                RoundedCornerShape(3.dp)
                            )
                    )
                }
                Spacer(Modifier.height(9.dp))
            }
        }
    }

    Spacer(Modifier.height(14.dp))
    SectionTitle(stringResource(R.string.st_month), stringResource(R.string.st_month_d))
    CardBox {
        val maxA = (snap.months.maxOfOrNull { it.activeDays } ?: 0).coerceAtLeast(1)
        Row(
            Modifier.fillMaxWidth().height(96.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            snap.months.forEach { m ->
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("${m.activeDays}", fontSize = 9.sp, color = TextSecondary)
                    Spacer(Modifier.height(2.dp))
                    Box(
                        Modifier
                            .fillMaxWidth(0.62f)
                            .height((6 + m.activeDays.toFloat() / maxA * 56).dp)
                            .background(
                                if (m.activeDays > 0) Brush.verticalGradient(listOf(TonePink, ToneBlue))
                                else SolidColor(SurfaceMuted),
                                RoundedCornerShape(4.dp)
                            )
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            snap.months.forEach { m ->
                Text(
                    stringResource(R.string.st_month_fmt, m.month.takeLast(2)),
                    fontSize = 9.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
} //统计分类

/** 按天柱状图：柱高按当期最大值归一，点一根看当天明细 */
@Composable
private fun BarChart(items: List<DayUse>, onPick: (DayUse) -> Unit) {
    if (items.isEmpty()) {
        Text(stringResource(R.string.st_no_data), fontSize = 11.sp, color = TextSecondary)
        return
    }
    val max = items.maxOf { it.total }.coerceAtLeast(1L)
    Row(
        Modifier.fillMaxWidth().height(100.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items.forEach { d ->
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                //最少留 2dp，有量就能看见，不至于是一根看不见的线
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((2 + d.total.toFloat() / max * 92).dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (d.total > 0) Brush.verticalGradient(listOf(TonePink, ToneBlue))
                            else SolidColor(SurfaceMuted)
                        )
                        .tap { onPick(d) }
                )
            }
        }
    }
} //柱状图

/** 本月方格图：7 列铺日号，没用的天浅灰，用过的按强度上渐变 */
@Composable
private fun MonthGrid(items: List<DayUse>, pick: DayUse?, onPick: (DayUse) -> Unit) {
    val max = (items.maxOfOrNull { it.total } ?: 0L).coerceAtLeast(1L)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.chunked(7).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { d ->
                    val a = 0.22f + 0.78f * (d.total.toFloat() / max).coerceIn(0f, 1f)
                    val on = pick?.day == d.day
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (d.total > 0) Brush.linearGradient(
                                    listOf(ToneBlue.copy(alpha = a), TonePink.copy(alpha = a))
                                ) else SolidColor(SurfaceMuted)
                            )
                            .then(
                                if (on) Modifier.border(1.5.dp, AccentBlue, RoundedCornerShape(8.dp))
                                else Modifier
                            )
                            .tap { onPick(d) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            d.day.takeLast(2),
                            fontSize = 9.sp,
                            color = if (d.total > 0) Color.White else TextSecondary
                        )
                    }
                }
                //补齐到 7 格，最后一行没满时不被拉伸
                repeat(7 - row.size) { Spacer(Modifier.size(36.dp)) }
            }
        }
    }
} //方格图

@Composable
private fun MiniStat(label: String, v: Long, modifier: Modifier = Modifier) {
    Column(
        modifier.background(SurfaceMuted, RoundedCornerShape(10.dp)).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(tk(v), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
        Text(label, fontSize = 9.sp, color = TextSecondary)
    }
} //总计卡里的小分块

@Composable
private fun DayField(
    value: String,
    hint: String,
    modifier: Modifier = Modifier,
    onValue: (String) -> Unit
) {
    Box(
        modifier
            .background(SurfaceMuted, RoundedCornerShape(9.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        if (value.isEmpty()) Text(hint, fontSize = 10.sp, color = TextSecondary)
        BasicTextField(
            value = value,
            onValueChange = onValue,
            textStyle = TextStyle(fontSize = 11.sp, color = TextPrimary),
            singleLine = true,
            cursorBrush = SolidColor(AccentBlue),
            modifier = Modifier.fillMaxWidth()
        )
    }
} //日期输入格

private fun dayRangeLabel(items: List<DayUse>): String =
    if (items.isEmpty()) "—" else "${items.first().day} → ${items.last().day}" //柱状图下方的日期跨度
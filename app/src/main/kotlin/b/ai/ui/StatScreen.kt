package b.ai.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.core.DayUse
import b.ai.core.ModelUse
import b.ai.core.Price
import b.ai.core.Prices
import b.ai.core.Stat
import b.ai.core.StatSnap
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
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

/** 金额：小额留 4 位小数，不然 0.00 看不出花没花钱 */
private fun money(v: Double): String =
    if (v >= 1.0) "¥%.2f".format(v) else "¥%.4f".format(v) //金额

/** 统计页状态：拆成 item 后各块共用这一份，别在 item 里各自 remember（滚出屏就丢） */
class StatUi {
    var snap by mutableStateOf(StatSnap())
    var range by mutableIntStateOf(2)
    var fromKey by mutableStateOf("")
    var toKey by mutableStateOf("")
    var pick by mutableStateOf<DayUse?>(null)
    //图表形态：0 = 柱状，1 = 折线
    var chartMode by mutableIntStateOf(0)
    //正在配价的模型名 + 三个输入框的草稿
    var editing by mutableStateOf<String?>(null)
    var eIn by mutableStateOf("")
    var eOut by mutableStateOf("")
    var eCache by mutableStateOf("")
    //已经扫过库就不再重复扫，滚动来回不重复开销
    var loaded = false

    //0=1天 1=7天 2=30天 3=90天 4=自定义
    val shown: List<DayUse>
        get() = when (range) {
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
} //统计页状态

/** 统计页：总计、范围柱状图、本月方格图、模型分类、月活跃，一块一个 item */
fun LazyListScope.statTab(ui: StatUi) {
    item {
    //只进页时扫一次库；切范围只是从已聚合好的天里截，不重复查库
    LaunchedEffect(Unit) {
        if (!ui.loaded) {
            withContext(Dispatchers.IO) { ui.snap = Stat.load() }
            ui.loaded = true
        }
    }
    SectionTitle(stringResource(R.string.st_total), stringResource(R.string.st_total_d))
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                //大数字后面直接跟「/ 花费」，费用跟 token 同一条基线，不再缩成小字
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(tk(ui.snap.total), fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    if (ui.snap.priceOn) {
                        Text(" / ", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                        Text(money(ui.snap.totalCost), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = AccentBlue)
                    }
                }
                Text(stringResource(R.string.st_total_tok), fontSize = 10.sp, color = TextSecondary)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.st_rounds, ui.snap.msgCount), fontSize = 12.sp, color = TextPrimary)
                Text(stringResource(R.string.st_active, ui.snap.activeDays), fontSize = 10.sp, color = TextSecondary)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniStat(stringResource(R.string.st_in), ui.snap.totalPrompt, Modifier.weight(1f))
            MiniStat(stringResource(R.string.st_in_cache), ui.snap.totalCached, Modifier.weight(1f))
            MiniStat(stringResource(R.string.st_out), ui.snap.totalCompletion, Modifier.weight(1f))
        }
        if (ui.snap.totalPrompt > 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.st_cache_rate).format(ui.snap.totalCached * 100.0 / ui.snap.totalPrompt),
                fontSize = 9.sp,
                color = TextSecondary
            )
        }
        //没配过任何模型价格时给一句提示，配过就不占地方
        if (!ui.snap.priceOn) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.st_cost_off), fontSize = 10.sp, color = TextSecondary)
        }
    }
    }

    item {
    Spacer(Modifier.height(14.dp))
    SectionTitle(stringResource(R.string.st_range), stringResource(R.string.st_range_d))
    CardBox {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(stringResource(R.string.st_d1), stringResource(R.string.st_d7), stringResource(R.string.st_d30), stringResource(R.string.st_d90), stringResource(R.string.st_custom)).forEachIndexed { i, label ->
                PolicyChip(label, ui.range == i) { ui.range = i }
            }
        }
        if (ui.range == 4) {
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DayField(ui.fromKey, stringResource(R.string.st_from), Modifier.weight(1f)) { ui.fromKey = it }
                Text("→", fontSize = 11.sp, color = TextSecondary)
                DayField(ui.toKey, stringResource(R.string.st_to), Modifier.weight(1f)) { ui.toKey = it }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(tk(ui.shown.sumOf { it.total }), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.st_tok_days, ui.shown.size),
                fontSize = 10.sp,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            stringResource(
                R.string.st_split,
                tk(ui.shown.sumOf { it.prompt }),
                tk(ui.shown.sumOf { it.cached }),
                tk(ui.shown.sumOf { it.completion })
            ),
            fontSize = 9.sp,
            color = TextSecondary
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PolicyChip(stringResource(R.string.st_chart_bar), ui.chartMode == 0) { ui.chartMode = 0 }
            PolicyChip(stringResource(R.string.st_chart_line), ui.chartMode == 1) { ui.chartMode = 1 }
        }
        Spacer(Modifier.height(6.dp))
        if (ui.chartMode == 1) LineChart(ui.shown, ui.pick) { ui.pick = it }
        else BarChart(ui.shown) { ui.pick = it }
        Spacer(Modifier.height(6.dp))
        Text(dayRangeLabel(ui.shown), fontSize = 9.sp, color = TextSecondary)
        if (ui.snap.priceOn) {
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.st_cost_range, money(ui.snap.costOf(ui.shown))),
                fontSize = 10.sp,
                color = TextPrimary
            )
        }
        ui.pick?.let { d ->
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.st_day_tip, d.day, tk(d.total), tk(d.prompt), tk(d.cached), tk(d.completion)),
                fontSize = 10.sp,
                color = AccentBlue
            )
        }
    }
    }

    item {
    Spacer(Modifier.height(14.dp))
    SectionTitle(stringResource(R.string.st_grid), stringResource(R.string.st_grid_d, ui.snap.monthLen))
    CardBox {
        MonthGrid(ui.snap.monthDay, ui.pick) { ui.pick = it }
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
                stringResource(R.string.st_grid_note, ui.snap.monthDay.count { it.total > 0 }, ui.snap.monthLen),
                fontSize = 9.sp,
                color = TextSecondary
            )
        }
    }
    }

    item {
    val scope = rememberCoroutineScope()
    Spacer(Modifier.height(14.dp))
    SectionTitle(stringResource(R.string.st_models), stringResource(R.string.st_models_d))
    CardBox {
        if (ui.snap.models.isEmpty()) {
            Text(stringResource(R.string.st_empty), fontSize = 11.sp, color = TextSecondary)
        } else {
            //条形长度按第一名归一，不然小模型会缩成一条线看不见
            val maxModel = ui.snap.models.first().total.coerceAtLeast(1L)
            val all = ui.snap.total.coerceAtLeast(1L)
            ui.snap.models.take(8).forEach { m ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        m.name,
                        fontSize = 11.sp,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(tk(m.total), fontSize = 10.sp, color = TextSecondary)
                    Spacer(Modifier.width(6.dp))
                    Text("%.0f%%".format(m.total * 100.0 / all), fontSize = 10.sp, color = AccentBlue)
                }
                Spacer(Modifier.height(3.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .liquidBg(RoundedCornerShape(3.dp), tint = SurfaceMuted)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((m.total.toFloat() / maxModel).coerceIn(0.02f, 1f))
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(listOf(ToneBlue, TonePink)),
                                RoundedCornerShape(3.dp)
                            )
                    )
                }
                Spacer(Modifier.height(6.dp))
                //第二行：这个模型花了多少钱 + 单独配价的入口（纯文字，不做胶囊贴在条形上）
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (m.cost > 0.0) stringResource(R.string.st_cost_of, money(m.cost))
                        else stringResource(R.string.st_cost_none),
                        fontSize = 10.sp,
                        color = if (m.cost > 0.0) TextPrimary else TextSecondary
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        (if (ui.editing == m.name) "▴ " else "▾ ") + stringResource(R.string.st_model_price),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = AccentBlue,
                        modifier = Modifier
                            .tap {
                                ui.editing = if (ui.editing == m.name) {
                                    null
                                } else {
                                    //开的时候带出这个模型自己的价，没配过就是空
                                    val p = Prices.get(m.name) ?: Price()
                                    ui.eIn = if (p.inP > 0f) p.inP.toString() else ""
                                    ui.eOut = if (p.outP > 0f) p.outP.toString() else ""
                                    ui.eCache = if (p.cacheP > 0f) p.cacheP.toString() else ""
                                    m.name
                                }
                            }
                            .padding(start = 12.dp, top = 6.dp, bottom = 6.dp)
                    )
                }
                if (ui.editing == m.name) {
                    Spacer(Modifier.height(5.dp))
                    Text(stringResource(R.string.st_model_price_t), fontSize = 9.sp, color = TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PriceField(ui.eIn, stringResource(R.string.st_price_in), Modifier.weight(1f)) { ui.eIn = it }
                        PriceField(ui.eOut, stringResource(R.string.st_price_out), Modifier.weight(1f)) { ui.eOut = it }
                        PriceField(ui.eCache, stringResource(R.string.st_price_cache), Modifier.weight(1f)) { ui.eCache = it }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        //两个按钮统一 46dp：通用组件 GhostButton 是 42、PrimaryButton 是 46，并排会一高一矮
                        GhostButton(
                            stringResource(R.string.st_price_clear),
                            modifier = Modifier.weight(1f).height(46.dp)
                        ) {
                            Prices.remove(m.name)
                            ui.editing = null
                            scope.launch { withContext(Dispatchers.IO) { ui.snap = Stat.load() } }
                        }
                        PrimaryButton(stringResource(R.string.st_price_save), modifier = Modifier.weight(1f)) {
                            Prices.put(
                                m.name,
                                Price(ui.eIn.toFloatOrNull() ?: 0f, ui.eOut.toFloatOrNull() ?: 0f, ui.eCache.toFloatOrNull() ?: 0f)
                            )
                            ui.editing = null
                            scope.launch { withContext(Dispatchers.IO) { ui.snap = Stat.load() } }
                        }
                    }
                }
                Spacer(Modifier.height(9.dp))
            }
        }
    }
    }

    item {
    Spacer(Modifier.height(14.dp))
    SectionTitle(stringResource(R.string.st_month), stringResource(R.string.st_month_d))
    CardBox {
        val maxA = (ui.snap.months.maxOfOrNull { it.activeDays } ?: 0).coerceAtLeast(1)
        Row(
            Modifier.fillMaxWidth().height(96.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ui.snap.months.forEach { m ->
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
            ui.snap.months.forEach { m ->
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

/** 按天折线图：点线 + 渐变填充，点一下选最近那天 */
@Composable
private fun LineChart(items: List<DayUse>, pick: DayUse?, onPick: (DayUse) -> Unit) {
    if (items.isEmpty()) {
        Text(stringResource(R.string.st_no_data), fontSize = 11.sp, color = TextSecondary)
        return
    }
    val max = items.maxOf { it.total }.coerceAtLeast(1L)
    Box(
        Modifier
            .fillMaxWidth()
            .height(112.dp)
            .pointerInput(items) {
                detectTapGestures { off ->
                    val n = items.size
                    if (n == 1) {
                        onPick(items[0])
                    } else {
                        val step = size.width.toFloat() / (n - 1)
                        if (step > 0f) onPick(items[(off.x / step).roundToInt().coerceIn(0, n - 1)])
                    }
                }
            }
    ) {
        Canvas(Modifier.fillMaxWidth().height(112.dp)) {
            val n = items.size
            val pad = 5.dp.toPx()
            val h = size.height - pad * 2
            val w = size.width
            fun xOf(i: Int): Float = if (n == 1) w / 2f else w * i / (n - 1)
            fun yOf(v: Long): Float = pad + h - (v.toFloat() / max) * h
            val line = Path()
            val fill = Path()
            items.forEachIndexed { i, d ->
                val x = xOf(i)
                val y = yOf(d.total)
                if (i == 0) {
                    line.moveTo(x, y)
                    fill.moveTo(x, size.height)
                    fill.lineTo(x, y)
                } else {
                    line.lineTo(x, y)
                    fill.lineTo(x, y)
                }
            }
            fill.lineTo(xOf(n - 1), size.height)
            fill.close()
            drawPath(
                fill,
                Brush.verticalGradient(listOf(ToneBlue.copy(alpha = 0.30f), ToneBlue.copy(alpha = 0.02f)))
            )
            drawPath(line, ToneBlue, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            items.forEachIndexed { i, d ->
                val c = Offset(xOf(i), yOf(d.total))
                if (pick?.day == d.day) drawCircle(TonePink, radius = 3.6.dp.toPx(), center = c)
                else if (n <= 40 && d.total > 0L) drawCircle(ToneBlue, radius = 1.9.dp.toPx(), center = c)
            }
        }
    }
} //折线图

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
        modifier.liquidBg(RoundedCornerShape(10.dp), tint = SurfaceMuted).padding(vertical = 8.dp),
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
            .liquidBg(RoundedCornerShape(9.dp), tint = SurfaceMuted)
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

/** 价格输入格：只收数字与小数点，边输边重算费用 */
@Composable
private fun PriceField(value: String, label: String, modifier: Modifier = Modifier, onValue: (String) -> Unit) {
    Column(modifier) {
        Text(label, fontSize = 9.sp, color = TextSecondary)
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .liquidBg(RoundedCornerShape(9.dp), tint = SurfaceMuted)
                .padding(horizontal = 8.dp, vertical = 7.dp)
        ) {
            if (value.isEmpty()) Text("0", fontSize = 11.sp, color = TextSecondary)
            BasicTextField(
                value = value,
                onValueChange = { t ->
                    //只留数字与小数点，最多 6 位，防止输成一长串
                    val clean = t.filter { it.isDigit() || it == '.' }.take(6)
                    onValue("$clean".let { if (it.count { c -> c == '.' } > 1) it.dropLast(1) else it })
                },
                textStyle = TextStyle(fontSize = 11.sp, color = TextPrimary),
                singleLine = true,
                cursorBrush = SolidColor(AccentBlue),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
} //价格输入格
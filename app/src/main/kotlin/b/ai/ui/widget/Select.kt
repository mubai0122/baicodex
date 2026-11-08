package b.ai.ui.widget
import b.ai.ui.dialog.ScrimCard
import b.ai.ui.glass.glassScrollState
import b.ai.ui.glass.liquidBg
import b.ai.net.api.llm.text

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import b.ai.R
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.AccentBlueSoft
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary

//一个可选项：label 是显示文案，selected 决定打勾，onPick 在点中时执行
internal class PickItem(val label: String, val selected: Boolean, val onPick: () -> Unit)

//把「打开选择面板」的入口往下传，四个 tab 不用各自多带一个回调参数
internal val LocalPicker = staticCompositionLocalOf<(String, List<PickItem>) -> Unit> { { _, _ -> } }

/** 下拉选择行：左边标题（可带副标题），右边当前值胶囊 + 下箭头，整行可点；menu 的锚点就是这颗胶囊 */
@Composable
internal fun SelectRow(
    label: String,
    value: String,
    desc: String? = null,
    onClick: () -> Unit,
    menu: @Composable BoxScope.(DpSize) -> Unit = {}
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .tap { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 13.5.sp, color = TextPrimary)
            if (desc != null) Text(desc, fontSize = 10.5.sp, color = TextSecondary)
        }
        Spacer(Modifier.width(10.dp))
        val den = LocalDensity.current
        var pill by remember { mutableStateOf(DpSize.Zero) }
        Box(Modifier.onSizeChanged { pill = with(den) { DpSize(it.width.toDp(), it.height.toDp()) } }) {
            Row(
                Modifier
                    //gate 显式清零：胶囊高 24dp，按最短边判永远过不了 156dp 门槛，会掉回平涂
                    .liquidBg(RoundedCornerShape(9.dp), tint = AccentBlueSoft, gate = 0.dp)
                    .padding(start = 9.dp, end = 5.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(value, fontSize = 12.sp, color = AccentBlue, maxLines = 1)
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = AccentBlue,
                    modifier = Modifier.size(15.dp)
                )
            }
            menu(pill)
        }
    }
} //下拉选择行

/** 类型化的选择字段：点行就地展开下拉，选中后行尾胶囊回显，不再走居中弹窗 */
@Composable
internal fun <T> SelectField(
    label: String,
    value: String,
    options: List<T>,
    desc: String? = null,
    optionLabel: @Composable (T) -> String,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val labels = ArrayList<String>(options.size)
    for (o in options) labels.add(optionLabel(o))
    //菜单宽度固定，右边缘跟胶囊右边缘对齐；用原生 Popup 自绘，材质和位置都自己拿，
    //不走 Material3 的 DropdownMenu（它内部那层 background lambda 会盖掉 liquidBg）
    val menuW = 216.dp
    val den = LocalDensity.current
    val gapPx = with(den) { 4.dp.roundToPx() }
    val padPx = with(den) { 8.dp.roundToPx() }
    val pos = remember(gapPx, padPx) { MenuPos(gapPx, padPx) }
    SelectRow(label, value, desc, onClick = { open = !open }) { _ ->
        if (open) {
            Popup(
                popupPositionProvider = pos,
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true)
            ) {
                Column(
                    Modifier
                        .width(menuW)
                        .heightIn(max = 320.dp)
                        //设置页属于「开玻璃就继承玻璃」的那一半：gate 必须显式清零，
                        //否则只有 3 项的下拉最短边过不了 156dp，会掉回平涂，跟 10 项的下拉材质对不上
                        .liquidBg(RoundedCornerShape(12.dp), veil = 0.42f, always = true, gate = 0.dp)
                        .verticalScroll(glassScrollState())
                        .padding(vertical = 4.dp)
                ) {
                    options.forEachIndexed { i, o ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .tap { open = false; onSelect(o) }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                labels[i],
                                fontSize = 12.5.sp,
                                color = if (isSelected(o)) AccentBlue else TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected(o)) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = AccentBlue,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
} //下拉选择字段（贴胶囊页内展开）

/** 选择面板：当前项打勾，点一项就关面板并回调 */
@Composable
internal fun PickSheet(title: String, items: List<PickItem>, onClose: () -> Unit) {
    ScrimCard(onDismiss = onClose) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Column(
            Modifier
                .heightIn(max = 380.dp)
                .verticalScroll(glassScrollState())
        ) {
            items.forEach { o ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 46.dp)
                        .tap { onClose(); o.onPick() }
                        .padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        o.label,
                        fontSize = 13.5.sp,
                        color = if (o.selected) AccentBlue else TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    if (o.selected) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = AccentBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            GhostButton(stringResource(R.string.se_142), modifier = Modifier.height(36.dp), onClick = onClose)
        }
    }
} //选择面板

//下拉菜单定位：右边缘贴胶囊右缘，下方放不下就翻到胶囊上方，四边留 pad 不出屏
private class MenuPos(private val gap: Int, private val pad: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val w = popupContentSize.width
        val h = popupContentSize.height
        val maxX = (windowSize.width - w - pad).coerceAtLeast(pad)
        val maxY = (windowSize.height - h - pad).coerceAtLeast(pad)
        val x = (anchorBounds.right - w).coerceIn(pad, maxX)
        val below = windowSize.height - anchorBounds.bottom - gap
        val above = anchorBounds.top - gap
        val y = if (h > below && above > below) anchorBounds.top - h - gap else anchorBounds.bottom + gap
        return IntOffset(x, y.coerceIn(pad, maxY))
    }
} //下拉菜单定位
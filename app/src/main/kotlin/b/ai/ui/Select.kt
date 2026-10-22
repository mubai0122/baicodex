package b.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.R
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.AccentBlueSoft
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary

//一个可选项：label 是显示文案，selected 决定打勾，onPick 在点中时执行
internal class PickItem(val label: String, val selected: Boolean, val onPick: () -> Unit)

//把「打开选择面板」的入口往下传，四个 tab 不用各自多带一个回调参数
internal val LocalPicker = staticCompositionLocalOf<(String, List<PickItem>) -> Unit> { { _, _ -> } }

/** 下拉选择行：左边标题（可带副标题），右边当前值胶囊 + 下箭头，整行可点 */
@Composable
internal fun SelectRow(label: String, value: String, desc: String? = null, onClick: () -> Unit) {
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
        Row(
            Modifier
                .background(AccentBlueSoft, RoundedCornerShape(9.dp))
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
    }
} //下拉选择行

/** 类型化的选择字段：选项文案在组合期算好，点行时才弹面板，避免在点击回调里碰 stringResource */
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
    val picker = LocalPicker.current
    val labels = ArrayList<String>(options.size)
    for (o in options) labels.add(optionLabel(o))
    SelectRow(label, value, desc) {
        picker(label, options.mapIndexed { i, o -> PickItem(labels[i], isSelected(o)) { onSelect(o) } })
    }
} //下拉选择字段

/** 选择面板：当前项打勾，点一项就关面板并回调 */
@Composable
internal fun PickSheet(title: String, items: List<PickItem>, onClose: () -> Unit) {
    ScrimCard(onDismiss = onClose) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Column(
            Modifier
                .heightIn(max = 380.dp)
                .verticalScroll(rememberScrollState())
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
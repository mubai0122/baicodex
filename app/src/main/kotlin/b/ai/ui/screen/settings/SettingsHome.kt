package b.ai.ui.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.R
import b.ai.ui.glass.GlassPerf
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import b.ai.ui.widget.CardBox
import b.ai.ui.widget.SectionTitle
import b.ai.ui.widget.tap

/**
 * 分类页：底栏点进来看到的第一屏，只列子页入口。
 * 行尾回显交给各子页自己给（S2 起接 summary），S1 阶段先只显示副标题
 */
fun LazyListScope.seCatHome(catLabel: Int, sub: String, pages: List<SePage>, onOpen: (SePage) -> Unit) {
    item {
        SectionTitle(stringResource(catLabel), sub)
        CardBox {
            pages.forEachIndexed { i, p ->
                if (i > 0) Spacer(Modifier.height(2.dp))
                SeRow(p) { GlassPerf.switched(); onOpen(p) }
            }
        }
    }
} //分类页

/** 专区页：米家 / 音乐 / 协作 / 沙箱都挂这里，先把壳搭出来 */
fun LazyListScope.seHubPage() {
    item {
        SectionTitle(stringResource(R.string.se_hub), stringResource(R.string.se_hub_sub))
        CardBox {
            Text(
                stringResource(R.string.se_hub_soon),
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }
    }
    items(listOf(R.string.se_hub_mi, R.string.se_hub_music, R.string.se_hub_lan, R.string.se_hub_box)) { t ->
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(t), fontSize = 14.sp, color = TextPrimary)
                }
                Text(stringResource(R.string.se_hub_soon_tag), fontSize = 10.sp, color = TextSecondary)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
} //专区（壳）

/** 分类页上的一行：标题 + 副标题 + 右箭头 */
@Composable
fun SeRow(p: SePage, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .tap { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(p.titleRes), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            if (p.subRes != 0) {
                Text(stringResource(p.subRes), fontSize = 10.sp, color = TextSecondary, maxLines = 1)
            }
        }
        Icon(
            Icons.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = AccentBlue,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(2.dp))
    }
} //子页入口行

/** 二级页顶部的返回行：跟系统手势并存的点击退路 */
@Composable
fun SeBackBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp)
            .tap { onBack() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.KeyboardArrowLeft,
            contentDescription = stringResource(R.string.se_059),
            tint = AccentBlue,
            modifier = Modifier.size(18.dp)
        )
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AccentBlue)
    }
} //子页返回条
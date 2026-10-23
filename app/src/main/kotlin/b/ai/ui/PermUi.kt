package b.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.core.Perms
import b.ai.ui.theme.OkGreen
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import b.ai.ui.theme.WarnAmber
import kotlinx.coroutines.delay

/** 特殊权限档位列表：一条一档，状态自己轮询刷新，从系统设置页返回就能看到变化 */
@Composable
fun SpecialPermCard(keys: List<String>, desc: String? = null) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    //轮询而不是监听：这三个开关都没有可靠的变更回调
    LaunchedEffect(Unit) {
        while (true) {
            delay(1500)
            tick++
        }
    }
    Column(Modifier.fillMaxWidth()) {
        if (desc != null) {
            Text(desc, fontSize = 11.sp, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
        }
        keys.forEach { k ->
            val on = remember(tick, k) { Perms.specialOn(ctx, k) }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(Perms.specialName(k), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (on) "已开启" else "未开启",
                            fontSize = 11.sp,
                            color = if (on) OkGreen else WarnAmber
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(Perms.specialDesc(k), fontSize = 11.sp, color = TextSecondary)
                }
                Spacer(Modifier.width(10.dp))
                if (on) {
                    Text("✓", fontSize = 14.sp, color = OkGreen)
                } else {
                    GhostButton("去开启") {
                        Perms.openSpecial(ctx, k)
                        tick++
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            GhostButton("刷新状态") { tick++ }
        }
    }
} //特殊权限档位卡片
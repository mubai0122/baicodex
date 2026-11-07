package b.ai.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.desktop.core.Intent
import b.ai.desktop.core.Session
import b.ai.desktop.core.ShellCore

//侧栏：新建入口 + 标签列表 + 最近会话。所有交互只发 Intent，不碰状态
@Composable
fun Sidebar(core: ShellCore, modifier: Modifier = Modifier) {
    val st = core.state
    Column(modifier.width(260.dp).fillMaxHeight().padding(horizontal = 8.dp, vertical = 6.dp)) {
        Row(
            Modifier.fillMaxWidth().height(34.dp)
                .glass(RoundedCornerShape(10.dp), 0.12f)
                .clickable { core.dispatch(Intent.NewTab) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.size(10.dp))
            Text("+", color = GlassTheme.accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(8.dp))
            Text("新会话", color = GlassTheme.text, fontSize = 12.sp)
        }

        Spacer(Modifier.height(10.dp))
        Text("标签", color = GlassTheme.sub, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))

        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(st.tabs, key = { it.id }) { s -> TabRow(s, s.id == st.active, core) }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(GlassTheme.line))
        Spacer(Modifier.height(8.dp))
        Text("最近", color = GlassTheme.sub, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        LazyColumn(Modifier.height(180.dp).fillMaxWidth()) {
            items(st.recent.reversed(), key = { it.id }) { s ->
                Row(
                    Modifier.fillMaxWidth().height(30.dp)
                        .clickable { core.dispatch(Intent.SelectTab(s.id)) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(s.title, color = GlassTheme.sub, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
} //侧栏

@Composable
private fun TabRow(s: Session, active: Boolean, core: ShellCore) {
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Row(
            Modifier.fillMaxWidth().height(32.dp)
                .glass(RoundedCornerShape(9.dp), if (active) 0.16f else 0.04f)
                .clickable { core.dispatch(Intent.SelectTab(s.id)) }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(5.dp)
                    .background(if (active) GlassTheme.accent else Color.Transparent, RoundedCornerShape(3.dp))
            )
            Spacer(Modifier.size(8.dp))
            Text(
                s.title,
                color = if (active) GlassTheme.text else GlassTheme.sub,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
            Box(Modifier.size(18.dp).clickable { core.dispatch(Intent.CloseTab(s.id)) }, contentAlignment = Alignment.Center) {
                Text("✕", color = GlassTheme.sub, fontSize = 10.sp)
            }
        }
    }
} //标签行
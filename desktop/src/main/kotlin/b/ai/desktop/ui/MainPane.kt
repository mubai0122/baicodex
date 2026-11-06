package b.ai.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.desktop.core.ShellCore

//主区：现在只有空态和输入框底壳，等 Rust 内核接进来再填对话流
@Composable
fun MainPane(core: ShellCore, modifier: Modifier = Modifier) {
    val st = core.state
    val title = st.tabs.firstOrNull { it.id == st.active }?.title
    Column(modifier.fillMaxSize().padding(8.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (title == null) {
                Text("没有打开的会话", color = GlassTheme.sub, fontSize = 13.sp)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(title, color = GlassTheme.text, fontSize = 18.sp)
                    Spacer(Modifier.size(8.dp))
                    Text("对话区待接入 Rust 内核", color = GlassTheme.sub, fontSize = 12.sp)
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().height(52.dp)
                .glass(RoundedCornerShape(13.dp), 0.10f)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("给 BaiCodex 发消息…", color = GlassTheme.sub, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Box(
                Modifier.size(30.dp).background(GlassTheme.accent.copy(alpha = 0.22f), RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("↑", color = GlassTheme.accent, fontSize = 14.sp)
            }
        }
    }
} //主区
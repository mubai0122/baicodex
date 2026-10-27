package b.ai.ui

import androidx.compose.foundation.background
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.core.A2aNode
import b.ai.core.A2aNodes
import b.ai.core.Cfg
import b.ai.exec.A2aSrv
import b.ai.net.A2A
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.AccentBlueSoft
import b.ai.ui.theme.DangerRed
import b.ai.ui.theme.DangerRedSoft
import b.ai.ui.theme.OkGreen
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun A2aPanel(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var serving by remember { mutableStateOf(A2aSrv.running) }
    var srvMsg by remember { mutableStateOf(if (A2aSrv.running) "已监听 " + A2aSrv.baseUrl() else "未开启") }
    var port by remember { mutableStateOf(Cfg.a2aPort.toString()) }
    var token by remember { mutableStateOf(Cfg.a2aToken) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var nodeToken by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    val nodes = A2aNodes.list

    //端口或 token 改了就重开监听，改完直接生效，不用再点一次开关
    fun restart(newPort: Int, newToken: String) {
        Cfg.a2aPort = newPort
        Cfg.a2aToken = newToken
        if (!serving) return
        val msg = A2aSrv.start(newPort)
        srvMsg = msg
        if (msg.contains("失败")) {
            serving = false
            Cfg.a2aOn = false
        }
    }

    ScrimCard(onDismiss = onClose) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("A2A", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
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
                .heightIn(max = 440.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "A2A 1.0 接口。两个方向：本机当客户端调用其他节点，本机当服务端接收任务。",
                fontSize = 10.sp, color = TextSecondary
            )

            Spacer(Modifier.height(10.dp))
            Text("本机服务端", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .background(SurfaceMuted, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (serving) "对外提供 A2A 接口" else "关闭（只当客户端）",
                        fontSize = 12.sp, color = TextPrimary
                    )
                    Text(srvMsg, fontSize = 10.sp, color = if (serving) OkGreen else TextSecondary)
                }
                Switch(
                    checked = serving,
                    onCheckedChange = { on ->
                        Cfg.a2aOn = on
                        if (on) {
                            val p = port.toIntOrNull() ?: Cfg.a2aPort
                            val msg = A2aSrv.start(p)
                            srvMsg = msg
                            serving = !msg.contains("失败")
                            if (!serving) Cfg.a2aOn = false
                        } else {
                            A2aSrv.stop()
                            serving = false
                            srvMsg = "未开启"
                        }
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue),
                    modifier = Modifier.height(28.dp)
                )
            }
            Row(Modifier.fillMaxWidth()) {
                Box(Modifier.weight(1f)) {
                    Field("端口", port, {
                        port = it.filter { c -> c.isDigit() }.take(5)
                        // 改动即生效：服务开着就换个端口重开，不用再点开关
                        port.toIntOrNull()?.let { p -> if (p in 1024..65535) restart(p, token) }
                    }, "8720")
                }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    Field("访问 token（留空不校验）", token, {
                        token = it
                        restart(port.toIntOrNull() ?: Cfg.a2aPort, it)
                    }, "可留空")
                }
            }
            Text(
                "改端口或 token 后，服务开着就直接换新的；token 只建议在可信局域网留空。",
                fontSize = 10.sp, color = TextSecondary
            )
            if (serving) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "AgentCard：" + A2aSrv.baseUrl() + A2A.CARD_PATH,
                    fontSize = 10.sp, color = AccentBlue, maxLines = 2
                )
                Text(
                    "把上面这个地址填进别人的 A2A 客户端就能找到本机。",
                    fontSize = 10.sp, color = TextSecondary
                )
            }

            Spacer(Modifier.height(12.dp))
            Text("对端（本机当客户端）", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
            if (nodes.isEmpty()) {
                Text(
                    "还没加节点。下面填一条地址就能调用其他 A2A 节点。",
                    fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(vertical = 6.dp)
                )
            } else {
                nodes.forEach { n ->
                    NodeRow(
                        n = n,
                        testing = testing == n.id,
                        onTest = {
                            testing = n.id
                            result = ""
                            scope.launch(Dispatchers.IO) {
                                val r = A2A.card(n)
                                withContext(Dispatchers.Main) {
                                    testing = ""
                                    result = r.text
                                }
                            }
                        }
                    )
                }
            }
            Field("对端名字", name, { name = it }, "例如：家里电脑")
            Field("对端地址", url, { url = it }, "http://192.168.1.20:8080")
            Field("对端 token（没有就留空）", nodeToken, { nodeToken = it }, "可留空")
            Spacer(Modifier.height(8.dp))
            Text(
                "保存对端",
                fontSize = 13.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AccentBlue, RoundedCornerShape(10.dp))
                    .tap {
                        if (url.isNotBlank()) {
                            A2aNodes.add(name, url, nodeToken)
                            A2A.forget()
                            name = ""
                            url = ""
                            nodeToken = ""
                            android.widget.Toast
                                .makeText(ctx, "已保存对端", android.widget.Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                    .padding(vertical = 10.dp)
            )
            if (result.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("测试结果", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
                Text(
                    result,
                    fontSize = 10.sp,
                    color = TextPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceMuted, RoundedCornerShape(10.dp))
                        .padding(8.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "注意：服务端跑在 App 进程里，进程被系统杀掉就断了；外部请求不会弹工具确认框，" +
                    "只有「工具策略 = 永久允许」的工具才会被执行，其余一律拒绝。",
                fontSize = 10.sp, color = TextSecondary
            )
        }
    }
} //A2A 面板

@Composable
private fun NodeRow(n: A2aNode, testing: Boolean, onTest: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(SurfaceMuted, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(n.name, fontSize = 12.sp, color = TextPrimary, maxLines = 1)
            Text(n.url, fontSize = 10.sp, color = TextSecondary, maxLines = 1)
        }
        Text(
            if (testing) "连接中…" else "测卡",
            fontSize = 11.sp,
            color = if (testing) TextSecondary else AccentBlue,
            modifier = Modifier
                .background(AccentBlueSoft, RoundedCornerShape(8.dp))
                .tap(enabled = !testing) { onTest() }
                .padding(horizontal = 8.dp, vertical = 5.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "删除",
            fontSize = 11.sp,
            color = DangerRed,
            modifier = Modifier
                .background(DangerRedSoft, RoundedCornerShape(8.dp))
                .tap {
                    A2aNodes.remove(n.id)
                    A2A.forget()
                }
                .padding(horizontal = 8.dp, vertical = 5.dp)
        )
    }
} //一个 A2A 对端
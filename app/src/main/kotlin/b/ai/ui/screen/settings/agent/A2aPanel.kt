/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:22
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package b.ai.ui.screen.settings.agent
import b.ai.ui.screen.settings.about.*
import b.ai.ui.screen.settings.agent.*
import b.ai.ui.screen.settings.core.*
import b.ai.ui.screen.settings.ia.*
import b.ai.ui.screen.settings.model.*
import b.ai.ui.screen.settings.perm.*
import b.ai.ui.screen.settings.ui.*
import b.ai.ui.screen.settings.chat.*
import b.ai.ui.screen.settings.kit.*
import androidx.compose.ui.res.stringResource
import b.ai.R
import b.ai.ui.dialog.ScrimCard
import b.ai.ui.glass.glassScrollState
import b.ai.ui.glass.liquidBg
import b.ai.ui.widget.Field
import b.ai.ui.widget.tap
import b.ai.App
import b.ai.net.api.llm.text

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
import b.ai.net.api.a2a.A2aNode
import b.ai.net.api.a2a.A2aNodes
import b.ai.core.cfg.app.Cfg
import b.ai.net.api.a2a.A2aSrv
import b.ai.net.api.a2a.A2A
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
    var srvMsg by remember { mutableStateOf(if (A2aSrv.running) b.ai.App.self.getString(R.string.tx_6c90deb5) + A2aSrv.baseUrl() else b.ai.App.self.getString(R.string.tx_ea4a363d)) }
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
        token = Cfg.a2aToken //启动时若 token 为空会被自动生成，这里同步回输入框
        if (msg.contains("失败")) {
            serving = false
            Cfg.a2aOn = false
        }
    } //重启监听

    ScrimCard(onDismiss = onClose) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("A2A", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.tx_b15d9127),
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier
                    .liquidBg(RoundedCornerShape(8.dp), tint = SurfaceMuted)
                    .tap { onClose() }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .heightIn(max = 440.dp)
                .verticalScroll(glassScrollState())
        ) {
            Text(
                stringResource(R.string.tx_f147798d),
                fontSize = 10.sp, color = TextSecondary
            )

            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.tx_dec42aa1), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .liquidBg(RoundedCornerShape(10.dp), tint = SurfaceMuted)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (serving) stringResource(R.string.tx_53d28c51) else stringResource(R.string.tx_3244f52a),
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
                            token = Cfg.a2aToken //可能刚被自动生成
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
                    Field(stringResource(R.string.tx_c76cfefe), port, {
                        port = it.filter { c -> c.isDigit() }.take(5)
                        // 改动即生效：服务开着就换个端口重开，不用再点开关
                        port.toIntOrNull()?.let { p -> if (p in 1024..65535) restart(p, token) }
                    }, "8720")
                }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    Field(stringResource(R.string.tx_9ed74fb4), token, {
                        token = it
                        restart(port.toIntOrNull() ?: Cfg.a2aPort, it)
                    }, "留空则自动生成")
                }
            }
            Text(
                stringResource(R.string.tx_96ed536e),
                fontSize = 10.sp, color = TextSecondary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.tx_9c4020f2),
                fontSize = 10.sp,
                color = AccentBlue,
                modifier = Modifier
                    .tap {
                        val t = A2aSrv.newToken()
                        token = t
                        restart(port.toIntOrNull() ?: Cfg.a2aPort, t)
                    }
                    .padding(vertical = 4.dp)
            )
            if (serving) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "AgentCard：" + A2aSrv.baseUrl() + A2A.CARD_PATH,
                    fontSize = 10.sp, color = AccentBlue, maxLines = 2
                )
                Text(
                    stringResource(R.string.tx_04af1007),
                    fontSize = 10.sp, color = TextSecondary
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.tx_fb34db95), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
            if (nodes.isEmpty()) {
                Text(
                    stringResource(R.string.tx_5d81c648),
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
            Field(stringResource(R.string.tx_44eac452), name, { name = it }, stringResource(R.string.tx_8d92c895))
            Field(stringResource(R.string.tx_4e40d39b), url, { url = it }, "http://192.168.1.20:8080")
            Field(stringResource(R.string.tx_f729e121), nodeToken, { nodeToken = it }, "可留空")
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.tx_3776a3b6),
                fontSize = 13.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidBg(RoundedCornerShape(10.dp), tint = AccentBlue)
                    .tap {
                        if (url.isNotBlank()) {
                            A2aNodes.add(name, url, nodeToken)
                            A2A.forget()
                            name = ""
                            url = ""
                            nodeToken = ""
                            android.widget.Toast
                                .makeText(ctx, b.ai.App.self.getString(R.string.tx_7eb883d0), android.widget.Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                    .padding(vertical = 10.dp)
            )
            if (result.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.tx_c6c9da2b), fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
                Text(
                    result,
                    fontSize = 10.sp,
                    color = TextPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidBg(RoundedCornerShape(10.dp), tint = SurfaceMuted)
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
internal fun NodeRow(n: A2aNode, testing: Boolean, onTest: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .liquidBg(RoundedCornerShape(10.dp), tint = SurfaceMuted)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(n.name, fontSize = 12.sp, color = TextPrimary, maxLines = 1)
            Text(n.url, fontSize = 10.sp, color = TextSecondary, maxLines = 1)
        }
        Text(
            if (testing) stringResource(R.string.tx_5e8eb212) else stringResource(R.string.tx_75cbe201),
            fontSize = 11.sp,
            color = if (testing) TextSecondary else AccentBlue,
            modifier = Modifier
                .liquidBg(RoundedCornerShape(8.dp), tint = AccentBlueSoft)
                .tap(enabled = !testing) { onTest() }
                .padding(horizontal = 8.dp, vertical = 5.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(R.string.tx_2f4aaddd),
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
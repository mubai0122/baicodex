package b.ai.ui.screen.settings

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.App
import b.ai.core.cfg.app.Cfg
import b.ai.core.cfg.seed.Seeds
import b.ai.net.api.a2a.A2aSrv
import b.ai.net.lan.Lan
import b.ai.net.lan.LanCfg
import b.ai.net.lan.Team
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.DangerRed
import b.ai.ui.theme.OkGreen
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import b.ai.ui.widget.CardBox
import b.ai.ui.widget.ChoiceRow
import b.ai.ui.widget.Field
import b.ai.ui.widget.GhostButton
import b.ai.ui.widget.SectionTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

//局域网互联页：发现开关、本机身份与配对码、扫描配对、已配对设备、端口并发、热点入口
fun LazyListScope.lanSec() {
    item { LanBody() }
} //局域网互联页

@Composable
private fun LanBody() {
    var tick by remember { mutableStateOf(0) }
    var msg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val c = remember(tick) { LanCfg.load() }
    val peers = remember(tick) { LanCfg.peers() }
    var name by remember(tick) { mutableStateOf(c.name) }
    var portDraft by remember(tick) { mutableStateOf(c.port.toString()) }
    var found by remember { mutableStateOf(listOf<LanCfg.Peer>()) }
    var codeDraft by remember { mutableStateOf("") }
    var online by remember { mutableStateOf(mapOf<String, Boolean>()) }
    var a2aMsg by remember(tick) { mutableStateOf("") }
    var a2aPortDraft by remember(tick) { mutableStateOf(Cfg.a2aPort.toString()) }
    var detail by remember { mutableStateOf("") }
    //进页面补一次监听：开机后直接进设置页时，后台可能还没把端口接上
    LaunchedEffect(Unit) {
        if (c.on && !Lan.isRunning()) Lan.start()
    }
    Column(Modifier.fillMaxWidth()) {
        SectionTitle("面对面团队协作", "一台派活、一台接活：两边的大模型都能调工具，配对后就能把任务交给对方跑")
        CardBox {
            Text(Team.self(), fontSize = 11.sp, color = TextSecondary)
            Spacer(Modifier.height(4.dp))
            Text(Seeds.report(), fontSize = 10.sp, color = TextSecondary)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("允许对面派活进来", fontSize = 14.sp, color = TextPrimary)
                    Text(
                        if (A2aSrv.running) "正在 " + A2aSrv.baseUrl() + " 接活" else "未接活",
                        fontSize = 10.sp,
                        color = if (A2aSrv.running) OkGreen else TextSecondary
                    )
                }
                Switch(
                    checked = Cfg.a2aOn,
                    onCheckedChange = { on ->
                        Cfg.a2aOn = on
                        a2aMsg = if (on) {
                            val p = a2aPortDraft.trim().toIntOrNull() ?: Cfg.a2aPort
                            Cfg.a2aPort = p
                            val m = A2aSrv.start(p)
                            if (!m.startsWith("已监听")) Cfg.a2aOn = false
                            m
                        } else {
                            A2aSrv.stop()
                            "已停止接活"
                        }
                        tick++
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
                )
            }
            Spacer(Modifier.height(8.dp))
            Field(label = "接活端口", value = a2aPortDraft, onValueChange = { a2aPortDraft = it }, placeholder = "8720")
            Spacer(Modifier.height(8.dp))
            GhostButton("保存端口并重开", modifier = Modifier.height(38.dp)) {
                val p = a2aPortDraft.trim().toIntOrNull()
                if (p == null || p !in 1024..65535) {
                    a2aMsg = "端口要 1024~65535 之间的数字"
                } else {
                    Cfg.a2aPort = p
                    Cfg.a2aOn = true
                    val m = A2aSrv.start(p)
                    if (!m.startsWith("已监听")) Cfg.a2aOn = false
                    a2aMsg = m
                }
                tick++
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "对面要在它那台也打开这个开关，再跟你配对一次——配对时两边会交换接活端口与凭据，之后模型就能互相派活。",
                fontSize = 10.sp,
                color = TextSecondary
            )
            if (a2aMsg.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(a2aMsg, fontSize = 11.sp, color = if (a2aMsg.contains("已")) OkGreen else DangerRed)
            }
        }
        Spacer(Modifier.height(14.dp))
        SectionTitle("设备发现", "打开后本机才应答别人的扫描；关掉只影响「被发现」，自己扫别人仍然可以")
        CardBox {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("允许被发现", fontSize = 14.sp, color = TextPrimary)
                    Text(
                        if (Lan.isRunning()) "正在监听端口 ${c.port}" else "未监听",
                        fontSize = 10.sp,
                        color = if (Lan.isRunning()) OkGreen else TextSecondary
                    )
                }
                Switch(
                    checked = c.on,
                    onCheckedChange = { on ->
                        val err = LanCfg.save(LanCfg.Cfg(on, c.name, c.port, c.conc, c.code))
                        msg = if (err.isNotEmpty()) err else if (on) Lan.start().ifEmpty { "已开始监听" } else {
                            Lan.stop(); "已停止监听"
                        }
                        tick++
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
                )
            }
            if (msg.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(msg, fontSize = 11.sp, color = if (msg.contains("已") || msg.contains("成功")) OkGreen else DangerRed)
            }
        }
        Spacer(Modifier.height(14.dp))
        SectionTitle("本机身份", "名字和配对码是给对方看的；配对码换了以后旧码立刻失效")
        CardBox {
            Field(label = "本机名称", value = name, onValueChange = { name = it }, placeholder = "客厅平板")
            Spacer(Modifier.height(8.dp))
            GhostButton("保存名称", modifier = Modifier.height(38.dp)) {
                val err = LanCfg.save(LanCfg.Cfg(c.on, name, c.port, c.conc, c.code))
                msg = if (err.isEmpty()) "名称已保存" else err
                tick++
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("配对码 " + c.code, fontSize = 16.sp, color = TextPrimary)
                    Text("对方配对时要输这一串", fontSize = 10.sp, color = TextSecondary)
                }
                GhostButton("换一个", modifier = Modifier.height(34.dp).width(76.dp)) {
                    LanCfg.regenCode()
                    msg = "配对码已更新，旧码作废"
                    tick++
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("设备号 " + LanCfg.selfId(), fontSize = 10.sp, color = TextSecondary)
        }
        Spacer(Modifier.height(14.dp))
        SectionTitle("发现设备", "同一 Wi-Fi 下点扫描；对方也要把「允许被发现」打开")
        CardBox {
            GhostButton("扫描局域网", modifier = Modifier.height(38.dp)) {
                msg = "正在扫描…"
                scope.launch {
                    val r = withContext(Dispatchers.IO) { Lan.scan(2500) }
                    found = r
                    msg = if (r.isEmpty()) "没扫到设备（不同网段，或对面没开发现）" else "扫到 ${r.size} 台"
                }
            }
            if (found.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Field(
                    label = "对方的配对码",
                    value = codeDraft,
                    onValueChange = { codeDraft = it },
                    placeholder = "6 位数字"
                )
                Spacer(Modifier.height(8.dp))
                found.forEach { p ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name.ifEmpty { p.id }, fontSize = 14.sp, color = TextPrimary)
                            Text(
                                "${p.ip}:${p.port}" + if (p.a2aPort > 0) " · 可接活" else " · 没开协作",
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                        }
                        GhostButton("配对", modifier = Modifier.height(34.dp).width(76.dp)) {
                            msg = "正在配对…"
                            scope.launch {
                                val err = withContext(Dispatchers.IO) { Lan.pair(p, codeDraft) }
                                msg = if (err.isEmpty()) "配对成功：${p.name.ifEmpty { p.id }}" else err
                                if (err.isEmpty()) found = found.filter { it.id != p.id }
                                tick++
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        SectionTitle("已配对设备", "探活成功才算在线；点「协作」看对面能不能接活，能接活就能把任务派过去")
        CardBox {
            if (peers.isEmpty()) {
                Text("还没有配对过的设备", fontSize = 12.sp, color = TextSecondary)
            } else {
                peers.forEachIndexed { i, p ->
                    if (i > 0) Spacer(Modifier.height(10.dp))
                    Column(Modifier.fillMaxWidth()) {
                        Text(p.name.ifEmpty { p.id }, fontSize = 14.sp, color = TextPrimary)
                        val st = when (online[p.id]) {
                            true -> "在线"
                            false -> "无应答"
                            null -> "未探活"
                        }
                        Text(
                            "${p.ip}:${p.port} · $st · " + (if (p.a2aPort > 0) "可接活 ${p.a2aPort}" else "没开协作"),
                            fontSize = 10.sp,
                            color = if (online[p.id] == true) OkGreen else TextSecondary
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GhostButton("探活", modifier = Modifier.height(32.dp).width(64.dp)) {
                                msg = "正在探活…"
                                scope.launch {
                                    val ok = withContext(Dispatchers.IO) { Lan.ping(p) }
                                    online = online + (p.id to ok)
                                    msg = if (ok) "${p.name.ifEmpty { p.id }} 在线" else "没收到应答"
                                    tick++
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            GhostButton("协作", modifier = Modifier.height(32.dp).width(64.dp)) {
                                detail = "正在问对面…"
                                scope.launch {
                                    val r = withContext(Dispatchers.IO) { Team.card(p) }
                                    detail = if (r.ok) r.text.take(300) else "协作不通：" + r.text
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            GhostButton("移除", modifier = Modifier.height(32.dp).width(64.dp)) {
                                LanCfg.removePeer(p.id)
                                msg = "已解除配对"
                                detail = ""
                                tick++
                            }
                        }
                    }
                }
                if (detail.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(detail, fontSize = 10.sp, color = TextSecondary)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        SectionTitle("传输端口与并发", "端口要两台设备一致才能互相发现；并发是同时传输的任务数上限")
        CardBox {
            Field(label = "端口", value = portDraft, onValueChange = { portDraft = it }, placeholder = "8388")
            Spacer(Modifier.height(8.dp))
            GhostButton("保存端口", modifier = Modifier.height(38.dp)) {
                val p = portDraft.trim().toIntOrNull()
                if (p == null || p !in 1024..65535) {
                    msg = "端口要 1024~65535 之间的数字"
                } else {
                    val err = LanCfg.save(LanCfg.Cfg(c.on, c.name, p, c.conc, c.code))
                    msg = if (err.isEmpty()) "端口改成 $p，重开开关后生效" else err
                    tick++
                }
            }
            Spacer(Modifier.height(10.dp))
            listOf(1, 2, 3, 4, 6, 8).forEach { n ->
                ChoiceRow("并发 $n 路", null, c.conc == n) {
                    val err = LanCfg.save(LanCfg.Cfg(c.on, c.name, c.port, n, c.code))
                    msg = if (err.isEmpty()) "并发改成 $n 路" else err
                    tick++
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        SectionTitle("热点自建", "两个设备都不在同一个 Wi-Fi 时，用手机热点当传输通道：一台开热点，另一台连上再扫描")
        CardBox {
            Text(
                "开热点那台记得把这里也设成同一个端口；连上以后扫描不到就先看是不是被系统挡了局域网访问权限。",
                fontSize = 11.sp,
                color = TextSecondary
            )
            Spacer(Modifier.height(8.dp))
            GhostButton("打开热点设置", modifier = Modifier.height(38.dp)) {
                msg = if (openTether()) "已打开系统热点设置" else "这台机器没有热点设置页，去系统设置里找「个人热点」"
            }
        }
        Spacer(Modifier.height(10.dp))
    }
} //局域网互联正文

private fun openTether(): Boolean {
    val list = listOf(
        "android.settings.WIFI_TETHER_SETTINGS",
        "android.settings.TETHER_SETTINGS",
        "android.settings.WIRELESS_SETTINGS"
    )
    for (a in list) {
        val i = Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { App.self.startActivity(i) }.isSuccess) return true
    }
    return false
} //逐个试热点设置入口，成功即返回
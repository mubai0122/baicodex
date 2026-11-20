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

package b.ai.ui.screen.settings

import androidx.compose.ui.res.stringResource
import b.ai.R
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
        SectionTitle(stringResource(R.string.tx_eea76d6e), stringResource(R.string.tx_4f487d11))
        CardBox {
            Text(Team.self(), fontSize = 11.sp, color = TextSecondary)
            Spacer(Modifier.height(4.dp))
            Text(Seeds.report(), fontSize = 10.sp, color = TextSecondary)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.tx_57d21753), fontSize = 14.sp, color = TextPrimary)
                    Text(
                        if (A2aSrv.running) stringResource(R.string.tx_a9fc6f2f) + A2aSrv.baseUrl() + " 接活" else stringResource(R.string.tx_cca228c7),
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
            Field(label = stringResource(R.string.tx_bf8b66f3), value = a2aPortDraft, onValueChange = { a2aPortDraft = it }, placeholder = "8720")
            Spacer(Modifier.height(8.dp))
            GhostButton(stringResource(R.string.tx_a25be09d), modifier = Modifier.height(38.dp)) {
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
                stringResource(R.string.tx_42ac4191),
                fontSize = 10.sp,
                color = TextSecondary
            )
            if (a2aMsg.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(a2aMsg, fontSize = 11.sp, color = if (a2aMsg.contains("已")) OkGreen else DangerRed)
            }
        }
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.tx_bf77571e), stringResource(R.string.tx_8624e553))
        CardBox {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.tx_37e72ddf), fontSize = 14.sp, color = TextPrimary)
                    Text(
                        if (Lan.isRunning()) stringResource(R.string.tx_19dd9c89, c.port) else stringResource(R.string.tx_a6821857),
                        fontSize = 10.sp,
                        color = if (Lan.isRunning()) OkGreen else TextSecondary
                    )
                }
                Switch(
                    checked = c.on,
                    onCheckedChange = { on ->
                        val err = LanCfg.save(LanCfg.Cfg(on, c.name, c.port, c.conc, c.code))
                        msg = if (err.isNotEmpty()) err else if (on) Lan.start().ifEmpty { b.ai.App.self.getString(R.string.tx_dc7f3530) } else {
                            Lan.stop(); "已停止监听"
                        }
                        tick++
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
                )
            }
            if (msg.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(msg, fontSize = 11.sp, color = if (msg.contains("已") || msg.contains(stringResource(R.string.tx_330363df))) OkGreen else DangerRed)
            }
        }
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.tx_53528c9d), stringResource(R.string.tx_49d52c8d))
        CardBox {
            Field(label = stringResource(R.string.tx_4c9562fe), value = name, onValueChange = { name = it }, placeholder = stringResource(R.string.tx_857abb80))
            Spacer(Modifier.height(8.dp))
            GhostButton(stringResource(R.string.tx_160f1630), modifier = Modifier.height(38.dp)) {
                val err = LanCfg.save(LanCfg.Cfg(c.on, name, c.port, c.conc, c.code))
                msg = if (err.isEmpty()) b.ai.App.self.getString(R.string.tx_2bf9e6b5) else err
                tick++
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.tx_03d448d3) + c.code, fontSize = 16.sp, color = TextPrimary)
                    Text(stringResource(R.string.tx_7970c992), fontSize = 10.sp, color = TextSecondary)
                }
                GhostButton(stringResource(R.string.tx_b152d341), modifier = Modifier.height(34.dp).width(76.dp)) {
                    LanCfg.regenCode()
                    msg = "配对码已更新，旧码作废"
                    tick++
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.tx_97ab69b3) + LanCfg.selfId(), fontSize = 10.sp, color = TextSecondary)
        }
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.tx_8d4d4b42), stringResource(R.string.tx_5d029e5a))
        CardBox {
            GhostButton(stringResource(R.string.tx_3a8e52ef), modifier = Modifier.height(38.dp)) {
                msg = "正在扫描…"
                scope.launch {
                    val r = withContext(Dispatchers.IO) { Lan.scan(2500) }
                    found = r
                    msg = if (r.isEmpty()) b.ai.App.self.getString(R.string.tx_d37a76e7) else b.ai.App.self.getString(R.string.tx_1488e82c, r.size)
                }
            }
            if (found.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Field(
                    label = stringResource(R.string.tx_4a8edc80),
                    value = codeDraft,
                    onValueChange = { codeDraft = it },
                    placeholder = stringResource(R.string.tx_a87595f9)
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
                                "${p.ip}:${p.port}" + if (p.a2aPort > 0) stringResource(R.string.tx_d424955f) else stringResource(R.string.tx_af1f045b),
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                        }
                        GhostButton(stringResource(R.string.tx_e33ff6aa), modifier = Modifier.height(34.dp).width(76.dp)) {
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
        SectionTitle(stringResource(R.string.tx_96bfbadc), stringResource(R.string.tx_8eafe666))
        CardBox {
            if (peers.isEmpty()) {
                Text(stringResource(R.string.tx_6261755b), fontSize = 12.sp, color = TextSecondary)
            } else {
                peers.forEachIndexed { i, p ->
                    if (i > 0) Spacer(Modifier.height(10.dp))
                    Column(Modifier.fillMaxWidth()) {
                        Text(p.name.ifEmpty { p.id }, fontSize = 14.sp, color = TextPrimary)
                        val st = when (online[p.id]) {
                            true -> stringResource(R.string.tx_68905cf3)
                            false -> stringResource(R.string.tx_ac6913a4)
                            null -> stringResource(R.string.tx_fc7d3267)
                        }
                        Text(
                            "${p.ip}:${p.port} · $st · " + (if (p.a2aPort > 0) stringResource(R.string.tx_68e55fc5, p.a2aPort) else stringResource(R.string.tx_6d83b7fe)),
                            fontSize = 10.sp,
                            color = if (online[p.id] == true) OkGreen else TextSecondary
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GhostButton(stringResource(R.string.tx_0edf7a8b), modifier = Modifier.height(32.dp).width(64.dp)) {
                                msg = "正在探活…"
                                scope.launch {
                                    val ok = withContext(Dispatchers.IO) { Lan.ping(p) }
                                    online = online + (p.id to ok)
                                    msg = if (ok) "${p.name.ifEmpty { p.id }} 在线" else b.ai.App.self.getString(R.string.tx_ca4ec8a5)
                                    tick++
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            GhostButton(stringResource(R.string.tx_904e740d), modifier = Modifier.height(32.dp).width(64.dp)) {
                                detail = "正在问对面…"
                                scope.launch {
                                    val r = withContext(Dispatchers.IO) { Team.card(p) }
                                    detail = if (r.ok) r.text.take(300) else b.ai.App.self.getString(R.string.tx_cdd9c35a) + r.text
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            GhostButton(stringResource(R.string.tx_86048b4f), modifier = Modifier.height(32.dp).width(64.dp)) {
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
        SectionTitle(stringResource(R.string.tx_41b91d77), stringResource(R.string.tx_d765fbf8))
        CardBox {
            Field(label = stringResource(R.string.tx_c76cfefe), value = portDraft, onValueChange = { portDraft = it }, placeholder = "8388")
            Spacer(Modifier.height(8.dp))
            GhostButton(stringResource(R.string.tx_4bece587), modifier = Modifier.height(38.dp)) {
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
        SectionTitle(stringResource(R.string.tx_9e1fd27b), stringResource(R.string.tx_34a1a141))
        CardBox {
            Text(
                stringResource(R.string.tx_c054d288),
                fontSize = 11.sp,
                color = TextSecondary
            )
            Spacer(Modifier.height(8.dp))
            GhostButton(stringResource(R.string.tx_87d6084a), modifier = Modifier.height(38.dp)) {
                msg = if (openTether()) b.ai.App.self.getString(R.string.tx_0eafe8f0) else b.ai.App.self.getString(R.string.tx_8a67e9a7)
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
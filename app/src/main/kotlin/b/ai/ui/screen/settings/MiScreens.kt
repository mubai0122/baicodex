/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:23
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
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.net.mi.MiAuth
import b.ai.net.mi.MiConf
import b.ai.native.security.MiCrypto
import b.ai.net.mi.MiHome
import b.ai.net.mi.MiStore
import b.ai.ui.glass.GlassPerf
import b.ai.ui.theme.DangerRed
import b.ai.ui.theme.OkGreen
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import b.ai.ui.widget.CardBox
import b.ai.ui.widget.ChoiceRow
import b.ai.ui.widget.Field
import b.ai.ui.widget.GhostButton
import b.ai.ui.widget.SectionTitle
import b.ai.ui.widget.SelectField
import b.ai.ui.widget.tap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 米家专区首页：三个入口 + 自检；子页内容在同文件下方 */
fun LazyListScope.miHubSec() {
    item {
        CardBox {
            seChildren("ext_mi").forEachIndexed { i, p ->
                if (i > 0) Spacer(Modifier.height(2.dp))
                SeRow(p) { GlassPerf.switched(); SeNav.push(p) }
            }
        }
    }
    miStatusSec()
} //米家专区首页

/** 能力自检：加解密是否算对、接入身份有没有注入，结果直接在页面上给，不靠日志猜 */
private fun LazyListScope.miStatusSec() {
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.tx_6f1c8d30), stringResource(R.string.tx_b736eacc))
        CardBox {
            val cryptoOk = remember { MiCrypto.selfTest() }
            val clientId = remember { runCatching { MiConf.load().clientId }.getOrDefault("") }
            //密钥算法：AES-128-CBC 与 MD5 都走 C 层 OpenSSL，自检拿标准向量对答案
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.tx_f182d08f), fontSize = 14.sp, color = TextPrimary)
                    Text(
                        if (cryptoOk) stringResource(R.string.tx_c15c91c7) else stringResource(R.string.tx_2b822c64),
                        fontSize = 11.sp,
                        color = if (cryptoOk) OkGreen else DangerRed
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            //接入身份：只报有没有，不把 client_id 明文打在界面上
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.tx_473954cb), fontSize = 14.sp, color = TextPrimary)
                    Text(
                        if (clientId.isBlank()) stringResource(R.string.tx_07fa5f00)
                        else stringResource(R.string.tx_59e9353c) + clientId.take(4) + stringResource(R.string.tx_a1451e0f) + clientId.length + " 位）",
                        fontSize = 11.sp,
                        color = if (clientId.isBlank()) DangerRed else OkGreen
                    )
                }
            }
        }
    }
} //能力自检

/** 登录子页：授权链接丢给浏览器，回调里的 code 粘回来换 token */
fun LazyListScope.miLoginSec() {
    item {
        SectionTitle(stringResource(R.string.tx_31ddf4f1), stringResource(R.string.tx_99bdc9ec))
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        var logged by remember { mutableStateOf(MiAuth.logged()) }
        var code by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var msg by remember { mutableStateOf("") }
        val cid = remember { MiConf.clientId() }
        val redirect = remember { MiConf.redirect() }
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (logged) stringResource(R.string.tx_0b5961c7) else stringResource(R.string.tx_63e85df1), fontSize = 14.sp, color = TextPrimary)
                    Text(
                        if (cid.isEmpty()) stringResource(R.string.tx_5ee6e604)
                        else stringResource(R.string.tx_69dcc11d) + redirect,
                        fontSize = 11.sp,
                        color = if (cid.isEmpty()) DangerRed else TextSecondary
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            //授权页交给系统浏览器：米家登录要过短信/滑块，WebView 里过不去
            GhostButton(stringResource(R.string.tx_f7552158), modifier = Modifier.height(38.dp)) {
                val url = MiAuth.authUrl()
                if (url.isEmpty()) {
                    msg = "没注入 client_id，拼不出授权链接"
                } else {
                    val ok = runCatching {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }.isSuccess
                    msg = if (ok) b.ai.App.self.getString(R.string.tx_8c8c9140) else b.ai.App.self.getString(R.string.tx_20c5f916)
                }
            }
            Spacer(Modifier.height(8.dp))
            Field(
                label = stringResource(R.string.tx_479c40df),
                value = code,
                onValueChange = { code = it },
                placeholder = stringResource(R.string.tx_2e3dda27),
                secret = false
            )
            Spacer(Modifier.height(8.dp))
            GhostButton(if (busy) stringResource(R.string.tx_0f7d8564) else stringResource(R.string.tx_e2df88e7), modifier = Modifier.height(38.dp)) {
                if (busy) return@GhostButton
                val c = code.trim()
                if (c.isEmpty()) {
                    msg = "先把 code 粘进来"
                    return@GhostButton
                }
                busy = true
                scope.launch {
                    val t = withContext(Dispatchers.IO) { MiAuth.login(c) }
                    busy = false
                    logged = MiAuth.logged()
                    msg = if (t != null) b.ai.App.self.getString(R.string.tx_792b0724) else b.ai.App.self.getString(R.string.tx_9aaf0ba9)
                    if (t != null) code = ""
                }
            }
            if (logged) {
                Spacer(Modifier.height(8.dp))
                GhostButton(stringResource(R.string.tx_44efd179), modifier = Modifier.height(38.dp)) {
                    MiAuth.logout()
                    logged = false
                    msg = "已退出，设备身份保留（换身份等于换一个客户端）"
                }
            }
            if (msg.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(msg, fontSize = 11.sp, color = if (logged || msg.startsWith("已在")) OkGreen else TextSecondary)
            }
        }
    }
} //登录子页

/** 设备子页：同步云端清单、扫局域网回填 IP、手动补一台、读一个属性验证通道 */
fun LazyListScope.miDevSec() {
    item { MiDevBody() }
} //设备子页

/** 设备子页正文：清单 / 手动添加 / 属性读写；状态全在这里，LazyListScope 不是组合上下文放不了 remember */
@Composable
private fun MiDevBody() {
    var tick by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var sel by remember { mutableStateOf("") }
    var siid by remember { mutableStateOf("2") }
    var piid by remember { mutableStateOf("1") }
    var valueText by remember { mutableStateOf("") }
    var readText by remember { mutableStateOf("") }
    var mDid by remember { mutableStateOf("") }
    var mIp by remember { mutableStateOf("") }
    var mTok by remember { mutableStateOf("") }
    var mName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val rows = remember(tick) { MiStore.load() }
    Column(Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.tx_d8c3355e), stringResource(R.string.tx_d8f4952f))
        CardBox {
            if (rows.isEmpty()) {
                Text(stringResource(R.string.tx_f8f0ec92), fontSize = 12.sp, color = TextPrimary)
            } else {
                rows.forEachIndexed { i, r ->
                    if (i > 0) Spacer(Modifier.height(2.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .tap { sel = if (sel == r.did) "" else r.did }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                r.name.ifBlank { "设备 " + r.did },
                                fontSize = 14.sp,
                                color = TextPrimary
                            )
                            Text(
                                (r.model.ifBlank { "型号未知" }) + " · " + r.ip.ifBlank { "IP 未知" } +
                                    " · " + if (r.online) stringResource(R.string.tx_68905cf3) else stringResource(R.string.tx_50d4a850),
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                        }
                        Text(
                            if (MiStore.token(r.did).isEmpty()) stringResource(R.string.tx_a06cda21) else stringResource(R.string.tx_3c10b78c),
                            fontSize = 10.sp,
                            color = if (MiStore.token(r.did).isEmpty()) DangerRed else OkGreen
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row {
                GhostButton(if (busy == "sync") stringResource(R.string.tx_c6a97bb0) else stringResource(R.string.tx_12d11a6b), modifier = Modifier.height(38.dp)) {
                    if (busy.isNotEmpty()) return@GhostButton
                    busy = "sync"
                    scope.launch {
                        msg = withContext(Dispatchers.IO) { MiHome.sync() }
                        busy = ""
                        tick++
                    }
                }
                Spacer(Modifier.width(10.dp))
                GhostButton(if (busy == "scan") stringResource(R.string.tx_68c15533) else stringResource(R.string.tx_f32ae05c), modifier = Modifier.height(38.dp)) {
                    if (busy.isNotEmpty()) return@GhostButton
                    busy = "scan"
                    scope.launch {
                        val n = withContext(Dispatchers.IO) { MiHome.lanScan() }
                        msg = "局域网探到 $n 台"
                        busy = ""
                        tick++
                    }
                }
            }
            if (msg.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(msg, fontSize = 11.sp, color = TextSecondary)
            }
        }
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.tx_63edd678), stringResource(R.string.tx_1cdd6eaf))
        CardBox {
            Field(label = "did", value = mDid, onValueChange = { mDid = it }, placeholder = stringResource(R.string.tx_86cd35c6))
            Spacer(Modifier.height(6.dp))
            Field(label = "IP", value = mIp, onValueChange = { mIp = it }, placeholder = "192.168.1.23")
            Spacer(Modifier.height(6.dp))
            Field(label = "token", value = mTok, onValueChange = { mTok = it }, placeholder = stringResource(R.string.tx_eeb83a34), secret = true)
            Spacer(Modifier.height(6.dp))
            Field(label = stringResource(R.string.tx_02c2d3ce), value = mName, onValueChange = { mName = it }, placeholder = stringResource(R.string.tx_448f42da))
            Spacer(Modifier.height(8.dp))
            GhostButton(stringResource(R.string.tx_d82daace), modifier = Modifier.height(38.dp)) {
                val err = MiHome.addManual(mDid.trim(), mIp.trim(), mTok.trim(), mName.trim())
                msg = if (err.isEmpty()) b.ai.App.self.getString(R.string.tx_032fe963) else err
                if (err.isEmpty()) {
                    mDid = ""
                    mIp = ""
                    mTok = ""
                    mName = ""
                    tick++
                }
            }
        }
        //选中一台设备才展开属性面板：没有 spec 就先按 siid/piid 手输，够验证通道通不通
        if (sel.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            SectionTitle(stringResource(R.string.tx_50e84fc2), stringResource(R.string.tx_1aed6ccf) + sel)
            CardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Field(label = "siid", value = siid, onValueChange = { siid = it }, placeholder = "2")
                    Spacer(Modifier.width(10.dp))
                    Field(label = "piid", value = piid, onValueChange = { piid = it }, placeholder = "1")
                }
                Spacer(Modifier.height(8.dp))
                GhostButton(stringResource(R.string.tx_a72a9ed7), modifier = Modifier.height(38.dp)) {
                    val s = siid.trim().toIntOrNull()
                    val p = piid.trim().toIntOrNull()
                    if (s == null || p == null) {
                        readText = "siid / piid 必须是数字"
                        return@GhostButton
                    }
                    scope.launch {
                        val v = withContext(Dispatchers.IO) { MiHome.read(sel, s, p) }
                        readText = if (v == null) b.ai.App.self.getString(R.string.tx_f18fd725) else "当前值：$v"
                    }
                }
                if (readText.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(readText, fontSize = 11.sp, color = TextSecondary)
                }
                Spacer(Modifier.height(10.dp))
                Field(label = stringResource(R.string.tx_b9fbdeb2), value = valueText, onValueChange = { valueText = it }, placeholder = "true / 30 / \"auto\"")
                Spacer(Modifier.height(8.dp))
                GhostButton(stringResource(R.string.tx_96f68a9d), modifier = Modifier.height(38.dp)) {
                    val s = siid.trim().toIntOrNull()
                    val p = piid.trim().toIntOrNull()
                    if (s == null || p == null) {
                        readText = "siid / piid 必须是数字"
                        return@GhostButton
                    }
                    val v: Any = valueText.trim().toBooleanStrictOrNull()
                        ?: valueText.trim().toIntOrNull()
                        ?: valueText.trim()
                    scope.launch {
                        val err = withContext(Dispatchers.IO) { MiHome.write(sel, s, p, v) }
                        readText = if (err == null) b.ai.App.self.getString(R.string.tx_728c67c3) else err
                    }
                }
            }
        }
    }
} //设备子页

/** 通道子页：区域、局域网优先、超时与诊断 */
fun LazyListScope.miChSec() {
    item { MiChBody() }
} //通道子页

/** 通道子页正文：区域 / 局域网优先 / 超时 / 诊断，改完立刻写盘 */
@Composable
private fun MiChBody() {
    var tick by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    val c = remember(tick) { MiConf.load() }
    var to by remember(tick) { mutableStateOf(c.timeoutMs.toString()) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.tx_ca160a43), stringResource(R.string.tx_ecb7e8a2))
        CardBox {
            SelectField(
                stringResource(R.string.tx_d3ce40d8),
                c.region,
                listOf("cn", "de", "sg", "us", "i2"),
                optionLabel = { it },
                isSelected = { c.region == it },
                onSelect = { r ->
                    msg = MiConf.save(MiConf.Cfg(c.clientId, c.redirect, r, c.lanFirst, c.timeoutMs))
                    if (msg.isEmpty()) msg = "区域已切到 $r，重新登录一次才会用新区域的接口"
                    tick++
                }
            )
            Spacer(Modifier.height(8.dp))
            ChoiceRow(
                stringResource(R.string.tx_8190cc38),
                if (c.lanFirst) stringResource(R.string.tx_f745e83a) else stringResource(R.string.tx_84d198e1),
                c.lanFirst
            ) {
                msg = MiConf.save(MiConf.Cfg(c.clientId, c.redirect, c.region, !c.lanFirst, c.timeoutMs))
                tick++
            }
            Spacer(Modifier.height(8.dp))
            Field(
                label = stringResource(R.string.tx_fbb1d2ab),
                value = to,
                onValueChange = { to = it },
                placeholder = "4000",
                secret = false
            )
            Spacer(Modifier.height(8.dp))
            //超时不跟着每个按键写盘：填完点保存，顺便卡一下合理区间
            GhostButton(stringResource(R.string.tx_86d74002), modifier = Modifier.height(38.dp)) {
                val n = to.trim().toIntOrNull()
                msg = if (n == null || n !in 1000..20000) {
                    "超时只接受 1000~20000 的整数（毫秒）"
                } else {
                    MiConf.save(MiConf.Cfg(c.clientId, c.redirect, c.region, c.lanFirst, n)).ifEmpty { "超时已改成 $n ms" }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.tx_fd4bef54), stringResource(R.string.tx_957dc19a))
        CardBox {
            Text(
                "登录态：" + if (MiAuth.logged()) stringResource(R.string.tx_3c10b78c) else stringResource(R.string.tx_2a2bf781),
                fontSize = 12.sp,
                color = if (MiAuth.logged()) OkGreen else DangerRed
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "接入身份：" + if (c.clientId.isEmpty()) stringResource(R.string.tx_cded648b) else stringResource(R.string.tx_59e9353c) + c.clientId.take(4) + "…）",
                fontSize = 12.sp,
                color = if (c.clientId.isEmpty()) DangerRed else OkGreen
            )
            Spacer(Modifier.height(10.dp))
            GhostButton(if (busy) stringResource(R.string.tx_5349b266) else stringResource(R.string.tx_a20ab061), modifier = Modifier.height(38.dp)) {
                if (busy) return@GhostButton
                busy = true
                scope.launch {
                    msg = withContext(Dispatchers.IO) { MiHome.sync() }
                    busy = false
                }
            }
            if (msg.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(msg, fontSize = 11.sp, color = TextSecondary)
            }
        }
    }
} //通道子页
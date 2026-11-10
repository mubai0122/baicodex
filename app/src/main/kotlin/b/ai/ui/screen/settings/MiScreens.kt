package b.ai.ui.screen.settings

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
        SectionTitle("能力自检", "两项都绿才谈得上连设备")
        CardBox {
            val cryptoOk = remember { MiCrypto.selfTest() }
            val clientId = remember { runCatching { MiConf.load().clientId }.getOrDefault("") }
            //密钥算法：AES-128-CBC 与 MD5 都走 C 层 OpenSSL，自检拿标准向量对答案
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("报文加解密", fontSize = 14.sp, color = TextPrimary)
                    Text(
                        if (cryptoOk) "AES-128-CBC / MD5 / SHA-1 自检通过" else "自检失败：算法结果与标准向量不符",
                        fontSize = 11.sp,
                        color = if (cryptoOk) OkGreen else DangerRed
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            //接入身份：只报有没有，不把 client_id 明文打在界面上
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("接入身份", fontSize = 14.sp, color = TextPrimary)
                    Text(
                        if (clientId.isBlank()) "未注入：local.properties 里填 miot.client.id 后重新安装"
                        else "已注入（" + clientId.take(4) + "…，共 " + clientId.length + " 位）",
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
        SectionTitle("登录米家账号", "授权码模式：浏览器里登录，把回调地址上的 code 粘回来")
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
                    Text(if (logged) "已登录" else "未登录", fontSize = 14.sp, color = TextPrimary)
                    Text(
                        if (cid.isEmpty()) "接入身份没注入：local.properties 填 miot.client.id 重装"
                        else "回调地址：" + redirect,
                        fontSize = 11.sp,
                        color = if (cid.isEmpty()) DangerRed else TextSecondary
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            //授权页交给系统浏览器：米家登录要过短信/滑块，WebView 里过不去
            GhostButton("打开授权页", modifier = Modifier.height(38.dp)) {
                val url = MiAuth.authUrl()
                if (url.isEmpty()) {
                    msg = "没注入 client_id，拼不出授权链接"
                } else {
                    val ok = runCatching {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }.isSuccess
                    msg = if (ok) "已在浏览器打开；登录后把地址栏里的 code 复制回来" else "打不开浏览器"
                }
            }
            Spacer(Modifier.height(8.dp))
            Field(
                label = "回调地址里的 code",
                value = code,
                onValueChange = { code = it },
                placeholder = "登录完成后地址形如 …?code=xxxx",
                secret = false
            )
            Spacer(Modifier.height(8.dp))
            GhostButton(if (busy) "换取中…" else "用 code 换 token", modifier = Modifier.height(38.dp)) {
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
                    msg = if (t != null) "登录成功，token 已落加密库" else "换取失败：看日志 bai.mi.auth（区域 / 回调地址要和平台登记一致）"
                    if (t != null) code = ""
                }
            }
            if (logged) {
                Spacer(Modifier.height(8.dp))
                GhostButton("退出登录", modifier = Modifier.height(38.dp)) {
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
        SectionTitle("设备清单", "云端拉一次，再扫局域网把 IP 补上；离线也能用 IP + token 直连")
        CardBox {
            if (rows.isEmpty()) {
                Text("台账还是空的：先登录同步，或手动加一台", fontSize = 12.sp, color = TextPrimary)
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
                                    " · " + if (r.online) "在线" else "离线",
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                        }
                        Text(
                            if (MiStore.token(r.did).isEmpty()) "缺 token" else "有 token",
                            fontSize = 10.sp,
                            color = if (MiStore.token(r.did).isEmpty()) DangerRed else OkGreen
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row {
                GhostButton(if (busy == "sync") "同步中…" else "同步云端", modifier = Modifier.height(38.dp)) {
                    if (busy.isNotEmpty()) return@GhostButton
                    busy = "sync"
                    scope.launch {
                        msg = withContext(Dispatchers.IO) { MiHome.sync() }
                        busy = ""
                        tick++
                    }
                }
                Spacer(Modifier.width(10.dp))
                GhostButton(if (busy == "scan") "扫描中…" else "扫局域网", modifier = Modifier.height(38.dp)) {
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
        SectionTitle("手动添加", "只走局域网：did + IP + 32 位十六进制 token")
        CardBox {
            Field(label = "did", value = mDid, onValueChange = { mDid = it }, placeholder = "设备数字 id")
            Spacer(Modifier.height(6.dp))
            Field(label = "IP", value = mIp, onValueChange = { mIp = it }, placeholder = "192.168.1.23")
            Spacer(Modifier.height(6.dp))
            Field(label = "token", value = mTok, onValueChange = { mTok = it }, placeholder = "32 位十六进制", secret = true)
            Spacer(Modifier.height(6.dp))
            Field(label = "名称（可空）", value = mName, onValueChange = { mName = it }, placeholder = "客厅灯")
            Spacer(Modifier.height(8.dp))
            GhostButton("存进台账", modifier = Modifier.height(38.dp)) {
                val err = MiHome.addManual(mDid.trim(), mIp.trim(), mTok.trim(), mName.trim())
                msg = if (err.isEmpty()) "已添加，点上面扫局域网可以确认在线" else err
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
            SectionTitle("属性读写", "设备 " + sel)
            CardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Field(label = "siid", value = siid, onValueChange = { siid = it }, placeholder = "2")
                    Spacer(Modifier.width(10.dp))
                    Field(label = "piid", value = piid, onValueChange = { piid = it }, placeholder = "1")
                }
                Spacer(Modifier.height(8.dp))
                GhostButton("读一次", modifier = Modifier.height(38.dp)) {
                    val s = siid.trim().toIntOrNull()
                    val p = piid.trim().toIntOrNull()
                    if (s == null || p == null) {
                        readText = "siid / piid 必须是数字"
                        return@GhostButton
                    }
                    scope.launch {
                        val v = withContext(Dispatchers.IO) { MiHome.read(sel, s, p) }
                        readText = if (v == null) "没读到（设备离线 / token 不对 / 属性号错）" else "当前值：$v"
                    }
                }
                if (readText.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(readText, fontSize = 11.sp, color = TextSecondary)
                }
                Spacer(Modifier.height(10.dp))
                Field(label = "要写入的值", value = valueText, onValueChange = { valueText = it }, placeholder = "true / 30 / \"auto\"")
                Spacer(Modifier.height(8.dp))
                GhostButton("写一次", modifier = Modifier.height(38.dp)) {
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
                        readText = if (err == null) "写入成功" else err
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
        SectionTitle("通道设置", "改了立刻生效，不用重启")
        CardBox {
            SelectField(
                "区域",
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
                "局域网优先",
                if (c.lanFirst) "先走局域网（500ms 级），不通再回落云" else "直接走云端",
                c.lanFirst
            ) {
                msg = MiConf.save(MiConf.Cfg(c.clientId, c.redirect, c.region, !c.lanFirst, c.timeoutMs))
                tick++
            }
            Spacer(Modifier.height(8.dp))
            Field(
                label = "超时（毫秒）",
                value = to,
                onValueChange = { to = it },
                placeholder = "4000",
                secret = false
            )
            Spacer(Modifier.height(8.dp))
            //超时不跟着每个按键写盘：填完点保存，顺便卡一下合理区间
            GhostButton("保存超时", modifier = Modifier.height(38.dp)) {
                val n = to.trim().toIntOrNull()
                msg = if (n == null || n !in 1000..20000) {
                    "超时只接受 1000~20000 的整数（毫秒）"
                } else {
                    MiConf.save(MiConf.Cfg(c.clientId, c.redirect, c.region, c.lanFirst, n)).ifEmpty { "超时已改成 $n ms" }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        SectionTitle("诊断", "哪一步断了在这里看结果，不用翻日志")
        CardBox {
            Text(
                "登录态：" + if (MiAuth.logged()) "有 token" else "无 token",
                fontSize = 12.sp,
                color = if (MiAuth.logged()) OkGreen else DangerRed
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "接入身份：" + if (c.clientId.isEmpty()) "未注入" else "已注入（" + c.clientId.take(4) + "…）",
                fontSize = 12.sp,
                color = if (c.clientId.isEmpty()) DangerRed else OkGreen
            )
            Spacer(Modifier.height(10.dp))
            GhostButton(if (busy) "跑着…" else "跑一次云端清单", modifier = Modifier.height(38.dp)) {
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
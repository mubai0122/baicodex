package b.ai.ui.screen.settings

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.core.media.music.MusicCfg
import b.ai.net.music.NeteaseLogin
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

/** 音乐专区页：平台开关与优先级、音质档位、缓存、登录态；播放器没接，这里先把配置落到实处 */
fun LazyListScope.musicSec() {
    item { MusicBody() }
} //音乐专区页

/** 音乐页正文：状态全在这里，LazyListScope 不是组合上下文放不了 remember */
@Composable
private fun MusicBody() {
    var tick by remember { mutableStateOf(0) }
    var msg by remember { mutableStateOf("") }
    var drafts by remember { mutableStateOf(mapOf<String, String>()) }
    val scope = rememberCoroutineScope()
    val c = remember(tick) { MusicCfg.load() }
    val size = remember(tick) { MusicCfg.cacheSize() }
    Column(Modifier.fillMaxWidth()) {
        SectionTitle("平台优先级", "同时只让一个平台说话：开着且排在最前的那个才接活，上移就是换主平台")
        CardBox {
            c.order.forEachIndexed { i, id ->
                if (i > 0) Spacer(Modifier.height(2.dp))
                val name = MusicCfg.PLATS[id] ?: id
                val hasCookie = MusicCfg.cookie(id).isNotEmpty()
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("$i. $name", fontSize = 14.sp, color = TextPrimary)
                        Text(
                            (if (c.on[id] == true) "已启用" else "已停用") + " · " + if (hasCookie) "已登录" else "未登录",
                            fontSize = 10.sp,
                            color = if (hasCookie) OkGreen else TextSecondary
                        )
                    }
                    if (i > 0) {
                        GhostButton("上移", modifier = Modifier.height(32.dp).width(64.dp)) {
                            val err = MusicCfg.moveUp(id)
                            msg = if (err.isEmpty()) "$name 已上移" else err
                            tick++
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Switch(
                        checked = c.on[id] == true,
                        onCheckedChange = { on ->
                            val next = LinkedHashMap(c.on)
                            next[id] = on
                            val err = MusicCfg.save(MusicCfg.Cfg(c.order, next, c.quality))
                            msg = if (err.isEmpty()) "$name 已${if (on) "启用" else "停用"}" else err
                            tick++
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
                    )
                }
            }
            if (msg.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(msg, fontSize = 11.sp, color = if (msg.contains("已") || msg.contains("成功")) OkGreen else DangerRed)
            }
        }
        Spacer(Modifier.height(14.dp))
        SectionTitle("音质档位", "各家内部 code 不一样，这里只存统一档位，调用时由平台自己映射")
        CardBox {
            MusicCfg.QUALITIES.forEachIndexed { i, q ->
                if (i > 0) Spacer(Modifier.height(4.dp))
                ChoiceRow(q, null, c.quality == q) {
                    val err = MusicCfg.save(MusicCfg.Cfg(c.order, c.on, q))
                    msg = if (err.isEmpty()) "音质已切到 $q" else err
                    tick++
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        SectionTitle("缓存", "下载的音频与封面都落这个目录，换机器记得先清")
        CardBox {
            Text(MusicCfg.cacheDir().absolutePath, fontSize = 11.sp, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text("当前占用 " + MusicCfg.sizeText(size), fontSize = 11.sp, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            GhostButton("清空缓存", modifier = Modifier.height(38.dp)) {
                val freed = MusicCfg.clearCache()
                msg = "已清掉 " + MusicCfg.sizeText(freed)
                tick++
            }
        }
        Spacer(Modifier.height(14.dp))
        SectionTitle("登录态", "网易云支持手机验证码登录；其它平台把浏览器里登录后的 cookie 整行粘进来，都存加密库，不落明文")
        MusicCfg.PLATS.forEach { (id, name) ->
            //每个平台一块卡：cookie 只在保存时进加密库，草稿只活在当前页面
            run {
                Spacer(Modifier.height(10.dp))
                CardBox {
                    Text(name, fontSize = 14.sp, color = TextPrimary)
                    val has = MusicCfg.cookie(id).isNotEmpty()
                    Text(
                        if (has) "已存 cookie（" + MusicCfg.cookie(id).length + " 字符）" else "还没有 cookie",
                        fontSize = 10.sp,
                        color = if (has) OkGreen else TextSecondary
                    )
                    if (id == "netease") {
                        //网易云走真登录：验证码换 cookie，cookie 由 NeteaseLogin 直接写进加密库
                        Field(
                            label = "手机号",
                            value = drafts["${id}_phone"].orEmpty(),
                            onValueChange = { drafts = drafts + ("${id}_phone" to it) },
                            placeholder = "13xxxxxxxxx"
                        )
                        Field(
                            label = "短信验证码",
                            value = drafts["${id}_code"].orEmpty(),
                            onValueChange = { drafts = drafts + ("${id}_code" to it) },
                            placeholder = "6 位数字"
                        )
                        Spacer(Modifier.height(8.dp))
                        Row {
                            GhostButton("发验证码", modifier = Modifier.height(38.dp)) {
                                val ph = drafts["${id}_phone"].orEmpty()
                                msg = "正在发验证码…"
                                scope.launch {
                                    val r = withContext(Dispatchers.IO) { NeteaseLogin.sendCaptcha(ph) }
                                    msg = if (r.isEmpty()) "验证码已发出，等短信" else r
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            GhostButton("登录", modifier = Modifier.height(38.dp)) {
                                val ph = drafts["${id}_phone"].orEmpty()
                                val cd = drafts["${id}_code"].orEmpty()
                                msg = "正在登录…"
                                scope.launch {
                                    val r = withContext(Dispatchers.IO) { NeteaseLogin.loginByCaptcha(ph, cd) }
                                    if (r.startsWith("登录成功")) drafts = drafts + ("${id}_code" to "")
                                    msg = r
                                    tick++
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            GhostButton("查账号", modifier = Modifier.height(38.dp)) {
                                msg = "正在查账号…"
                                scope.launch {
                                    msg = withContext(Dispatchers.IO) { NeteaseLogin.me() }
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Field(
                        label = "$name cookie",
                        value = drafts[id].orEmpty(),
                        onValueChange = { drafts = drafts + (id to it) },
                        placeholder = "MUSIC_U=…; __csrf=…",
                        secret = true
                    )
                    Spacer(Modifier.height(8.dp))
                    Row {
                        GhostButton("保存", modifier = Modifier.height(38.dp)) {
                            val err = MusicCfg.setCookie(id, drafts[id].orEmpty())
                            msg = if (err.isEmpty()) "$name cookie 已存进加密库" else err
                            if (err.isEmpty()) drafts = drafts + (id to "")
                            tick++
                        }
                        Spacer(Modifier.width(10.dp))
                        GhostButton("清除", modifier = Modifier.height(38.dp)) {
                            MusicCfg.clearCookie(id)
                            msg = "$name cookie 已清除"
                            tick++
                        }
                    }
                }
            }
        }
    }
} //音乐页正文
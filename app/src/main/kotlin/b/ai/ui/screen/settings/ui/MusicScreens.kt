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

package b.ai.ui.screen.settings.ui
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
internal fun MusicBody() {
    var tick by remember { mutableStateOf(0) }
    var msg by remember { mutableStateOf("") }
    var drafts by remember { mutableStateOf(mapOf<String, String>()) }
    val scope = rememberCoroutineScope()
    val c = remember(tick) { MusicCfg.load() }
    val size = remember(tick) { MusicCfg.cacheSize() }
    Column(Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.tx_f98a8254), stringResource(R.string.tx_f7ba9c52))
        CardBox {
            c.order.forEachIndexed { i, id ->
                if (i > 0) Spacer(Modifier.height(2.dp))
                val name = MusicCfg.PLATS[id] ?: id
                val hasCookie = MusicCfg.cookie(id).isNotEmpty()
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("$i. $name", fontSize = 14.sp, color = TextPrimary)
                        Text(
                            (if (c.on[id] == true) stringResource(R.string.tx_53ace430) else stringResource(R.string.tx_69b0f684)) + " · " + if (hasCookie) stringResource(R.string.tx_0b5961c7) else stringResource(R.string.tx_63e85df1),
                            fontSize = 10.sp,
                            color = if (hasCookie) OkGreen else TextSecondary
                        )
                    }
                    if (i > 0) {
                        GhostButton(stringResource(R.string.tx_315eacd1), modifier = Modifier.height(32.dp).width(64.dp)) {
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
                Text(msg, fontSize = 11.sp, color = if (msg.contains("已") || msg.contains(stringResource(R.string.tx_330363df))) OkGreen else DangerRed)
            }
        }
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.tx_5d92b9b7), stringResource(R.string.tx_db28b874))
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
        SectionTitle(stringResource(R.string.tx_e80c310e), stringResource(R.string.tx_56417f31))
        CardBox {
            Text(MusicCfg.cacheDir().absolutePath, fontSize = 11.sp, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.tx_bc64a057) + MusicCfg.sizeText(size), fontSize = 11.sp, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            GhostButton(stringResource(R.string.tx_79e1bb3f), modifier = Modifier.height(38.dp)) {
                val freed = MusicCfg.clearCache()
                msg = "已清掉 " + MusicCfg.sizeText(freed)
                tick++
            }
        }
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.tx_12eb255e), stringResource(R.string.tx_f4c9c9c5))
        MusicCfg.PLATS.forEach { (id, name) ->
            //每个平台一块卡：cookie 只在保存时进加密库，草稿只活在当前页面
            run {
                Spacer(Modifier.height(10.dp))
                CardBox {
                    Text(name, fontSize = 14.sp, color = TextPrimary)
                    val has = MusicCfg.cookie(id).isNotEmpty()
                    Text(
                        if (has) stringResource(R.string.tx_b26eb8c0) + MusicCfg.cookie(id).length + " 字符）" else stringResource(R.string.tx_c46e3a43),
                        fontSize = 10.sp,
                        color = if (has) OkGreen else TextSecondary
                    )
                    if (id == "netease") {
                        //网易云走真登录：验证码换 cookie，cookie 由 NeteaseLogin 直接写进加密库
                        Field(
                            label = stringResource(R.string.tx_8098e2b4),
                            value = drafts["${id}_phone"].orEmpty(),
                            onValueChange = { drafts = drafts + ("${id}_phone" to it) },
                            placeholder = "13xxxxxxxxx"
                        )
                        Field(
                            label = stringResource(R.string.tx_eb76c062),
                            value = drafts["${id}_code"].orEmpty(),
                            onValueChange = { drafts = drafts + ("${id}_code" to it) },
                            placeholder = stringResource(R.string.tx_a87595f9)
                        )
                        Spacer(Modifier.height(8.dp))
                        Row {
                            GhostButton(stringResource(R.string.tx_d5bd674d), modifier = Modifier.height(38.dp)) {
                                val ph = drafts["${id}_phone"].orEmpty()
                                msg = "正在发验证码…"
                                scope.launch {
                                    val r = withContext(Dispatchers.IO) { NeteaseLogin.sendCaptcha(ph) }
                                    msg = if (r.isEmpty()) b.ai.App.self.getString(R.string.tx_a1122f98) else r
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            GhostButton(stringResource(R.string.tx_402d19e5), modifier = Modifier.height(38.dp)) {
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
                            GhostButton(stringResource(R.string.tx_c46cea79), modifier = Modifier.height(38.dp)) {
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
                        GhostButton(stringResource(R.string.tx_be5fbbe3), modifier = Modifier.height(38.dp)) {
                            val err = MusicCfg.setCookie(id, drafts[id].orEmpty())
                            msg = if (err.isEmpty()) "$name cookie 已存进加密库" else err
                            if (err.isEmpty()) drafts = drafts + (id to "")
                            tick++
                        }
                        Spacer(Modifier.width(10.dp))
                        GhostButton(stringResource(R.string.tx_4403fca0), modifier = Modifier.height(38.dp)) {
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
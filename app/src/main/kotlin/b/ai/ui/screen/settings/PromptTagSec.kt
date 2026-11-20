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
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.core.cfg.app.PromptTags
import b.ai.ui.dialog.HostInOverlay
import b.ai.ui.dialog.ScrimCard
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import b.ai.ui.widget.CardBox
import b.ai.ui.widget.Field
import b.ai.ui.widget.GhostButton
import b.ai.ui.widget.SectionTitle
import b.ai.ui.widget.tap

/** 提示词标签子页：列表、启用开关、新建 / 编辑 / 删除 */
fun LazyListScope.promptTagSec() {
    item { PromptTagBody() }
}

@Composable
private fun PromptTagBody() {
    var list by remember { mutableStateOf(PromptTags.list()) }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PromptTags.Tag?>(null) }
    var name by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var arming by remember { mutableStateOf<String?>(null) }
    val open = creating || editing != null

    fun close() {
        creating = false
        editing = null
    } //关弹窗

    HostInOverlay(open) {
        if (open) {
            ScrimCard(onDismiss = { close() }) {
                Text(if (creating) stringResource(R.string.tx_4a067930) else stringResource(R.string.tx_3a81b1ed), fontSize = 16.sp, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                Field(stringResource(R.string.tx_689fad20), name, { name = it }, placeholder = stringResource(R.string.tx_58823c0a))
                Field(
                    stringResource(R.string.tx_58378f0d),
                    body,
                    { body = it },
                    placeholder = stringResource(R.string.tx_60dbc35c),
                    singleLine = false,
                    minLines = 5
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        stringResource(R.string.tx_625fb26b),
                        fontSize = 13.sp,
                        color = TextSecondary,
                        modifier = Modifier
                            .tap { close() }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                    Text(
                        stringResource(R.string.tx_be5fbbe3),
                        fontSize = 13.sp,
                        color = AccentBlue,
                        modifier = Modifier
                            .tap {
                                if (body.isBlank() && name.isBlank()) {
                                    close()
                                    return@tap
                                }
                                val cur = editing
                                if (cur == null) PromptTags.add(name, body)
                                else PromptTags.update(cur.copy(name = name, body = body))
                                list = PromptTags.list()
                                close()
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }

    Column {
        SectionTitle(stringResource(R.string.tx_ce813e67), stringResource(R.string.tx_dae9a428))
        val on = list.count { it.on && it.body.isNotBlank() }
        CardBox {
            Text(
                //崩溃根因：这条文案有两个占位符（%1$s 共几条 / %2$s 启用几条），
                //之前只传了一个参数，String.format 抛 MissingFormatArgumentException 直接把页面崩掉
                stringResource(R.string.tx_70bc66d5, list.size, on),
                fontSize = 11.sp,
                color = TextSecondary
            )
            if (on > 0) Text(stringResource(R.string.tx_0b55c2a3), fontSize = 10.sp, color = TextSecondary)
        }
        if (list.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            CardBox {
                Text(stringResource(R.string.tx_76a9ffa3), fontSize = 11.sp, color = TextSecondary)
            }
        }
        list.forEach { t ->
            Spacer(Modifier.height(8.dp))
            CardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.name, fontSize = 13.sp, color = TextPrimary)
                        Text(
                            t.body.replace("\n", " ").take(60).ifBlank { "（正文为空，启用也不会生效）" },
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = t.on,
                        onCheckedChange = {
                            PromptTags.setOn(t.id, it)
                            list = PromptTags.list()
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        stringResource(R.string.tx_95b351c8),
                        fontSize = 11.sp,
                        color = AccentBlue,
                        modifier = Modifier
                            .tap {
                                editing = t
                                creating = false
                                name = t.name
                                body = t.body
                                arming = null
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (arming == t.id) stringResource(R.string.tx_631cd220) else stringResource(R.string.tx_2f4aaddd),
                        fontSize = 11.sp,
                        color = if (arming == t.id) AccentBlue else TextSecondary,
                        modifier = Modifier
                            .tap {
                                if (arming == t.id) {
                                    PromptTags.remove(t.id)
                                    list = PromptTags.list()
                                    arming = null
                                } else {
                                    arming = t.id
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        CardBox {
            GhostButton(stringResource(R.string.tx_4a067930)) {
                creating = true
                editing = null
                name = ""
                body = ""
                arming = null
            }
        }
    }
} //提示词标签子页
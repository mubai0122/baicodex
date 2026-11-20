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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.core.cfg.emoji.Emojis
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

/** 自定义表情子页：按分类陈列、单条换图/删除、新建、重置回内置 */
fun LazyListScope.emojiSec() {
    item { EmojiBody() }
}

@Composable
private fun EmojiBody() {
    var tick by remember { mutableIntStateOf(0) }
    var target by remember { mutableStateOf<Emojis.Item?>(null) }
    var creating by remember { mutableStateOf(false) }
    var wiping by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("") }
    var ch by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    val groups = remember(tick) { Emojis.groups() }

    //换图：先记住要改哪一条，选完图再落盘
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val n = target?.name ?: return@rememberLauncherForActivityResult
        val bytes = runCatching {
            ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
        if (bytes == null || bytes.isEmpty()) return@rememberLauncherForActivityResult
        val path = Emojis.importImage(bytes, n)
        if (path.isNotBlank()) {
            Emojis.setImage(n, path)
            tick++
        }
        target = null
    }

    HostInOverlay(creating || target != null || wiping) {
        if (creating) {
            ScrimCard(onDismiss = { creating = false }) {
                Text(stringResource(R.string.tx_e0423a8e), fontSize = 16.sp, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                Field(stringResource(R.string.tx_7190c3e1), name, { name = it }, placeholder = stringResource(R.string.tx_f79de08c))
                Field(stringResource(R.string.tx_d0771a42), group, { group = it }, placeholder = stringResource(R.string.tx_825ff4d7))
                Field(stringResource(R.string.tx_aeadd7b2), ch, { ch = it }, placeholder = "🐟")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        stringResource(R.string.tx_625fb26b),
                        fontSize = 13.sp,
                        color = TextSecondary,
                        modifier = Modifier.tap { creating = false }.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                    Text(
                        stringResource(R.string.tx_be5fbbe3),
                        fontSize = 13.sp,
                        color = AccentBlue,
                        modifier = Modifier
                            .tap {
                                if (name.isNotBlank()) {
                                    Emojis.upsert(group, name, ch, "")
                                    tick++
                                }
                                creating = false
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
        val t = target
        if (t != null) {
            ScrimCard(onDismiss = { target = null }) {
                Text(t.name, fontSize = 16.sp, color = TextPrimary)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EmojiChipArt(t, 40)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (t.file.isNotBlank()) stringResource(R.string.tx_04470007) else stringResource(R.string.tx_25b347e9) + t.emoji,
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        stringResource(R.string.tx_5b58f2bd),
                        fontSize = 13.sp,
                        color = AccentBlue,
                        modifier = Modifier.tap { picker.launch("image/*") }.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.tx_2f4aaddd),
                        fontSize = 13.sp,
                        color = TextSecondary,
                        modifier = Modifier
                            .tap {
                                Emojis.remove(t.name)
                                target = null
                                tick++
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
        if (wiping) {
            ScrimCard(onDismiss = { wiping = false }) {
                Text(stringResource(R.string.tx_597f1e0f), fontSize = 16.sp, color = TextPrimary)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.tx_f6135cae), fontSize = 12.sp, color = TextSecondary)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        stringResource(R.string.tx_625fb26b),
                        fontSize = 13.sp,
                        color = TextSecondary,
                        modifier = Modifier.tap { wiping = false }.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                    Text(
                        stringResource(R.string.tx_80cfc33c),
                        fontSize = 13.sp,
                        color = AccentBlue,
                        modifier = Modifier
                            .tap {
                                Emojis.reset()
                                wiping = false
                                tick++
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }

    Column {
        SectionTitle(stringResource(R.string.tx_37f45cdf), stringResource(R.string.tx_65524e55))
        CardBox {
            Text(
                "图片放在 " + Emojis.dir().absolutePath + "，索引跟着配置一起存。删掉的那条模型再也调不出来，换图只影响显示。",
                fontSize = 10.sp,
                color = TextSecondary
            )
        }
        groups.forEach { (g, items) ->
            Spacer(Modifier.height(8.dp))
            CardBox {
                Text(g, fontSize = 12.sp, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items.forEach { it ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.tap { target = it }) { EmojiChipArt(it, 34) }
                            Text(it.name, fontSize = 9.sp, color = TextSecondary)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        CardBox {
            GhostButton(stringResource(R.string.tx_e0423a8e)) {
                creating = true
                name = ""
                group = ""
                ch = ""
            }
        }
        Spacer(Modifier.height(8.dp))
        CardBox {
            GhostButton(stringResource(R.string.tx_b63585d6)) { wiping = true }
        }
    }
} //自定义表情子页

/** 单个表情的显示：有自定义图就画图，否则画内置字符 */
@Composable
private fun EmojiChipArt(i: Emojis.Item, dp: Int) {
    val side = dp.dp
    val bmp = remember(i.file) {
        if (i.file.isBlank()) null
        else runCatching { android.graphics.BitmapFactory.decodeFile(i.file) }.getOrNull()
    }
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = i.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(side)
        )
        return
    }
    Box(Modifier.size(side), contentAlignment = Alignment.Center) {
        Text(i.emoji.ifBlank { "＋" }, fontSize = (dp * 0.62f).sp, color = TextPrimary)
    }
} //表情格子
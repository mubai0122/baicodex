/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:24
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * MirrorPicker.kt — Ubuntu 镜像源选择弹窗
 *
 * 外观走项目统一弹窗外壳 ScrimCard（玻璃卡片 + 背景模糊 + 入场动画），不再自己搭 AlertDialog。
 * 打开即拉清单并并行测速：第一项「自动」把选择权交给客户端（TTFB ≤ 200ms 择优 + 速度排序），
 * 其余每项显示 TTFB / 速度 / 达标标记，点一条写 Cfg.linuxMirrorNode（空串 = 自动）。
 * 只负责选择，不负责下载；下载与校验在 LinuxMirror.fetch 里做。
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.core.cfg.app.Cfg
import b.ai.device.shell.local.LinuxMirror
import b.ai.ui.dialog.HostInOverlay
import b.ai.ui.dialog.ScrimCard
import b.ai.ui.glass.liquidBg
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.OkGreen
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import b.ai.ui.widget.GhostButton
import b.ai.ui.widget.PrimaryButton
import b.ai.ui.widget.tap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 镜像源选择弹窗。
 * @param onDismiss 关闭回调
 */
@Composable
fun MirrorPickerDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var manifest by remember { mutableStateOf<LinuxMirror.Manifest?>(null) }
    var probes by remember { mutableStateOf<List<LinuxMirror.Probe>>(emptyList()) }
    var busy by remember { mutableStateOf(true) }
    var round by remember { mutableIntStateOf(0) }
    var picked by remember { mutableStateOf(Cfg.linuxMirrorNode) }

    //每轮（打开 / 点重测）都重新拉清单 + 并行测速；结果逐条回填，用户能看到测速在推进
    LaunchedEffect(round) {
        busy = true
        probes = emptyList()
        val acc = java.util.Collections.synchronizedList(ArrayList<LinuxMirror.Probe>())
        val m = withContext(Dispatchers.IO) {
            val mf = LinuxMirror.fetchManifest()
            val nodes = mf?.nodes ?: LinuxMirror.FALLBACK_NODES
            val res = LinuxMirror.probeAll(nodes) { p ->
                acc.add(p) //回调在测速线程里，切回主线程刷新列表
                scope.launch(Dispatchers.Main) { probes = acc.toList() }
            }
            mf to res
        }
        manifest = m.first
        probes = m.second
        busy = false
    } //拉清单 + 测速

    val nodes = manifest?.nodes ?: LinuxMirror.FALLBACK_NODES
    val best = LinuxMirror.pick(probes)
    val byId = probes.associateBy { it.node.id }

    //挂到根弹窗层：背景模糊与其它弹窗保持一致
    HostInOverlay(active = true) {
        ScrimCard(onDismiss = onDismiss) {
            Text(stringResource(R.string.tx_1f8dd094), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                if (busy) stringResource(R.string.tx_89e93f24, probes.size, nodes.size)
                else stringResource(R.string.tx_89bde44d, LinuxMirror.fastTtfbMs),
                fontSize = 11.sp,
                color = TextSecondary
            )
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 330.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                //自动档：不写死节点，交给客户端每次安装时重新择优
                MirrorRow(
                    title = stringResource(R.string.tx_6d8aba37),
                    subtitle = if (busy) stringResource(R.string.tx_7765b216) else "当前最优：${best?.node?.name ?: "无可用节点"}",
                    selected = picked.isBlank(),
                    accent = true
                ) {
                    Cfg.linuxMirrorNode = ""
                    picked = ""
                }
                nodes.forEach { node ->
                    val p = byId[node.id]
                    val speed = when {
                        p == null -> "等待测速…"
                        !p.ok -> stringResource(R.string.tx_c6d082e2, p.err)
                        else -> "TTFB ${p.ttfbMs}ms · ${p.speedText()}" + if (p.fast()) stringResource(R.string.tx_b2b2c8e5) else ""
                    }
                    MirrorRow(
                        title = node.name,
                        subtitle = speed,
                        selected = picked == node.id,
                        accent = p?.fast() == true
                    ) {
                        Cfg.linuxMirrorNode = node.id
                        picked = node.id
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                GhostButton(if (busy) stringResource(R.string.tx_7765b216) else stringResource(R.string.tx_ef121b59)) { if (!busy) round++ }
                Spacer(Modifier.width(10.dp))
                PrimaryButton("完成") { onDismiss() }
            }
        }
    }
} //镜像源选择

/** 一行可点的镜像项：选中态用主题色描边，达标项副标题用绿色 */
@Composable
private fun MirrorRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    accent: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .liquidBg(RoundedCornerShape(10.dp), tint = if (selected) AccentBlue.copy(alpha = 0.16f) else SurfaceMuted)
            .tap { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 12.sp, color = if (selected) AccentBlue else TextPrimary)
            Text(subtitle, fontSize = 10.sp, color = if (accent) OkGreen else TextSecondary)
        }
        if (selected) Text("●", fontSize = 10.sp, color = AccentBlue)
    }
} //镜像项
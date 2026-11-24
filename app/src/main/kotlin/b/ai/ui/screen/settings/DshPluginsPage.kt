/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Repository: https://github.com/mubai0122/Baicodex
 *
 * 本文件作用：dsh 插件广场页 —— 列出本机 dsh profile 里真实装载的插件行，
 * 按 id 开关（写 cordis.patch.yml），并提供「重启内核生效」。
 * 数据全部来自 [b.ai.core.dsh.DshPlugins]，本页不做任何推断。
 */
package b.ai.ui.screen.settings

import b.ai.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.core.dsh.DshPlugin
import b.ai.core.dsh.DshPlugins
import b.ai.core.dsh.DshSdk
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.DangerRed
import b.ai.ui.theme.OkGreen
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import b.ai.ui.widget.CardBox
import b.ai.ui.widget.GhostButton
import b.ai.ui.widget.PrimaryButton
import b.ai.ui.widget.SectionTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 分类名：只影响分组标题（走资源，随语言切换） */
@Composable
private fun kindName(kind: String): String = when (kind) {
    "tool" -> stringResource(R.string.dh_kind_tool)
    "skill" -> stringResource(R.string.dh_kind_skill)
    "web" -> stringResource(R.string.dh_kind_web)
    "mcp" -> "MCP"
    "cmd" -> stringResource(R.string.dh_kind_cmd)
    "office" -> stringResource(R.string.dh_kind_office)
    "agent" -> stringResource(R.string.dh_kind_agent)
    else -> stringResource(R.string.dh_kind_core)
} //分类名

/** 分类展示顺序 */
private val KIND_ORDER = listOf("tool", "skill", "web", "mcp", "cmd", "office", "agent", "core")

/**
 * dsh 插件广场：全部是本机真实装载行。
 * 默认只显示「能力类」（工具/技能/联网/MCP/命令/办公/Agent），内部服务收在开关后面。
 */
@Composable
fun DshPluginsPage() {
    DshInstalledContent()
} //设置页里的 dsh 插件子页

/**
 * 已装插件内容（设置页的 dsh 插件子页与抽屉里的插件页共用同一份实现）。
 * @param installToken 变化时重读清单（安装 / 卸载完成后父页 +1 触发）
 * @param onNeedRestart 有改动待重启时回调，父页可据此在顶部提示
 */
@Composable
fun DshInstalledContent(installToken: Int = 0, onNeedRestart: (Boolean) -> Unit = {}) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth()) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var items by remember { mutableStateOf<List<DshPlugin>>(emptyList()) }
    var err by remember { mutableStateOf("") }
    var showCore by remember { mutableStateOf(false) }   //是否展开内部服务
    var needRestart by remember { mutableStateOf(false) } //有改动待重启
    var busyId by remember { mutableStateOf("") }         //正在写盘的那一行

    //读清单：文件 IO，走 IO 线程
    suspend fun reload() {
        loading = true
        val r = withContext(Dispatchers.IO) { DshPlugins.list() }
        r.onSuccess { list ->
            items = list.sortedWith(compareBy({ KIND_ORDER.indexOf(it.kind).let { i -> if (i < 0) 9 else i } }, { it.id }))
            err = ""
        }.onFailure { err = it.message ?: ctx.getString(R.string.dh_read_list_failed) }
        loading = false
    } //重读

    LaunchedEffect(installToken) { reload() }

    //改动状态抛给父页（父页据此在顶部显示「重启内核生效」）
    LaunchedEffect(needRestart) { onNeedRestart(needRestart) }

    val shown = items.filter { showCore || it.capable }
    val enabledCount = shown.count { !it.disabled }

    //0. 概览：环境状态 + 刷新/重启动作
    SectionTitle(stringResource(R.string.dh_plugins_title), stringResource(R.string.dh_plugins_sub, DshPlugins.PROFILE))
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (loading) stringResource(R.string.dh_reading) else stringResource(R.string.dh_plugins_count, shown.size, enabledCount),
                    fontSize = 13.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    when {
                        err.isNotBlank() -> err
                        !DshPlugins.ready() -> stringResource(R.string.dh_plugins_not_init)
                        needRestart -> stringResource(R.string.dh_need_restart_short)
                        else -> stringResource(R.string.dh_core_state, if (DshSdk.isRunning()) stringResource(R.string.dh_core_running) else stringResource(R.string.dh_not_started))
                    },
                    fontSize = 10.sp,
                    color = if (err.isNotBlank()) DangerRed else if (needRestart) AccentBlue else TextSecondary
                )
            }
            GhostButton(stringResource(R.string.dh_refresh), modifier = Modifier.height(32.dp)) { scope.launch { reload() } }
        }
        if (needRestart) {
            Spacer(Modifier.height(8.dp))
            PrimaryButton(stringResource(R.string.dh_restart_core), modifier = Modifier.fillMaxWidth()) {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            DshSdk.stop()
                            DshSdk.start()
                        }
                    }
                    needRestart = false
                    reload()
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        CapSwitch(
            stringResource(R.string.dh_show_core),
            stringResource(R.string.dh_show_core_sub),
            showCore
        ) { showCore = it }
    }

    //1. 按分类分组列出：items 已按 KIND_ORDER 排好，groupBy 保留顺序
    shown.groupBy { it.kind }.forEach { (kind, group) ->
        Spacer(Modifier.height(12.dp))
        SectionTitle(kindName(kind), stringResource(R.string.dh_kind_count, group.count { !it.disabled }, group.size))
        group.forEach { p ->
            CardBox(Modifier.padding(bottom = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            p.short.ifBlank { p.id },
                            fontSize = 13.sp,
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            buildString {
                                append("id: ").append(p.id)
                                if (p.bundle != "user") append("  ·  ").append(p.bundle)
                                //条件禁用：真实取值由 dsh 运行时按平台/上下文求值，这里只提示
                                p.condition?.let { append("  ·  ").append(stringResource(R.string.dh_cond_prefix)).append(it.take(40)) }
                            },
                            fontSize = 9.sp,
                            color = TextSecondary
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    if (busyId == p.id) {
                        Text("…", fontSize = 13.sp, color = AccentBlue)
                    } else {
                        Switch(
                            checked = !p.disabled,
                            onCheckedChange = { on ->
                                busyId = p.id
                                scope.launch {
                                    //写盘在 IO 线程；回来后才动 Compose 状态（onSuccess 的 lambda 非 suspend，别在里面调 reload）
                                    val r = withContext(Dispatchers.IO) { DshPlugins.setEnabled(p.id, on) }
                                    if (r.isSuccess) {
                                        needRestart = true
                                        reload()
                                    } else {
                                        err = r.exceptionOrNull()?.message ?: ctx.getString(R.string.dh_write_failed)
                                    }
                                    busyId = ""
                                }
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
                        )
                    }
                }
                //条件行额外给一句人话，避免用户以为开关坏了
                if (p.condition != null) {
                    Text(
                        stringResource(R.string.dh_cap_hint),
                        fontSize = 9.sp,
                        color = OkGreen,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    } //分组列表

    if (!loading && shown.isEmpty() && err.isBlank()) {
        Spacer(Modifier.height(12.dp))
        CardBox {
            Text("没有可显示的插件行。", fontSize = 12.sp, color = TextSecondary)
        }
    }
    Spacer(Modifier.height(16.dp))
    }
} //已装插件内容
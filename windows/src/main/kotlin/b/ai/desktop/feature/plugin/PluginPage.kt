/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27 01:28:54
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * 插件页：已装 / 广场 / 创建。
 * 已装：真实扫描 <appDir>/plugins，开关直接进 config.enabledPlugins；
 * 广场：读用户自己填的索引地址，或直接粘贴 zip / git 链接安装；
 * 创建：生成骨架 plugin.json（prompt + 一条回显命令），装完即可改。
 * 装卸都走二次确认，重名覆盖会明确说是覆盖。
 */

package b.ai.desktop.feature.plugin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.desktop.app.AppState
import b.ai.desktop.core.plugin.PluginInfo
import b.ai.desktop.core.plugin.PluginMarket
import b.ai.desktop.core.plugin.PluginStore
import b.ai.desktop.core.plugin.RemotePlugin
import b.ai.desktop.core.util.openPath
import b.ai.desktop.feature.settings.PillButton
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import b.ai.desktop.feature.settings.SectionTitle
import b.ai.desktop.feature.settings.TextSetting
import b.ai.desktop.feature.settings.pickDirectory
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PluginCenter(state: AppState) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    var tab by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf<String?>(null) }
    var tip by remember { mutableStateOf<String?>(null) }
    var tipOk by remember { mutableStateOf(true) }

    fun report(result: String) {
        tipOk = result.startsWith("已") || result.startsWith("安装") && !result.contains("失败")
        tip = result
    }

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("已装", "广场", "创建").forEachIndexed { index, label ->
            val active = tab == index
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) colors.primary.copy(alpha = 0.12f) else colors.surfaceVariant)
                    .clickable { tab = index }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    label,
                    fontSize = 11.sp,
                    color = if (active) colors.primary else colors.onSurfaceVariant,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        if (busy != null) {
            Text(busy!!, fontSize = 10.sp, color = colors.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterVertically))
        }
    }

    Spacer(Modifier.size(10.dp))

    when (tab) {
        0 -> InstalledTab(state, scope, onReport = ::report, onBusy = { busy = it })
        1 -> MarketTab(state, scope, onReport = ::report, onBusy = { busy = it })
        else -> CreateTab(state, onReport = ::report)
    }

    if (tip != null) {
        Text(
            tip!!,
            fontSize = 10.sp,
            color = if (tipOk) colors.primary else colors.error,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

// ---------------------------------------------------------------- 已装

@Composable
private fun InstalledTab(
    state: AppState,
    scope: kotlinx.coroutines.CoroutineScope,
    onReport: (String) -> Unit,
    onBusy: (String?) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var pendingUninstall by remember { mutableStateOf<String?>(null) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            PluginStore.root().absolutePath,
            fontSize = 10.sp,
            color = colors.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        PillButton("打开目录") { openPath(PluginStore.root().absolutePath) }
        Spacer(Modifier.width(6.dp))
        PillButton("刷新") { state.reloadPlugins() }
    }

    Spacer(Modifier.size(8.dp))

    if (state.plugins.isEmpty()) {
        Text(
            "还没有插件。可以到「广场」按链接安装，或在「创建」里生成一个骨架再改。",
            fontSize = 11.sp,
            color = colors.onSurfaceVariant,
        )
    }

    state.plugins.forEach { info ->
        val enabled = info.id in state.config.enabledPlugins && info.usable
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(info.title, fontSize = 12.sp, color = colors.onSurface, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(6.dp))
                    Text("v${info.version}", fontSize = 9.sp, color = colors.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    if (info.manifest.prompt.isNotBlank()) Badge("提示词")
                    if (info.manifest.tools.isNotEmpty()) Badge("${info.manifest.tools.size} 个工具")
                }
                Text(
                    info.manifest.description.ifBlank { info.id },
                    fontSize = 10.sp,
                    color = colors.onSurfaceVariant,
                )
                if (info.broken != null) {
                    Text(info.broken!!, fontSize = 10.sp, color = colors.error)
                }
            }

            if (pendingUninstall == info.id) {
                PillButton("确认卸载", enabled = true) {
                    onBusy("正在卸载…")
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { state.uninstallPlugin(info.id) }
                        onBusy(null)
                        pendingUninstall = null
                        onReport(result)
                    }
                }
                Spacer(Modifier.width(6.dp))
                PillButton("取消") { pendingUninstall = null }
            } else {
                PillButton("打开") { state.openPluginDir(info.id) }
                Spacer(Modifier.width(6.dp))
                PillButton("卸载") { pendingUninstall = info.id }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = enabled,
                    onCheckedChange = { state.togglePlugin(info.id, it) },
                    enabled = info.usable,
                    colors = SwitchDefaults.colors(checkedTrackColor = colors.primary),
                )
            }
        }
        HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.6f))
    }

    Spacer(Modifier.size(10.dp))
    SectionTitle("从本地目录安装", "目录里要有 plugin.json；同名插件会被覆盖")
    var folder by remember { mutableStateOf("") }
    TextSetting(label = "插件目录", value = folder, onChange = { folder = it }, placeholder = "选择或粘贴目录路径")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
        PillButton("选择目录…") {
            pickDirectory(File(folder.ifBlank { PluginStore.root().absolutePath }), "选择插件目录")?.let { folder = it }
        }
        PillButton("安装", primary = true, enabled = folder.isNotBlank()) {
            onBusy("正在安装…")
            scope.launch {
                val result = state.installPluginFromDir(File(folder.trim()))
                onBusy(null)
                onReport(result)
            }
        }
    }
}

@Composable
private fun Badge(text: String) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(colors.primary.copy(alpha = 0.10f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(text, fontSize = 8.sp, color = colors.primary)
    }
}

// ---------------------------------------------------------------- 广场

@Composable
private fun MarketTab(
    state: AppState,
    scope: kotlinx.coroutines.CoroutineScope,
    onReport: (String) -> Unit,
    onBusy: (String?) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var indexUrl by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<RemotePlugin>>(emptyList()) }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<RemotePlugin?>(null) }
    var directUrl by remember { mutableStateOf("") }
    var pendingDirect by remember { mutableStateOf(false) }

    SectionTitle("索引地址", "桌面端不内置索引；填一个 JSON 地址就能列出别人的插件")
    TextSetting(
        label = "索引 URL",
        value = indexUrl,
        onChange = { indexUrl = it },
        placeholder = "https://…/plugins.json",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
        PillButton("拉取", primary = true, enabled = !loading && indexUrl.isNotBlank()) {
            loading = true
            error = ""
            scope.launch {
                withContext(Dispatchers.IO) { PluginMarket.fetch(indexUrl) }.fold(
                    onSuccess = { results = it },
                    onFailure = {
                        results = emptyList()
                        error = it.message ?: "拉取失败"
                    },
                )
                loading = false
            }
        }
        if (loading) Text("正在拉取…", fontSize = 10.sp, color = colors.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterVertically))
        if (error.isNotBlank()) Text(error, fontSize = 10.sp, color = colors.error, modifier = Modifier.align(Alignment.CenterVertically))
    }

    results.forEach { item ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.name, fontSize = 12.sp, color = colors.onSurface, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(6.dp))
                    Text("v${item.version}", fontSize = 9.sp, color = colors.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Badge(if (item.isGit) "git" else "zip")
                    if (state.plugins.any { it.id == item.id }) {
                        Spacer(Modifier.width(4.dp))
                        Badge("已装")
                    }
                }
                Text(item.description.ifBlank { item.source }, fontSize = 10.sp, color = colors.onSurfaceVariant)
            }
            if (pending?.id == item.id) {
                PillButton("确认安装（覆盖同名）") {
                    pending = null
                    onBusy("正在安装 ${item.name}…")
                    scope.launch {
                        val result = if (item.isGit) state.installPluginFromGit(item.source)
                        else state.installPluginFromZip(item.source)
                        onBusy(null)
                        onReport(result)
                    }
                }
            } else {
                PillButton("安装") { pending = item }
            }
        }
        HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.6f))
    }

    Spacer(Modifier.size(12.dp))
    SectionTitle("直接粘贴链接安装", "以 .git 结尾或 git@ 开头的按仓库克隆，其余当 zip 下载")
    TextSetting(label = "zip 或 git 地址", value = directUrl, onChange = { directUrl = it }, placeholder = "https://…")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
        if (pendingDirect) {
            PillButton("确认安装", primary = true) {
                val url = directUrl.trim()
                pendingDirect = false
                if (url.isBlank()) return@PillButton
                onBusy("正在安装…")
                scope.launch {
                    val result = if (PluginMarket.looksLikeGit(url)) state.installPluginFromGit(url)
                    else state.installPluginFromZip(url)
                    onBusy(null)
                    onReport(result)
                }
            }
            PillButton("取消") { pendingDirect = false }
        } else {
            PillButton("安装", primary = true, enabled = directUrl.isNotBlank()) { pendingDirect = true }
        }
    }

    Spacer(Modifier.size(10.dp))
    Text(
        "索引格式：{ \"plugins\": [ { \"id\": \"demo\", \"name\": \"示例\", \"description\": \"…\", " +
            "\"version\": \"1.0.0\", \"zip\": \"https://…/demo.zip\" } ] }——zip 换成 git 字段则按仓库克隆。",
        fontSize = 9.sp,
        lineHeight = 15.sp,
        color = colors.onSurfaceVariant,
    )
}

// ---------------------------------------------------------------- 创建

@Composable
private fun CreateTab(state: AppState, onReport: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var id by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }

    SectionTitle("新建骨架", "生成 plugin.json + README，默认带一条回显命令，用来验证工具链路")
    TextSetting(label = "id", value = id, onChange = { id = it }, placeholder = "例如 code-review")
    TextSetting(label = "名称", value = name, onChange = { name = it }, placeholder = "例如 代码审查规范")
    TextSetting(label = "一句话说明", value = desc, onChange = { desc = it })
    TextSetting(
        label = "追加给模型的提示词",
        value = prompt,
        onChange = { prompt = it },
        singleLine = false,
        placeholder = "启用后这段会拼在系统提示词后面",
    )
    Row(Modifier.padding(top = 8.dp)) {
        PillButton("创建", primary = true, enabled = id.isNotBlank()) {
            onReport(state.createPlugin(id, name, desc, prompt))
            if (state.plugins.any { it.id == id.trim() }) {
                id = ""
                name = ""
                desc = ""
                prompt = ""
            }
        }
    }
    Spacer(Modifier.size(10.dp))
    Text(
        "创建后到「已装」里打开目录，把 plugin.json 的 prompt 换成你自己的规范；" +
            "tools 里每条命令都能被模型调用，参数通过环境变量 PLUGIN_INPUT 传入。",
        fontSize = 9.sp,
        lineHeight = 15.sp,
        color = colors.onSurfaceVariant,
    )
}

/** 插件信息里没被用到的字段，留在这里方便以后扩展列表（避免误删字段） */
@Suppress("unused")
private fun PluginInfo.debugLine(): String =
    "id=${manifest.id} tools=${manifest.tools.size} dir=${dir.name}"

/** 聊天区的插件页：整窗覆盖 + 顶部返回 */
@Composable
fun PluginPage(state: AppState, onClose: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("插件", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                Spacer(Modifier.width(8.dp))
                Text(
                    "已装 ${state.plugins.size} · 启用 ${state.activePlugins.size}",
                    fontSize = 11.sp,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                PillButton("返回") { onClose() }
            }
            HorizontalDivider(color = colors.outlineVariant)
            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Column(
                    Modifier.widthIn(max = 820.dp).padding(horizontal = 20.dp, vertical = 14.dp),
                ) {
                    PluginCenter(state)
                    Spacer(Modifier.width(1.dp).padding(bottom = 24.dp))
                }
            }
        }
    }
}

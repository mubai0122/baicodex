/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27 01:42:59
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baicodex Windows - 设置 · 关于组
 * 版本与更新 / 快捷键 / GitHub 账号（账号页复用 AccountSettings）。
 */

package b.ai.desktop.feature.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import b.ai.desktop.core.config.ConfigStore
import b.ai.desktop.core.config.ModelProfile
import b.ai.desktop.core.util.openPath
import b.ai.desktop.core.util.openUrl
import b.ai.desktop.feature.settings.PillButton
import b.ai.desktop.feature.settings.SectionTitle
import b.ai.desktop.feature.settings.SettingRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val APP_VERSION = "0.1.0"
/** 纯数字版本码：与服务端 get_pc.php 的 latestCode 比较，发新版时 +1 */
const val APP_UPDATE_CODE = 1
private const val REPO = "https://github.com/mubai0122/Baicodex"
private const val SITE = "https://agent.ai.baiccb.cn/"
/** 自建更新接口（桌面版）：与手机端 get.php / History.php 同协议，内容在服务端文件里维护 */
private const val UPDATE_API = "https://agent.ai.baiccb.cn/api/update/get_pc.php"
private const val UPDATE_HISTORY_API = "https://agent.ai.baiccb.cn/api/update/History_pc.php"

/** 版本与更新：版本号、数据位置、检查更新、重置 */
@Composable
fun AboutVersionPage(state: AppState) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var ok by remember { mutableStateOf(true) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    SettingRow("版本", "Baicodex Windows $APP_VERSION · MPI 本地优先") {
        PillButton("检查更新", enabled = !checking) {
            checking = true
            message = null
            scope.launch {
                val result = withContext(Dispatchers.IO) { fetchUpdate() }
                checking = false
                result.fold(
                    onSuccess = { info ->
                        if (!info.hasUpdate) {
                            ok = true
                            updateInfo = null
                            message = "已是最新（v${info.version}，${info.date}）"
                        } else {
                            ok = true
                            updateInfo = info
                            message = "发现新版本 v${info.version}（${info.date}）" +
                                if (info.forceRequired) " · 需要强制更新" else ""
                        }
                    },
                    onFailure = {
                        ok = false
                        updateInfo = null
                        message = "检查失败：${it.message?.take(80) ?: "网络不可用"}"
                    },
                )
            }
        }
    }
    if (checking) {
        Text("正在查询更新…", fontSize = 10.sp, color = colors.onSurfaceVariant)
    }
    if (message != null) {
        Text(message!!, fontSize = 10.sp, color = if (ok) colors.primary else colors.error, modifier = Modifier.padding(top = 4.dp))
    }
    // 更新日志（检查到新版本时展示；服务端 get_pc.php 的 content 就是更新日志）
    updateInfo?.let { info ->
        Column(
            Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(9.dp))
                .background(colors.surfaceVariant.copy(alpha = 0.5f))
                .padding(10.dp),
        ) {
            Text("v${info.version} · ${info.subtitle.ifBlank { "更新日志" }}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
            info.content.trim().takeIf { it.isNotBlank() }?.let { body ->
                Text(
                    body,
                    fontSize = 9.sp,
                    lineHeight = 15.sp,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("前往下载", primary = true) {
                    val url = info.downloadJumpUrl.ifBlank { REPO + "/releases/latest" }
                    state.flash(if (openUrl(url)) "已打开下载页" else "打不开：$url")
                }
            }
        }
    }

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)

    // 更新历史：与手机端 History.php 同协议（Update_pc.json），点了才拉
    SectionTitle("更新历史", "全部版本记录，存服务端 Update_pc.json")
    var historyLoading by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf<List<UpdateLogEntry>?>(null) }
    var historyError by remember { mutableStateOf("") }
    if (history == null && !historyLoading && historyError.isBlank()) {
        PillButton("查看历史") {
            historyLoading = true
            historyError = ""
            scope.launch {
                val result = withContext(Dispatchers.IO) { fetchUpdateHistory() }
                historyLoading = false
                result.fold(onSuccess = { history = it }, onFailure = { historyError = it.message ?: "拉取失败" })
            }
        }
    }
    if (historyLoading) Text("正在读取历史…", fontSize = 10.sp, color = colors.onSurfaceVariant)
    if (historyError.isNotBlank()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("历史读取失败：${historyError.take(60)}", fontSize = 10.sp, color = colors.error)
            PillButton("重试") {
                historyError = ""
                historyLoading = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) { fetchUpdateHistory() }
                    historyLoading = false
                    result.fold(onSuccess = { history = it }, onFailure = { historyError = it.message ?: "拉取失败" })
                }
            }
        }
    }
    history?.forEach { entry ->
        Column(Modifier.padding(top = 8.dp)) {
            Text(
                "${entry.title} · ${entry.date}" + if (entry.subtitle.isNotBlank()) " · ${entry.subtitle}" else "",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface,
            )
            if (entry.content.isNotBlank()) {
                Text(
                    entry.content,
                    fontSize = 9.sp,
                    lineHeight = 15.sp,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }

    SectionTitle("引导", "首次启动会走一遍；想再看一次可以在这里重播")
    SettingRow("欢迎引导", "工作区、沙箱、工具确认三件事过一遍") {
        PillButton("重看引导") { state.replayOnboarding() }
    }

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)

    SectionTitle("位置")
    SettingRow("配置", "config.json") {
        PillButton("打开") { openPath(ConfigStore.appDir().absolutePath) }
    }
    SettingRow("会话", "sessions.json") {
        PillButton("打开") { openPath(ConfigStore.appDir().absolutePath) }
    }
    SettingRow("工作区", state.config.workspaceDir().absolutePath) {
        PillButton("打开") { openPath(state.config.workspaceDir().absolutePath) }
    }

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)

    SectionTitle("链接")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton("GitHub 仓库") { openUrl(REPO) }
        PillButton("官网") { openUrl(SITE) }
    }
    Text("AGPL-3.0 · 由 沐白/Baix 维护", fontSize = 9.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)

    SectionTitle("重置", "模型连接、密钥、GitHub 登录、已装插件都不动")
    if (confirmReset) {
        Column(
            Modifier
                .clip(RoundedCornerShape(9.dp))
                .background(colors.error.copy(alpha = 0.08f))
                .padding(12.dp),
        ) {
            Text("确认把所有设置恢复默认？", fontSize = 11.sp, color = colors.error)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("确认恢复", primary = true) {
                    state.resetSettings()
                    confirmReset = false
                }
                PillButton("取消") { confirmReset = false }
            }
        }
    } else {
        PillButton("恢复默认设置") { confirmReset = true }
    }
}

/** 快捷键与操作：桌面端所有入口都列出来，不用猜 */
@Composable
fun AboutKeysPage(state: AppState) {
    val colors = MaterialTheme.colorScheme

    SectionTitle("输入", "发送键可在「对话 → 会话」里换")
    KeyRow("Enter", if (state.config.sendOnEnter) "发送" else "换行")
    KeyRow("Shift+Enter", "换行（发送键为 Enter 时）")
    KeyRow("Ctrl+Enter", if (state.config.sendOnEnter) "换行" else "发送")
    KeyRow("Esc", "关闭弹出的面板 / 退出全屏输入 / 清空搜索")

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)
    SectionTitle("鼠标")
    KeyRow("右键会话", "重命名 / 置顶 / 复制 / 导出 / 删除")
    KeyRow("右键消息", "复制 / 编辑重发 / 重新生成 / 收藏 / 朗读 / 删除")
    KeyRow("拖入文件", "直接作为附件，可随时移除")
    KeyRow("点击预算环", "展开上下文用量卡")

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)
    SectionTitle("页面")
    KeyRow("侧边栏搜索", "按标题与正文匹配，正文命中会显示片段")
    KeyRow("侧边栏「插件」", "已装 / 广场 / 创建，装完即启用")
    KeyRow("聊天顶栏「工作区」", "切换目录，文件工具只能在这里读写")
    KeyRow("模型胶囊", "输入区上方切换连接与思考档位")

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)
    Text(
        "提示：所有工具操作都限制在工作区内；命令执行有超时，超时会强制结束进程。",
        fontSize = 9.sp,
        lineHeight = 15.sp,
        color = colors.onSurfaceVariant,
    )
}

@Composable
private fun KeyRow(key: String, action: String) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            key,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = colors.onSurface,
            modifier = Modifier
                .width(140.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(colors.surfaceVariant.copy(alpha = 0.6f))
                .padding(horizontal = 6.dp, vertical = 3.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(action, fontSize = 10.sp, color = colors.onSurfaceVariant)
    }
}

/** get_pc.php 返回的最新版本信息（已算好 hasUpdate / forceUpdateRequired） */
data class UpdateInfo(
    val version: String,
    val code: Int,
    val date: String,
    val title: String,
    val subtitle: String,
    val content: String,
    val downloadJumpUrl: String,
    val downloadDirectUrl: String,
    val hasUpdate: Boolean,
    val forceRequired: Boolean,
)

/** History_pc.php 的一条历史记录 */
data class UpdateLogEntry(
    val code: Int,
    val version: String,
    val date: String,
    val title: String,
    val subtitle: String,
    val content: String,
)

private val updateJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

/** GET 一个 URL，返回正文；非 2xx 抛错 */
private fun httpGetOrThrow(url: String): String {
    val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 10_000
        requestMethod = "GET"
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", "Baicodex-Windows/$APP_VERSION")
    }
    try {
        if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
        return conn.inputStream.bufferedReader().use { it.readText() }
    } finally {
        conn.disconnect()
    }
}

/** 检查更新：自建 get_pc.php?code=，协议与手机端一致 */
private fun fetchUpdate(): Result<UpdateInfo> = runCatching {
    val body = httpGetOrThrow("$UPDATE_API?code=$APP_UPDATE_CODE")
    val root = updateJson.parseToJsonElement(body).kotlinxJsonObject
    if (root["code"]?.kotlinxJsonPrimitive?.content != "200") {
        throw IllegalStateException(root["msg"]?.kotlinxJsonPrimitive?.content?.take(60) ?: "接口返回异常")
    }
    val data = root["data"]?.kotlinxJsonObject ?: throw IllegalStateException("响应缺 data")
    UpdateInfo(
        version = data.str("latestVersion"),
        code = data.int("latestCode"),
        date = data.str("updateDate"),
        title = data.str("title"),
        subtitle = data.str("subtitle"),
        content = data.str("content"),
        downloadJumpUrl = data.str("downloadJumpUrl"),
        downloadDirectUrl = data.str("downloadDirectUrl"),
        hasUpdate = data["hasUpdate"]?.kotlinxJsonPrimitive?.content == "true",
        forceRequired = data["forceUpdateRequired"]?.kotlinxJsonPrimitive?.content == "true",
    )
}

/** 更新历史：自建 History_pc.php，读 Update_pc.json */
private fun fetchUpdateHistory(): Result<List<UpdateLogEntry>> = runCatching {
    val body = httpGetOrThrow(UPDATE_HISTORY_API)
    val root = updateJson.parseToJsonElement(body).kotlinxJsonObject
    if (root["code"]?.kotlinxJsonPrimitive?.content != "200") {
        throw IllegalStateException(root["msg"]?.kotlinxJsonPrimitive?.content?.take(60) ?: "接口返回异常")
    }
    val data = root["data"]?.kotlinxJsonObject ?: throw IllegalStateException("响应缺 data")
    val arr = data["list"] as? kotlinx.serialization.json.JsonArray
        ?: kotlinx.serialization.json.JsonArray(emptyList())
    arr.mapNotNull { item ->
        val o = item as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
        UpdateLogEntry(
            code = o.int("code"),
            version = o.str("version"),
            date = o.str("date"),
            title = o.str("title"),
            subtitle = o.str("subtitle"),
            content = o.str("content"),
        )
    }
}

private fun kotlinx.serialization.json.JsonObject.str(key: String): String =
    this[key]?.kotlinxJsonPrimitive?.content.orEmpty()

private fun kotlinx.serialization.json.JsonObject.int(key: String): Int =
    this[key]?.kotlinxJsonPrimitive?.content?.toIntOrNull() ?: 0

private val kotlinx.serialization.json.JsonElement.kotlinxJsonPrimitive
    get() = this as? kotlinx.serialization.json.JsonPrimitive

private val kotlinx.serialization.json.JsonElement.kotlinxJsonObject
    get() = this as? kotlinx.serialization.json.JsonObject
        ?: throw IllegalStateException("响应不是 JSON 对象")

/** 供「模型」页测试连接提示里引用，避免魔法字符串散落 */
internal fun profileHint(profile: ModelProfile?): String =
    profile?.let { "${it.label} · ${it.model}" } ?: "未配置"


/** 反馈与赞赏：问题反馈入口 + 支持作者 */
@Composable
fun AboutRewardPage(state: AppState) {
    val colors = MaterialTheme.colorScheme

    SectionTitle("反馈", "问题、想法、想接的能力，都从这里递")
    val rows = listOf(
        "GitHub Issues" to "https://github.com/mubai0122/Baicodex/issues",
        "官网与公告" to "https://agent.ai.baiccb.cn/",
    )
    rows.forEach { (label, url) ->
        SettingRow(label, url) {
            PillButton("打开") {
                val ok = b.ai.desktop.core.util.openUrl(url)
                state.flash(if (ok) "已打开 $url" else "打不开：$url")
            }
        }
    }

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)
    SectionTitle("赞赏", "项目持续维护靠兴趣也靠电费；觉得好用可以请作者喝杯咖啡")
    SettingRow("赞赏作者", "跳转到赞赏页，金额随意") {
        PillButton("前往", primary = true) {
            val ok = b.ai.desktop.core.util.openUrl("https://agent.ai.baiccb.cn/reward")
            state.flash(if (ok) "已打开赞赏页" else "打不开赞赏页，可以去官网找入口")
        }
    }
    Text(
        "不开赞赏页面也一样能用全部功能——AGPL-3.0，代码永远是你的。",
        fontSize = 9.sp,
        color = colors.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

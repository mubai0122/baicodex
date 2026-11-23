/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-24
 * Repository: https://github.com/mubai0122/baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * DeepSeek 历史导入的入口卡与弹窗：贴链接自动下载 → 解析 → 入库并绑定当前工作区。
 * 也支持直接选本地已下好的导出包。
 */

package b.ai.ui.screen.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.agent.run.Agent
import b.ai.core.cfg.app.Cfg
import b.ai.core.data.io.DeepSeekImport
import b.ai.device.fs.Workspace
import b.ai.ui.dialog.ScrimCard
import b.ai.ui.glass.liquidBg
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.DangerRed
import b.ai.ui.theme.DangerRedSoft
import b.ai.ui.theme.OkGreen
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import b.ai.ui.widget.CardBox
import b.ai.ui.widget.Field
import b.ai.ui.widget.GhostButton
import b.ai.ui.widget.IcClose
import b.ai.ui.widget.tap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 对话分类里的一行入口：从 DeepSeek 导入历史 */
fun LazyListScope.dsImportEntry(onOpen: () -> Unit) {
    item {
        Spacer(Modifier.height(14.dp))
        CardBox {
            Row(
                Modifier.fillMaxWidth().tap { onOpen() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.List, null, tint = AccentBlue, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("从 DeepSeek 导入历史", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                    Text("粘贴导出链接，自动下载解析并绑定工作区", fontSize = 11.sp, color = TextSecondary)
                }
                Text("›", fontSize = 18.sp, color = TextSecondary)
            }
        }
    }
} //DeepSeek 导入入口

/** 导入弹窗：教程三步 + 链接输入 + 本地文件兜底 */
@Composable
fun DeepSeekImportDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var step by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    //关掉「C 端」后缀时的提示：2 秒后自动收起
    var suffixTip by remember { mutableStateOf(false) }
    LaunchedEffect(suffixTip) {
        if (suffixTip) {
            delay(2000)
            suffixTip = false
        }
    } //提示倒计时

    /** 导入一个已落地的 zip（下载完或用户自己选的） */
    fun startZip(f: File) {
        busy = true
        result = ""
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                DeepSeekImport.importZip(f, Workspace.path()) { s ->
                    //回调在 IO 线程，写状态要切回主线程
                    scope.launch { step = s }
                }
            }
            busy = false
            step = ""
            result = if (r.err != null) {
                "导入失败：" + r.err
            } else {
                "已导入 ${r.sessions} 个会话 · ${r.msgs} 条消息 · ${r.files} 个附件"
            }
            if (r.err == null) Agent.refreshSessions()
        }
    } //导入本地 zip

    /** 下载后导入；链接带签名，过期就只能重新从 DeepSeek 导出 */
    fun startUrl() {
        if (url.isBlank()) {
            result = "先把导出链接粘贴进来"
            return
        }
        busy = true
        result = ""
        scope.launch {
            val f = File(ctx.cacheDir, "ds_export.zip")
            val err = withContext(Dispatchers.IO) {
                DeepSeekImport.download(url, f) { got, total ->
                    val pct = if (total > 0) " ${got * 100 / total}%" else ""
                    val mb = String.format("%.1f", got / 1048576.0)
                    scope.launch { step = "正在下载 $mb MB$pct" }
                }
            }
            if (err != null) {
                busy = false
                step = ""
                result = "下载失败：$err"
                return@launch
            }
            step = "下载完成，正在解析…"
            startZip(f)
        }
    } //下载并导入

    val pickZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val f = File(ctx.cacheDir, "ds_export_local.zip")
        val ok = runCatching {
            ctx.contentResolver.openInputStream(uri)?.use { ins -> f.outputStream().use { ins.copyTo(it) } } != null
        }.getOrDefault(false)
        if (ok) startZip(f) else result = "读不到这个文件"
    }

    ScrimCard(onDismiss = { if (!busy) onClose() }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            //标题行：右边挂矢量关闭图标，关掉弹窗不必滚到底部找文字
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.List, null, tint = AccentBlue, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "导入 DeepSeek 历史",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    Modifier
                        .size(26.dp)
                        .liquidBg(CircleShape, tint = SurfaceMuted)
                        .tap { if (!busy) onClose() },
                    contentAlignment = Alignment.Center
                ) { IcClose(TextSecondary, side = 11.dp) }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "① 打开 DeepSeek，左上角唤出侧边栏 → 个人信息 → 数据管理\n" +
                    "② 点「导出所有历史对话」，导出完成后复制下载链接\n" +
                    "③ 回到这里粘贴链接，点开始导入\n\n" +
                    "链接是带签名的临时地址（约 7 天过期），过期了重新导出一次即可。\n" +
                    "导入的会话会存成新会话，标题是否带后缀由下方「C 端」开关决定；导入后自动绑定当前工作区，图片附件放在工作区的 deepseek_import 目录下。",
                fontSize = 11.sp,
                color = TextSecondary,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(6.dp))
            Field(
                label = "",
                value = url,
                onValueChange = { url = it },
                placeholder = "粘贴导出链接 https://…zip?…",
                singleLine = false,
                minLines = 2
            )
            Spacer(Modifier.height(8.dp))
            //C 端后缀开关：默认开；关掉时提示来源会混在一起，2 秒后自动收起
            CapSwitch(
                "C 端",
                if (Cfg.importSuffix) "导入的会话标题自动带「（DeepSeek）」，和原有会话区分开"
                else "已关闭：标题按原样导入，容易和原有会话混在一起",
                Cfg.importSuffix
            ) { v ->
                Cfg.importSuffix = v
                if (!v) suffixTip = true
            }
            if (suffixTip) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "已关闭 C 端后缀：导入的会话不再带来源标记，容易和原有会话混在一起，找起来会乱。",
                    fontSize = 10.sp,
                    color = DangerRed,
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidBg(RoundedCornerShape(9.dp), tint = DangerRedSoft)
                        .padding(8.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            if (step.isNotBlank()) {
                Text(step, fontSize = 11.sp, color = AccentBlue, modifier = Modifier.padding(bottom = 8.dp))
            }
            if (result.isNotBlank()) {
                Text(
                    result,
                    fontSize = 11.sp,
                    color = if (result.startsWith("已导入")) OkGreen else TextSecondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Row {
                GhostButton(if (busy) "处理中…" else "开始导入", modifier = Modifier.height(38.dp)) {
                    if (!busy) startUrl()
                }
                Spacer(Modifier.width(8.dp))
                GhostButton("选本地 zip", modifier = Modifier.height(38.dp)) {
                    if (!busy) runCatching { pickZip.launch(arrayOf("*" + "/" + "*")) }
                }
            }
        }
    }
} //导入弹窗
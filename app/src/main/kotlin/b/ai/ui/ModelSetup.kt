package b.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.core.Cfg
import b.ai.core.ModelProfile
import b.ai.core.Provider
import b.ai.core.Providers
import b.ai.core.Profiles
import b.ai.net.LlmClient
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.AccentBlueSoft
import b.ai.ui.theme.DangerRed
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.SurfaceWhite
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 厂商 + 连通配置面板：引导页与设置页共用 */
@Composable
fun ModelSetupPanel() {
    val scope = rememberCoroutineScope()
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf("") } //空串 = 不弹，provider / model 各对应一个

    //弹窗挂在根弹窗层，卡片本身不会被录进模糊背景
    HostInOverlay(dialog.isNotEmpty()) {
        when (dialog) {
            "provider" -> ProviderDialog(
                current = Cfg.providerId,
                onPick = { p ->
                    //换厂商就清掉旧模型名，免得卡片显示「通义千问 · deepseek-chat」这种混搭
                    if (Cfg.providerId != p.id) Cfg.model = ""
                    Cfg.providerId = p.id
                    Cfg.baseUrl = p.baseUrl
                    Profiles.capture()
                    dialog = ""
                },
                onClose = { dialog = "" }
            )
            "model" -> ModelDialog(
                models = models,
                current = Cfg.model,
                onPick = { m ->
                    Cfg.model = m
                    Profiles.capture()
                    dialog = ""
                },
                onClose = { dialog = "" }
            )
        }
    }

    Column {
        //配置名：留空就跟随「厂商 · 模型」，右侧方按钮一键回到自动名
        val autoNow = Profiles.autoLabel(Cfg.providerId, Cfg.model)
        FieldBox(
            "配置名",
            if (Cfg.profileCustom) Cfg.profileName else "",
            {
                Cfg.profileName = it
                Cfg.profileCustom = it.isNotBlank()
                Profiles.capture()
            },
            placeholder = autoNow,
            trailing = {
                SquareIconBox(Icons.Filled.Refresh, desc = "用自动名") {
                    Cfg.profileName = ""
                    Cfg.profileCustom = false
                    Profiles.capture()
                }
            }
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "当前显示：「$autoNow」",
            fontSize = 10.sp,
            color = TextSecondary,
            maxLines = 1
        )
        Spacer(Modifier.height(8.dp))
        SectionTitle("厂商", "点开列表选，选完自动填 base_url，仍可手改")
        Row(
            Modifier
                .fillMaxWidth()
                .background(SurfaceMuted, RoundedCornerShape(12.dp))
                .tap { dialog = "provider" }
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(Providers.byId(Cfg.providerId).name, fontSize = 14.sp, color = TextPrimary)
                Text(
                    Providers.byId(Cfg.providerId).baseUrl.ifBlank { "需手填 base_url" },
                    fontSize = 10.sp,
                    color = TextSecondary,
                    maxLines = 1
                )
            }
            Text("选择 ›", fontSize = 11.sp, color = AccentBlue)
        }
        Field("base_url", Cfg.baseUrl, { Cfg.baseUrl = it; Profiles.capture() }, placeholder = "https://api.deepseek.com/v1")
        //可填多个 Key：一行一个，请求失败自动换下一个
        Field(
            "API key",
            Cfg.apiKey,
            { Cfg.apiKey = it; Profiles.capture() },
            secret = true,
            singleLine = false,
            minLines = 2,
            placeholder = "sk-…（多 Key 一行一个）"
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .background(SurfaceMuted, RoundedCornerShape(12.dp))
                .tap { if (!loading) dialog = "model" }
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    Cfg.model.ifBlank { "未选模型" },
                    fontSize = 14.sp,
                    color = TextPrimary,
                    maxLines = 1
                )
                Text(
                    if (models.isEmpty()) "先拉取模型，或直接在下面手填" else "已拉取 ${models.size} 个，点开选",
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
            Text(if (loading) "获取中…" else "选择 ›", fontSize = 11.sp, color = AccentBlue)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            GhostButton(if (loading) "获取中…" else "获取模型") {
                if (loading) return@GhostButton
                loading = true
                err = null
                scope.launch {
                    val res = runCatching {
                        withContext(Dispatchers.IO) {
                            LlmClient(Cfg.baseUrl, Cfg.apiKey, Providers.byId(Cfg.providerId).auth).listModels()
                        }
                    }
                    loading = false
                    res.onSuccess {
                        models = it
                        if (it.isEmpty()) err = "接口没有返回模型，手动填写模型名即可" else dialog = "model"
                    }.onFailure { err = it.message }
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "走 GET {base_url}/models；拿到后弹窗里选，也可手动填",
                fontSize = 10.sp,
                color = TextSecondary
            )
        }
        if (err != null) {
            Spacer(Modifier.height(6.dp))
            Text(err!!, fontSize = 11.sp, color = DangerRed)
        }
        Field("模型名", Cfg.model, { Cfg.model = it; Profiles.capture() }, placeholder = "手动填写，如 deepseek-chat")
        Spacer(Modifier.height(10.dp))
        Text("单轮最大 token", fontSize = 11.sp, color = TextSecondary)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0, 2048, 4096, 8192, 16384).forEach { v ->
                PolicyChip(if (v == 0) "默认" else v.toString(), Cfg.maxTokens == v) {
                    Cfg.maxTokens = v
                    Profiles.capture()
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "0/默认表示不下发 max_tokens，跟随服务端；设太小会把长回答截断。",
            fontSize = 10.sp,
            color = TextSecondary
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            GhostButton("保存到当前配置") { Profiles.capture() }
            Spacer(Modifier.width(8.dp))
            Text(
                "写回「${Profiles.selected?.label ?: "新配置"}」",
                fontSize = 10.sp,
                color = TextSecondary,
                maxLines = 1
            )
        }
    }
} //配置面板

/** 模型配置列表：一条 = 一套提供商 + Key + 模型，点一下整组切换 */
@Composable
fun ProfileList() {
    CardBox {
        Profiles.list.forEach { p ->
            ProfileRow(p)
            Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            GhostButton("新增配置") { Profiles.capture().let { Profiles.duplicateCurrent() } }
            Spacer(Modifier.width(8.dp))
            Text("复制当前配置再改，适合同厂商加第二个模型", fontSize = 10.sp, color = TextSecondary)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "改动实时写回当前配置；点下面按钮可以再手动存一次。",
            fontSize = 10.sp,
            color = TextSecondary
        )
    }
} //模型配置列表

@Composable
private fun ProfileRow(p: ModelProfile) {
    val sel = p.id == Cfg.profileId
    //选中项直接读 Cfg：编辑区改一个字符卡片就跟着变，不等落盘重组
    val title = if (sel) {
        if (Cfg.profileCustom && Cfg.profileName.isNotBlank()) Cfg.profileName
        else Profiles.autoLabel(Cfg.providerId, Cfg.model)
    } else p.label
    val sub = if (sel) {
        Cfg.model.ifBlank { "未选模型" } + " · " + Cfg.baseUrl.ifBlank { "无 base_url" }
    } else {
        p.model.ifBlank { "未选模型" } + " · " + p.baseUrl.ifBlank { "无 base_url" }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (sel) AccentBlueSoft else SurfaceMuted, RoundedCornerShape(12.dp))
            .tap {
                //切走之前先把当前表单值写回旧配置，避免改了一半的输入丢掉
                Profiles.capture()
                Profiles.select(p.id)
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 13.sp,
                color = if (sel) AccentBlue else TextPrimary,
                maxLines = 1
            )
            Text(
                sub,
                fontSize = 10.sp,
                color = TextSecondary,
                maxLines = 1
            )
        }
        if (sel) Text("✓", fontSize = 13.sp, color = AccentBlue)
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.size(24.dp).background(if (sel) SurfaceWhite else Color.Transparent, CircleShape)
                .tap { Profiles.remove(p.id) },
            contentAlignment = Alignment.Center
        ) { IcClose(TextSecondary, side = 11.dp) }
    }
} //一条模型配置

@Composable
private fun DialogHead(title: String, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.size(28.dp).background(SurfaceMuted, CircleShape).tap { onClose() },
            contentAlignment = Alignment.Center
        ) { IcClose(TextSecondary, side = 12.dp) }
    }
} //弹窗标题行

@Composable
private fun ProviderDialog(current: String, onPick: (Provider) -> Unit, onClose: () -> Unit) {
    ScrimCard {
        DialogHead("选择厂商", onClose)
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Providers.builtin.forEach { p ->
                val sel = p.id == current
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            if (sel) AccentBlueSoft else Color.Transparent,
                            RoundedCornerShape(10.dp)
                        )
                        .tap { onPick(p) }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontSize = 13.sp, color = if (sel) AccentBlue else TextPrimary)
                        Text(
                            p.baseUrl.ifBlank { "需手填 base_url" },
                            fontSize = 10.sp,
                            color = TextSecondary,
                            maxLines = 1
                        )
                    }
                    if (sel) Text("✓", fontSize = 13.sp, color = AccentBlue)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("选完会自动把 base_url 填进表单，可以再改", fontSize = 10.sp, color = TextSecondary)
    }
} //厂商弹窗

@Composable
private fun ModelDialog(
    models: List<String>,
    current: String,
    onPick: (String) -> Unit,
    onClose: () -> Unit
) {
    ScrimCard {
        DialogHead("选择模型", onClose)
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
        ) {
            models.forEach { m ->
                val sel = m == current
                Text(
                    m,
                    fontSize = 12.sp,
                    color = if (sel) AccentBlue else TextPrimary,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (sel) AccentBlueSoft else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .tap { onPick(m) }
                        .padding(horizontal = 10.dp, vertical = 9.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("共 ${models.size} 个，来自 {base_url}/models", fontSize = 10.sp, color = TextSecondary)
    }
} //模型弹窗
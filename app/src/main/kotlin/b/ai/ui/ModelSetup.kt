package b.ai.ui

import android.widget.Toast
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.core.Cfg
import b.ai.core.ModelProfile
import b.ai.core.Provider
import b.ai.core.Providers
import b.ai.core.Profiles
import b.ai.net.LlmClient
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.BorderGray
import b.ai.ui.theme.AccentBlueSoft
import b.ai.ui.theme.DangerRed
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.SurfaceWhite
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import b.ai.R

/** 厂商 + 连通配置面板：引导页与设置页共用。表单全是临时态，点「保存到当前配置」才写回配置与磁盘 */
@Composable
fun ModelSetupPanel() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf("") } //空串 = 不弹，provider / models / http 各对应一个
    //模型多选卡片的临时勾选与搜索词：点确定只进表单，表单再点保存才落盘
    var draft by remember { mutableStateOf(emptySet<String>()) }
    var q by remember { mutableStateOf("") }
    var httpUrl by remember { mutableStateOf("") } //待确认的 http 地址
    var httpLeft by remember { mutableStateOf(0) } //5 秒倒计时剩余
    var urlTick by remember { mutableStateOf(0) } //输入防抖的触发计数
    //表单草稿：这里改的全是临时值，只有点「保存到当前配置」才写回配置
    var fId by remember { mutableStateOf("") }
    var fName by remember { mutableStateOf("") }
    var fCustom by remember { mutableStateOf(false) }
    var fProv by remember { mutableStateOf("custom") }
    var fBase by remember { mutableStateOf("") }
    var fKey by remember { mutableStateOf("") }
    var fModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var fModel by remember { mutableStateOf("") }
    var fTok by remember { mutableStateOf(0) }
    var snap by remember { mutableStateOf("") } //上次载入或保存后的指纹，用来判断有没有改动
    var savedTo by remember { mutableStateOf("") } //保存成功的提示，一改动就失效
    var formErr by remember { mutableStateOf<String?>(null) }

    //表单指纹：任何一项变了就算脏，保存提示随之失效
    fun finger(): String = listOf(
        fId, fName, fCustom.toString(), fProv, fBase, fKey,
        fModels.joinToString(","), fModel, fTok.toString()
    ).joinToString("|")

    //把一条配置装进表单；没保存的改动直接丢掉
    fun loadForm(p: ModelProfile?) {
        fId = p?.id ?: ""
        fName = p?.name ?: ""
        fCustom = p?.custom ?: false
        fProv = p?.providerId ?: "custom"
        fBase = p?.baseUrl ?: ""
        fKey = p?.apiKey ?: ""
        fModels = p?.models ?: emptyList()
        fModel = p?.model ?: ""
        fTok = p?.maxTokens ?: 0
        formErr = null
        savedTo = ""
        snap = finger()
    } //载入表单

    //清成一张空白表单：不落库，点保存才真正新增一条配置
    fun newForm() {
        fId = ""
        fName = ""
        fCustom = false
        fProv = "custom"
        fBase = ""
        fKey = ""
        fModels = emptyList()
        fModel = ""
        fTok = 0
        formErr = null
        savedTo = ""
        snap = finger()
    } //空白表单

    //点保存才校验并写回：先落 models.json，再切到这条配置让 Cfg 生效
    fun save() {
        formErr = null
        val base = fBase.trim()
        val key = fKey.trim()
        if (base.isEmpty()) {
            formErr = ctx.getString(R.string.ms_need_base)
            return
        }
        val ms = (fModels + listOfNotNull(fModel.trim().takeIf { it.isNotEmpty() }))
            .distinct().filter { it.isNotBlank() }
        val one = fModel.trim().ifBlank { ms.firstOrNull() ?: "" }
        if (one.isEmpty()) {
            formErr = ctx.getString(R.string.ms_no_model)
            return
        }
        val id = fId.ifBlank { "p" + System.currentTimeMillis() }
        val custom = fCustom && fName.isNotBlank()
        val p = ModelProfile(
            id = id,
            name = if (custom) fName.trim() else Profiles.autoLabel(fProv, one),
            providerId = fProv,
            baseUrl = base,
            apiKey = key,
            model = one,
            models = ms,
            vision = Cfg.modelVision,
            toolCall = Cfg.modelToolCall,
            reasoning = Cfg.modelReasoning,
            maxTokens = fTok,
            custom = custom
        )
        Cfg.setProfileKey(id, key)
        Profiles.upsert(p)
        Profiles.select(id)
        loadForm(p)
        savedTo = ctx.getString(R.string.ms_saved_to, p.label)
        Toast.makeText(ctx, savedTo, Toast.LENGTH_SHORT).show()
    } //保存到当前配置

    //拉模型列表：只刷新候选，不动已勾选结果，用的是表单里的 base_url 与 Key
    fun pull() {
        if (loading) return
        loading = true
        err = null
        scope.launch {
            val res = runCatching {
                withContext(Dispatchers.IO) {
                    LlmClient(fBase.trim(), fKey.trim(), Providers.byId(fProv).auth).listModels()
                }
            }
            loading = false
            res.onSuccess { models = it; if (it.isEmpty()) err = ctx.getString(R.string.ms_no_models) }
                .onFailure { err = it.message }
        }
    } //拉列表

    //切配置就把表单整体换掉；刚保存的那条 id 已对上，不重载，保住成功提示
    LaunchedEffect(Cfg.profileId) {
        if (Cfg.profileId != fId) {
            loadForm(Profiles.list.firstOrNull { it.id == Cfg.profileId } ?: Profiles.selected)
        }
    }

    //输入停下来 700ms 再检查协议，免得逐字敲的时候反复弹窗
    LaunchedEffect(urlTick) {
        if (urlTick == 0) return@LaunchedEffect
        delay(700)
        val t = fBase.trim()
        if (t.startsWith("http://", true) && !Cfg.httpConfirmed && dialog.isEmpty()) {
            httpUrl = t
            httpLeft = 5
            dialog = "http"
        }
    }
    //倒计时读完才让点「仍然使用」，逼着看一眼风险
    LaunchedEffect(dialog) {
        if (dialog != "http") return@LaunchedEffect
        httpLeft = 5
        while (httpLeft > 0) {
            delay(1000)
            httpLeft -= 1
        }
    }

    //弹窗挂在根弹窗层，卡片本身不会被录进模糊背景
    HostInOverlay(dialog.isNotEmpty()) {
        when (dialog) {
            "provider" -> ProviderDialog(
                current = fProv,
                onPick = { p ->
                    //换厂商就清掉旧模型名与勾选，免得卡片显示「通义千问 · deepseek-chat」这种混搭
                    if (fProv != p.id) {
                        fModel = ""
                        fModels = emptyList()
                    }
                    fProv = p.id
                    fBase = p.baseUrl
                    formErr = null
                    dialog = ""
                },
                onClose = { dialog = "" }
            )
            "models" -> ModelPickMulti(
                models = models,
                loading = loading,
                err = err,
                q = q,
                onQ = { q = it },
                picked = draft,
                onToggle = { m -> draft = if (m in draft) draft - m else draft + m },
                onAll = { shown -> draft = draft + shown },
                onClear = { draft = emptySet() },
                onRetry = { pull() },
                onAddModel = { name ->
                    //手打进来的模型直接并进列表并勾上，保存时一起写进这条配置
                    if (name.isNotBlank()) {
                        if (name !in models) models = models + name
                        draft = draft + name
                    }
                },
                onOk = {
                    val list = draft.toList()
                    fModels = list
                    if (fModel.isBlank() || fModel !in list) fModel = list.firstOrNull() ?: fModel
                    formErr = null
                    dialog = ""
                },
                onClose = { dialog = "" }
            )
            "http" -> HttpWarnDialog(
                url = httpUrl,
                left = httpLeft,
                onCancel = { dialog = "" },
                onUseHttps = {
                    fBase = httpUrl.replaceFirst("http://", "https://").replaceFirst("HTTP://", "https://")
                    dialog = ""
                },
                onUseHttp = {
                    Cfg.httpConfirmed = true
                    dialog = ""
                }
            )
        }
    }

    Column {
        //一条配置只管一个供应商；这里只清成空白表单，点保存才真新增
        Row(
            Modifier
                .fillMaxWidth()
                .liquidBg(RoundedCornerShape(12.dp), tint = SurfaceMuted)
                .tap { newForm(); dialog = "provider" }
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ms_add_provider), fontSize = 13.sp, color = TextPrimary)
                Text(
                    stringResource(R.string.ms_add_provider_sub),
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
            Text("＋", fontSize = 13.sp, color = AccentBlue)
        }
        Spacer(Modifier.height(8.dp))
        //配置名：留空就跟随「厂商 · 模型」，右侧方按钮一键回到自动名
        val autoNow = Profiles.autoLabel(fProv, fModel)
        FieldBox(
            stringResource(R.string.ms_name),
            if (fCustom) fName else "",
            {
                fName = it
                fCustom = it.isNotBlank()
                formErr = null
            },
            placeholder = autoNow,
            trailing = {
                SquareIconBox(Icons.Filled.Refresh, desc = stringResource(R.string.ms_auto)) {
                    fName = ""
                    fCustom = false
                }
            }
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.ms_showing, autoNow),
            fontSize = 10.sp,
            color = TextSecondary,
            maxLines = 1
        )
        Spacer(Modifier.height(8.dp))
        SectionTitle(stringResource(R.string.ms_provider), stringResource(R.string.ms_provider_sub))
        Row(
            Modifier
                .fillMaxWidth()
                .liquidBg(RoundedCornerShape(12.dp), tint = SurfaceMuted)
                .tap { dialog = "provider" }
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(Providers.byId(fProv).name, fontSize = 14.sp, color = TextPrimary)
                Text(
                    Providers.byId(fProv).baseUrl.ifBlank { stringResource(R.string.ms_need_base) },
                    fontSize = 10.sp,
                    color = TextSecondary,
                    maxLines = 1
                )
            }
            Text(stringResource(R.string.ms_pick), fontSize = 11.sp, color = AccentBlue)
        }
        Field(
            "base_url",
            fBase,
            { fBase = it; formErr = null; urlTick++ },
            placeholder = "https://api.deepseek.com/v1"
        )
        //可填多个 Key：一行一个，请求失败自动换下一个
        Field(
            "API key",
            fKey,
            { fKey = it; formErr = null },
            secret = true,
            singleLine = false,
            minLines = 2,
            placeholder = stringResource(R.string.ms_keys_hint)
        )
        Spacer(Modifier.height(10.dp))
        //模型多选：勾一批进表单，保存后才写进这条配置
        SectionTitle(stringResource(R.string.ms_models_t), stringResource(R.string.ms_models_sub))
        Row(
            Modifier
                .fillMaxWidth()
                .liquidBg(RoundedCornerShape(12.dp), tint = SurfaceMuted)
                .tap {
                    if (loading) return@tap
                    draft = fModels.toSet()
                    q = ""
                    dialog = "models"
                    //还没拉过列表就先拉一次，拉完在卡片里勾
                    if (models.isEmpty()) pull()
                }
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (fModels.isEmpty()) stringResource(R.string.ms_models_none)
                    else stringResource(R.string.ms_models_picked, fModels.size),
                    fontSize = 14.sp,
                    color = TextPrimary,
                    maxLines = 1
                )
                Text(
                    fModel.ifBlank { stringResource(R.string.ms_no_model) },
                    fontSize = 10.sp,
                    color = TextSecondary,
                    maxLines = 1
                )
            }
            Text(
                if (loading) stringResource(R.string.ms_fetching) else stringResource(R.string.ms_models_pick),
                fontSize = 11.sp,
                color = AccentBlue
            )
        }
        //列表拉不到时的兜底：直接手打模型名，保存时自动并进已选列表
        Field(
            stringResource(R.string.ms_model_name),
            fModel,
            { fModel = it; formErr = null },
            placeholder = stringResource(R.string.ms_model_hint)
        )
        if (err != null) {
            Spacer(Modifier.height(6.dp))
            Text(err!!, fontSize = 11.sp, color = DangerRed)
        }
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.ms_max_tok), fontSize = 11.sp, color = TextSecondary)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0, 2048, 4096, 8192, 16384).forEach { v ->
                PolicyChip(if (v == 0) stringResource(R.string.ms_default) else v.toString(), fTok == v) {
                    fTok = v
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.ms_max_tok_tip),
            fontSize = 10.sp,
            color = TextSecondary
        )
        Spacer(Modifier.height(10.dp))
        //保存才落盘：脏的时候按钮实心，存完给一句写回哪条
        val dirty = finger() != snap
        Row(verticalAlignment = Alignment.CenterVertically) {
            PrimaryButton(stringResource(R.string.ms_save), enabled = dirty) { save() }
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    formErr != null -> formErr!!
                    dirty -> stringResource(R.string.ms_edit_tip)
                    savedTo.isNotEmpty() -> "✓ " + savedTo
                    else -> stringResource(
                        R.string.ms_saved_to,
                        Profiles.selected?.label ?: stringResource(R.string.ms_new_cfg)
                    )
                },
                fontSize = 10.sp,
                color = when {
                    formErr != null -> DangerRed
                    dirty -> TextSecondary
                    else -> AccentBlue
                },
                maxLines = 2
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
            GhostButton(stringResource(R.string.ms_add)) {
                //加的是空白供应商配置，填完在下面表单里点保存才生效
                Profiles.capture()
                Profiles.add()
            }
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.ms_add_provider_sub),
                fontSize = 10.sp,
                color = TextSecondary
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.ms_edit_tip),
            fontSize = 10.sp,
            color = TextSecondary
        )
    }
} //模型配置列表

@Composable
private fun ProfileRow(p: ModelProfile) {
    val sel = p.id == Cfg.profileId
    //卡片只反映已保存的内容：表单里改了一半的东西不会漏到这里
    val title = p.label
    val sub = p.model.ifBlank { stringResource(R.string.ms_no_model) } + " · " +
        p.baseUrl.ifBlank { stringResource(R.string.ms_no_base) }
    Row(
        Modifier
            .fillMaxWidth()
            .liquidBg(RoundedCornerShape(12.dp), tint = if (sel) AccentBlueSoft else SurfaceMuted)
            .tap {
                //切配置就整组换成那条的已保存内容，表单里没保存的改动会丢
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
            Modifier.size(24.dp).liquidBg(CircleShape, tint = if (sel) SurfaceWhite else Color.Transparent)
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
            Modifier.size(28.dp).liquidBg(CircleShape, tint = SurfaceMuted).tap { onClose() },
            contentAlignment = Alignment.Center
        ) { IcClose(TextSecondary, side = 12.dp) }
    }
} //弹窗标题行

@Composable
private fun ProviderDialog(current: String, onPick: (Provider) -> Unit, onClose: () -> Unit) {
    ScrimCard {
        DialogHead(stringResource(R.string.ms_pick_provider), onClose)
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp)
                .verticalScroll(glassScrollState())
        ) {
            Providers.builtin.forEach { p ->
                val sel = p.id == current
                Row(
                    Modifier
                        .fillMaxWidth()
                        .liquidBg(RoundedCornerShape(10.dp)
                        , tint = if (sel) AccentBlueSoft else Color.Transparent)
                        .tap { onPick(p) }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontSize = 13.sp, color = if (sel) AccentBlue else TextPrimary)
                        Text(
                            p.baseUrl.ifBlank { stringResource(R.string.ms_need_base) },
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
        Text(stringResource(R.string.ms_pick_provider_sub), fontSize = 10.sp, color = TextSecondary)
    }
} //厂商弹窗

/** 模型多选卡片：全量列表带搜索，勾上的存进当前配置，聊天页只在这批里挑 */
@Composable
private fun ModelPickMulti(
    models: List<String>,
    loading: Boolean,
    err: String?,
    q: String,
    onQ: (String) -> Unit,
    picked: Set<String>,
    onToggle: (String) -> Unit,
    onAll: (List<String>) -> Unit,
    onClear: () -> Unit,
    onRetry: () -> Unit,
    onAddModel: (String) -> Unit,
    onOk: () -> Unit,
    onClose: () -> Unit
) {
    val kw = q.trim()
    val shown = if (kw.isEmpty()) models else models.filter { it.contains(kw, ignoreCase = true) }
    //手打添加用的输入：厂商接口拉不到的模型也能在这里加进来
    var addM by remember { mutableStateOf("") }
    //半角逗号分隔可以一次加多个，全角逗号一起认，空格自动去掉
    fun commitAdd() {
        if (addM.isBlank()) return
        addM.split(',', '，').map { it.trim() }.filter { it.isNotEmpty() }.forEach(onAddModel)
        addM = ""
    }
    //返回键先收卡片，别把整个设置页带出去
    BackHandler { onClose() }
    BottomSheetCard(onDismiss = onClose) {
        Box(Modifier.fillMaxWidth().padding(bottom = 6.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.width(36.dp).height(4.dp).background(BorderGray, CircleShape))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.ms_pick_model),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    if (loading) stringResource(R.string.ms_fetching)
                    else stringResource(R.string.ms_models_picked, picked.size),
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
            Text(
                stringResource(R.string.ms_fetch),
                fontSize = 11.sp,
                color = AccentBlue,
                modifier = Modifier.tap { onRetry() }.padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        //搜索只过滤本地已拉到的列表，不发请求
        Row(
            Modifier
                .fillMaxWidth()
                .liquidBg(RoundedCornerShape(10.dp), tint = SurfaceMuted)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("⌕", fontSize = 13.sp, color = TextSecondary)
            Spacer(Modifier.width(6.dp))
            Box(Modifier.weight(1f)) {
                if (q.isEmpty()) {
                    Text(stringResource(R.string.ms_search), fontSize = 12.sp, color = TextSecondary)
                }
                BasicTextField(
                    value = q,
                    onValueChange = onQ,
                    textStyle = TextStyle(fontSize = 12.sp, color = TextPrimary),
                    singleLine = true,
                    cursorBrush = SolidColor(AccentBlue),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (q.isNotEmpty()) {
                Text(
                    "✕",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.tap { onQ("") }.padding(start = 6.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.ms_select_all),
                fontSize = 11.sp,
                color = AccentBlue,
                modifier = Modifier.tap { onAll(shown) }.padding(vertical = 4.dp)
            )
            Spacer(Modifier.width(14.dp))
            Text(
                stringResource(R.string.ms_clear_all),
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.tap { onClear() }.padding(vertical = 4.dp)
            )
        }
        //手打添加：列表里没有的模型能被加进来并直接勾上，不随列表滚动，永远看得见
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .liquidBg(RoundedCornerShape(10.dp), tint = SurfaceMuted)
                .padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f)) {
                if (addM.isEmpty()) {
                    Text(stringResource(R.string.ms_add_model_hint), fontSize = 12.sp, color = TextSecondary)
                }
                BasicTextField(
                    value = addM,
                    onValueChange = { addM = it.take(128) },
                    textStyle = TextStyle(fontSize = 12.sp, color = TextPrimary),
                    singleLine = true,
                    cursorBrush = SolidColor(AccentBlue),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commitAdd() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Text(
                stringResource(R.string.ms_add_ok),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (addM.isBlank()) TextSecondary else AccentBlue,
                modifier = Modifier
                    .tap { commitAdd() }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }
        //常驻小字：placeholder 一打字就没了，说明得单独留一行
        Text(
            stringResource(R.string.ms_add_model_tip),
            fontSize = 10.sp,
            color = TextSecondary,
            lineHeight = 14.sp,
            modifier = Modifier.padding(top = 4.dp, start = 2.dp)
        )
        Spacer(Modifier.height(4.dp))
        if (err != null) {
            Text(err, fontSize = 11.sp, color = DangerRed, modifier = Modifier.padding(bottom = 6.dp))
        }
        if (loading) {
            Box(
                Modifier.fillMaxWidth().padding(vertical = 44.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.ms_fetching), fontSize = 13.sp, color = TextSecondary)
            }
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(glassScrollState())
            ) {
                shown.forEach { m ->
                    val on = m in picked
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .liquidBg(RoundedCornerShape(12.dp), tint = if (on) AccentBlueSoft else Color.Transparent)
                            .tap { onToggle(m) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            m,
                            fontSize = 14.sp,
                            color = if (on) AccentBlue else TextPrimary,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            if (on) "✓" else "○",
                            fontSize = 13.sp,
                            color = if (on) AccentBlue else TextSecondary
                        )
                    }
                }
                if (shown.isEmpty()) {
                    Text(
                        stringResource(R.string.ms_no_models),
                        fontSize = 11.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(vertical = 18.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .weight(1f)
                    .liquidBg(RoundedCornerShape(12.dp), tint = SurfaceMuted)
                    .tap { onClose() }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.ms_multi_cancel), fontSize = 13.sp, color = TextPrimary)
            }
            Box(
                Modifier
                    .weight(1f)
                    .liquidBg(RoundedCornerShape(12.dp), tint = if (picked.isEmpty()) SurfaceMuted else AccentBlue)
                    .tap { if (picked.isNotEmpty()) onOk() }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.ms_multi_ok),
                    fontSize = 13.sp,
                    color = if (picked.isEmpty()) TextSecondary else Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
} //模型多选卡片
@Composable
private fun HttpWarnDialog(
    url: String,
    left: Int,
    onCancel: () -> Unit,
    onUseHttps: () -> Unit,
    onUseHttp: () -> Unit
) {
    val ready = left <= 0
    ScrimCard {
        DialogHead(stringResource(R.string.http_warn_title), onCancel)
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.http_warn_body, url),
            fontSize = 12.sp,
            color = TextPrimary,
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.http_warn_risk),
            fontSize = 11.sp,
            color = TextSecondary,
            lineHeight = 16.sp
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .weight(1f)
                    .liquidBg(RoundedCornerShape(12.dp), tint = SurfaceMuted)
                    .tap { onUseHttps() }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.http_warn_https),
                    fontSize = 13.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium
                )
            }
            Box(
                Modifier
                    .weight(1f)
                    .liquidBg(RoundedCornerShape(12.dp), tint = if (ready) AccentBlue else SurfaceMuted)
                    .tap(enabled = ready) { if (ready) onUseHttp() }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (ready) stringResource(R.string.http_warn_use)
                    else stringResource(R.string.http_warn_wait, left),
                    fontSize = 13.sp,
                    color = if (ready) Color.White else TextSecondary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
} //HTTP 明文警告：5 秒后才允许确认

@Composable
private fun ModelSheet(
    stage: String,
    providerId: String,
    models: List<String>,
    current: String,
    err: String?,
    onPickProvider: (Provider) -> Unit,
    onPickModel: (String) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit
) {
    BottomSheetCard(onDismiss = onClose) {
        //顶部把手
        Box(Modifier.fillMaxWidth().padding(bottom = 6.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.width(36.dp).height(4.dp).background(BorderGray, CircleShape))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (stage != "provider") {
                Text(
                    "‹",
                    fontSize = 20.sp,
                    color = TextSecondary,
                    modifier = Modifier
                        .tap { onBack() }
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            Text(
                stringResource(if (stage == "provider") R.string.ms_pick_provider else R.string.ms_pick_model),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
        }
        Spacer(Modifier.height(6.dp))
        if (stage == "loading") {
            Box(
                Modifier.fillMaxWidth().padding(vertical = 44.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.ms_fetching), fontSize = 13.sp, color = TextSecondary)
            }
        } else {
            if (err != null) {
                Text(err, fontSize = 11.sp, color = DangerRed, modifier = Modifier.padding(bottom = 6.dp))
            }
            Column(Modifier.fillMaxWidth().verticalScroll(glassScrollState())) {
                if (stage == "provider") {
                    Providers.builtin.forEach { p ->
                        SheetRow(p.name, p.baseUrl, p.id == providerId) { onPickProvider(p) }
                    }
                } else {
                    models.forEach { m ->
                        SheetRow(m, null, m == current) { onPickModel(m) }
                    }
                }
            }
        }
    }
} //模型选择吸附卡片

@Composable
private fun SheetRow(title: String, sub: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .liquidBg(RoundedCornerShape(12.dp), tint = if (selected) AccentBlueSoft else Color.Transparent)
            .tap { onClick() }
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = if (selected) AccentBlue else TextPrimary, maxLines = 1)
            if (!sub.isNullOrBlank()) {
                Text(sub, fontSize = 10.sp, color = TextSecondary, maxLines = 1)
            }
        }
        if (selected) Text("✓", fontSize = 13.sp, color = AccentBlue)
    }
} //吸附卡片里的一行

//模型配置的说明文字在设置项里，这里不再单独弹提示
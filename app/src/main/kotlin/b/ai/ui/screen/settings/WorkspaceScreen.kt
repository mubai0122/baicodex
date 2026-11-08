package b.ai.ui.screen.settings
import b.ai.ui.glass.GlassPerf
import b.ai.ui.glass.ProvideCardBackdrop
import b.ai.ui.glass.backdropPageInset
import b.ai.ui.glass.drawPageBg
import b.ai.ui.glass.glassPageLayer
import b.ai.ui.glass.glassScrollState
import b.ai.ui.glass.liquidBg
import b.ai.ui.glass.pageBg
import b.ai.ui.widget.CardBox
import b.ai.ui.widget.Field
import b.ai.ui.widget.GhostButton
import b.ai.ui.widget.PrimaryButton
import b.ai.ui.widget.SectionTitle
import b.ai.ui.widget.WsPickerBody
import b.ai.ui.widget.tap

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.R
import b.ai.core.cfg.app.Cfg
import b.ai.core.cfg.io.Paths
import b.ai.device.fs.Workspace
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.AccentBlueSoft
import b.ai.ui.theme.BgGray
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 工作区页面：信息卡 + 底栏两个动作，目录选择是同一页里的二级；backProgress 是返回手势进度 */
@Composable
internal fun WorkspaceScreen(backProgress: Float = 0f, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(Workspace.path()) }
    var files by remember { mutableIntStateOf(0) }
    var dirs by remember { mutableIntStateOf(0) }
    var bytes by remember { mutableLongStateOf(0L) }
    var tree by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    //绑定 / 解绑后换 key 重来一遍：草稿、统计与目录树一起刷新
    var ver by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    remember { GlassPerf.switched(); Unit }

    //扫盘可能上千个文件，一律走 IO 线程，扫完再回主线程更新
    fun reload() {
        scope.launch {
            busy = true
            val list = withContext(Dispatchers.IO) { Workspace.scan() }
            files = list.count { !it.dir }
            dirs = list.count { it.dir }
            bytes = list.filter { !it.dir }.sumOf { it.size }
            tree = withContext(Dispatchers.IO) { Workspace.tree() }
            busy = false
        }
    }

    LaunchedEffect(ver) {
        draft = Workspace.path()
        reload()
    }

    //把某个目录绑到当前会话：就地重扫，成功后草稿跟着变成新路径
    fun bindTo(p: String) {
        scope.launch {
            busy = true
            val ok = withContext(Dispatchers.IO) { Workspace.bind(p) }
            note = ctx.getString(if (ok) R.string.wp_bound_ok else R.string.wp_bind_fail)
            busy = false
            picking = false
            ver++
        }
    }

    //选择目录时返回手势先退选择器，别一下退回对话页
    BackHandler(enabled = picking) { picking = false }

    val (pageTop, pageBottom) = backdropPageInset()
    val pageBackdrop = rememberLayerBackdrop {
        drawRect(BgGray)
        //采样层必须含真背景，否则底栏玻璃在没内容的地方采到一块灰白
        drawPageBg(offsetY = -pageTop, fullHeight = size.height + pageTop + pageBottom)
        drawContent()
    }
    val cardBackdrop = rememberLayerBackdrop {
        drawRect(BgGray)
        drawContent()
    }

    ProvideCardBackdrop(cardBackdrop) {
        Box(
            Modifier
                .fillMaxSize()
                //返回手势跟手：整页右移 + 轻微缩小 + 淡出，松手没滑过阈值就归位
                .graphicsLayer {
                    translationX = size.width * 0.22f * backProgress
                    scaleX = 1f - 0.05f * backProgress
                    scaleY = 1f - 0.05f * backProgress
                    alpha = (1f - 0.55f * backProgress).coerceIn(0f, 1f)
                }
        ) {
            Box(Modifier.fillMaxSize().layerBackdrop(cardBackdrop).pageBg())
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Column(Modifier.fillMaxSize().glassPageLayer(pageBackdrop)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 18.dp, end = 10.dp, top = 8.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(if (picking) R.string.wp_pick_title else R.string.wp_title),
                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(if (picking) R.string.wp_pick_hint else R.string.wp_title_d),
                            fontSize = 12.sp, color = TextSecondary, maxLines = 1
                        )
                        Spacer(Modifier.weight(1f))
                        Row(
                            Modifier
                                .liquidBg(RoundedCornerShape(9.dp), tint = AccentBlueSoft)
                                .tap { if (picking) picking = false else onBack() }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.wp_pick_cancel),
                                tint = AccentBlue,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                stringResource(if (picking) R.string.wp_pick_cancel else R.string.se_059),
                                fontSize = 11.sp, color = AccentBlue
                            )
                        }
                    }

                    if (picking) {
                        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                            WsPickerBody(
                                start = Workspace.path(),
                                onCancel = { picking = false },
                                onPick = { p -> bindTo(p) }
                            )
                        }
                    } else {
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(glassScrollState())
                                .padding(horizontal = 16.dp)
                        ) {
                            SectionTitle(stringResource(R.string.wp_title), stringResource(R.string.wp_title_d))
                            CardBox {
                                CapSwitch(
                                    stringResource(R.string.wp_auto),
                                    stringResource(R.string.wp_auto_d),
                                    Cfg.autoBindWorkspace
                                ) { on ->
                                    Cfg.autoBindWorkspace = on
                                    //关掉时连会话行一起清：只清内存的话下次 adopt 又从 DB 读回来，开关看着关了其实没关
                                    if (!on) Workspace.unbind()
                                    Workspace.invalidate()
                                    ver++
                                }
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    when {
                                        Workspace.path().isEmpty() -> stringResource(R.string.wp_unbound)
                                        Workspace.custom() -> stringResource(R.string.wp_bound, Workspace.path())
                                        else -> stringResource(R.string.wp_cur, Workspace.path())
                                    },
                                    fontSize = 13.sp,
                                    color = TextPrimary,
                                    maxLines = 3
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    if (Workspace.path().isEmpty()) stringResource(R.string.wp_none)
                                    else stringResource(R.string.wp_stat, files, dirs, Workspace.human(bytes)),
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    maxLines = 3
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                            SectionTitle(stringResource(R.string.wp_path))
                            CardBox {
                                Field(
                                    stringResource(R.string.wp_path),
                                    draft,
                                    { draft = it },
                                    placeholder = "${Paths.base().absolutePath}/xxxx-xxxx-xxxx-xxxx-xxxx-xxxx"
                                )
                                Spacer(Modifier.height(6.dp))
                                GhostButton(stringResource(R.string.wp_browse), modifier = Modifier.fillMaxWidth()) {
                                    picking = true
                                }
                                Spacer(Modifier.height(6.dp))
                                //快捷胶囊一行铺开：四条标签加起来不到一行宽，别硬拆两行
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Workspace.quickPicks().forEach { (label, p) ->
                                        PolicyChip(label, Workspace.path() == p) { draft = p }
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                GhostButton(stringResource(R.string.wp_reindex), modifier = Modifier.fillMaxWidth()) {
                                    scope.launch {
                                        busy = true
                                        val n = withContext(Dispatchers.IO) { Workspace.index() }
                                        note = if (n > 0) ctx.getString(R.string.wp_reindex_ok, Workspace.INDEX, n)
                                        else ctx.getString(R.string.wp_reindex_none)
                                        busy = false
                                        reload()
                                    }
                                }
                                if (note.isNotBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(note, fontSize = 11.sp, color = TextSecondary)
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            SectionTitle(stringResource(R.string.wp_tree))
                            CardBox {
                                Text(
                                    when {
                                        busy -> stringResource(R.string.wp_scanning)
                                        tree.isBlank() -> stringResource(R.string.wp_tree_empty)
                                        else -> tree.take(1200)
                                    },
                                    fontSize = 10.sp,
                                    color = TextPrimary,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Spacer(Modifier.height(24.dp))
                        }
                        //底栏固定两个动作：主次分明、同高，滚到头也不会被内容压住
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PrimaryButton(
                                stringResource(R.string.wp_bind),
                                enabled = !busy,
                                modifier = Modifier.weight(1f).height(46.dp)
                            ) { bindTo(draft) }
                            GhostButton(
                                stringResource(R.string.wp_reset),
                                modifier = Modifier.weight(1f).height(46.dp)
                            ) {
                                Workspace.unbind()
                                note = if (Cfg.autoBindWorkspace) ctx.getString(R.string.wp_reset_ok)
                                else ctx.getString(R.string.wp_reset_unbound)
                                ver++
                            }
                        }
                    }
                }
            }
        }
    }
} //工作区页面
package b.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.agent.Agent
import b.ai.core.Repo
import b.ai.core.SessionRow
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.AccentBlueSoft
import b.ai.ui.theme.BgGray
import b.ai.ui.theme.BorderGray
import b.ai.ui.theme.DangerRed
import b.ai.ui.theme.DangerRedSoft
import b.ai.ui.theme.DarkColors
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.SurfaceWhite
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import b.ai.R

/** 侧边栏：搜索 + 会话列表 + 底部按钮栏（新对话 / 设置）；收藏、导出、记忆都收进设置页了 */
@Composable
fun DrawerPanel(
    onNewChat: () -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    var hit by remember { mutableStateOf<Set<String>?>(null) }
    var menu by remember { mutableStateOf<SessionRow?>(null) }
    //长按那一行在屏幕上的位置，菜单按它决定往上挂还是往下挂
    var menuY by remember { mutableIntStateOf(0) }
    var menuH by remember { mutableIntStateOf(0) }
    //面板自身的顶边与高度，用来把屏幕坐标换算成面板内坐标
    var boxTop by remember { mutableIntStateOf(0) }
    var boxH by remember { mutableIntStateOf(0) }
    //菜单内部三态：默认 / 就地改名 / 等二次确认删除
    var menuEdit by remember { mutableStateOf(false) }
    var menuConfirm by remember { mutableStateOf(false) }
    var bulkConfirm by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf("") }
    //非空 = 多选模式：长按任意会话进来，集合里就是被选中的会话 id
    var sel by remember { mutableStateOf<Set<String>>(emptySet()) }

    //搜索命中在 IO 线程算，主线程只做过滤渲染
    LaunchedEffect(q) {
        val key = q.trim()
        if (key.isEmpty()) {
            hit = null
        } else {
            hit = withContext(Dispatchers.IO) { Repo.searchSessionIds(key) }
        }
    }

    //选中集合一变就把批量删除的确认态收回去，避免带着旧确认去点新一批
    LaunchedEffect(sel) { bulkConfirm = false }

    //当前会话 id 在面板这一层读一次：item 里再用它比较，避免高亮跟着重组范围出岔子
    val curId = Agent.sessionId
    val all = Agent.sessions
    val h = hit
    val shown = if (h == null) all else all.filter { h.contains(it.id) }
    android.util.Log.d("BaiDBG", "drawer compose cur=${curId?.take(8)} shown=${shown.map { it.id.take(8) }}")

    Box(
        Modifier
            .width(DrawerWidth)
            .fillMaxHeight()
            .background(BgGray)
            .onGloballyPositioned {
                boxTop = it.positionInRoot().y.toInt()
                boxH = it.size.height
            }
    ) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 14.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("BaiAgent", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(30.dp).background(SurfaceWhite, CircleShape).tap { onClose() },
                    contentAlignment = Alignment.Center
                ) { IcClose(TextSecondary, side = 13.dp) }
            }

            if (sel.isEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(SurfaceWhite, RoundedCornerShape(12.dp))
                        .border(1.dp, BorderGray, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IcSearch(TextSecondary, side = 14.dp)
                    Spacer(Modifier.width(7.dp))
                    Box(Modifier.weight(1f)) {
                        if (q.isEmpty()) Text(stringResource(R.string.dw_search), fontSize = 12.sp, color = TextSecondary)
                        BasicTextField(
                            value = q,
                            onValueChange = { q = it },
                            textStyle = TextStyle(fontSize = 12.sp, color = TextPrimary),
                            singleLine = true,
                            cursorBrush = SolidColor(AccentBlue),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (q.isNotEmpty()) {
                        Box(Modifier.size(18.dp).tap { q = "" }, contentAlignment = Alignment.Center) {
                            IcClose(TextSecondary, side = 10.dp)
                        }
                    }
                }
            } else {
                //多选工具条：就地顶掉搜索框，不额外挤列表高度
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(AccentBlueSoft, RoundedCornerShape(12.dp))
                        .border(1.dp, AccentBlue, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.dw_selected, sel.size), fontSize = 12.sp, color = AccentBlue)
                    Spacer(Modifier.weight(1f))
                    Text(
                        stringResource(R.string.dw_all),
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier
                            .tap { sel = shown.map { it.id }.toSet() }
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                    Text(
                        stringResource(R.string.dw_cancel),
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier
                            .tap { sel = emptySet() }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                items(shown, key = { it.id }) { s ->
                    SessionItem(
                        s = s,
                        active = s.id == curId,
                        checked = sel.contains(s.id),
                        selecting = sel.isNotEmpty(),
                        //非多选态点一下就是打开；多选态点一下是勾选 / 取消

                        onClick = {
                            if (sel.isEmpty()) {
                                Agent.openSession(s.id)
                                onClose()
                            } else {
                                sel = if (sel.contains(s.id)) sel - s.id else sel + s.id
                            }
                        },
                        onLong = { y, h ->
                            menuY = y
                            menuH = h
                            menu = s
                            menuEdit = false
                            menuConfirm = false
                        }
                    )
                }
                if (shown.isEmpty()) {
                    item {
                        Text(
                            if (h == null) stringResource(R.string.dw_empty) else stringResource(R.string.dw_no_match),
                            fontSize = 11.sp,
                            color = TextSecondary,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            if (toast.isNotBlank()) {
                Text(
                    toast,
                    fontSize = 10.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                )
            }

            if (sel.isEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DrawerAction(stringResource(R.string.dw_new), Modifier.weight(1f)) {
                        toast = ""
                        onNewChat()
                    }
                    //收藏 / 导出 / 记忆都挪到设置的「对话」「模型」页了，抽屉只剩这两个高频入口
                    DrawerAction(stringResource(R.string.dw_settings), Modifier.weight(1f)) {
                        toast = ""
                        onOpenSettings()
                    }
                }
            } else {
                //多选态：底部换成改名 / 删除；改名只对单条开放
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DrawerAction(stringResource(R.string.dw_rename), Modifier.weight(1f), enabled = sel.size == 1) {
                        //改名不另开弹窗：直接拉起菜单并落在就地编辑态
                        shown.firstOrNull { sel.contains(it.id) }?.let { s ->
                            menu = s
                            menuEdit = true
                            menuConfirm = false
                            sel = emptySet()
                        }
                    }
                    DrawerAction(
                        if (bulkConfirm) stringResource(R.string.dw_del_confirm) else stringResource(R.string.dw_del),
                        Modifier.weight(1f),
                        danger = true
                    ) {
                        if (!bulkConfirm) {
                            bulkConfirm = true
                        } else {
                            val ids = sel
                            sel = emptySet()
                            Agent.deleteSessions(ids)
                            toast = ctx.getString(R.string.dw_deleted, ids.size)
                        }
                    }
                }
            }
        }

        menu?.let { s ->
            //以面板中线为界：长按那行在上半屏就往下挂，在下半屏就往上翻
            val gap = with(LocalDensity.current) { 4.dp.roundToPx() }
            val rowLocal = menuY - boxTop
            val below = rowLocal + menuH / 2 < boxH / 2
            val dy = if (below) rowLocal + menuH + gap else rowLocal - gap - boxH
            //透明层贴满面板，负责把菜单外的点击吃掉当关闭
            Box(Modifier.fillMaxSize().tap { menu = null; menuEdit = false; menuConfirm = false })
            SessionMenu(
                s = s,
                editing = menuEdit,
                confirmDel = menuConfirm,
                modifier = Modifier
                    .align(if (below) Alignment.TopEnd else Alignment.BottomEnd)
                    .offset { IntOffset(0, dy) }
                    .padding(end = 10.dp),
                onPin = {
                    Agent.pinSession(s.id, !s.pinned)
                    menu = null
                    menuEdit = false
                },
                onEdit = { menuEdit = true },
                onCancelEdit = { menuEdit = false },
                onRename = { t ->
                    Agent.renameSession(s.id, t)
                    menu = null
                    menuEdit = false
                },
                onAskDelete = { menuConfirm = true },
                onDelete = {
                    Agent.deleteSessions(setOf(s.id))
                    menu = null
                    menuConfirm = false
                },
                onMulti = {
                    //进多选并把这行勾上，菜单随即关掉
                    menu = null
                    sel = setOf(s.id)
                }
            )
        }
    }

} //侧边栏

@Composable
private fun DrawerAction(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier
            .background(if (danger) DangerRedSoft else SurfaceWhite, RoundedCornerShape(9.dp))
            .border(1.dp, if (danger) Color.Transparent else BorderGray, RoundedCornerShape(9.dp))
            .tap(enabled = enabled) { onClick() }
            .padding(vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 11.sp,
            color = when {
                !enabled -> TextSecondary
                danger -> DangerRed
                else -> TextPrimary
            }
        )
    }
} //底部纯文字按钮（不再配图标）

@Composable
private fun SessionItem(
    s: SessionRow,
    active: Boolean,
    checked: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLong: (y: Int, h: Int) -> Unit
) {
    //本行在屏幕上的位置，长按时交给面板决定菜单往上还是往下挂
    var rowY by remember { mutableIntStateOf(0) }
    var rowH by remember { mutableIntStateOf(0) }
    //勾选行用浅蓝底区分；多选态下不再透出右侧「⋯」，整行只认点击 / 长按
    val bg = when {
        checked -> AccentBlueSoft
        active -> SurfaceWhite
        else -> Color.Transparent
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(12.dp))
            .onGloballyPositioned {
                rowY = it.positionInRoot().y.toInt()
                rowH = it.size.height
            }
            .tapLong(onClick = onClick, onLong = { onLong(rowY, rowH) })
            .padding(start = 10.dp, end = 10.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selecting) {
            Box(
                Modifier
                    .size(15.dp)
                    .background(if (checked) AccentBlue else Color.Transparent, CircleShape)
                    .border(1.5.dp, if (checked) AccentBlue else BorderGray, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (checked) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            //置顶的会话在标题后面挂一颗小星，一眼能看出哪些被钉住了
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    s.title.ifBlank { stringResource(R.string.ag_new_session) },
                    fontSize = 13.sp,
                    color = if (active || checked) AccentBlue else TextPrimary,
                    fontWeight = if (active || checked) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (s.pinned) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = stringResource(R.string.dw_pinned),
                        tint = AccentBlue,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
            //副标题优先用 AI 生成的那句描述，还没生成就退回最后一条消息预览
            val sub = s.descr.ifBlank { s.preview.replace("\n", " ") }
            if (sub.isNotBlank()) {
                Text(
                    sub.take(40),
                    fontSize = 10.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
} //会话行：标题 + 副标题，多选态左侧圆圈

@Composable
private fun SessionMenu(
    s: SessionRow,
    editing: Boolean,
    confirmDel: Boolean,
    modifier: Modifier = Modifier,
    onPin: () -> Unit,
    onEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onRename: (String) -> Unit,
    onAskDelete: () -> Unit,
    onDelete: () -> Unit,
    onMulti: () -> Unit
) {
    var name by remember(s.id) { mutableStateOf(s.title) }
    Column(
        modifier
            .widthIn(min = 128.dp, max = 150.dp)
            .shadow(10.dp, RoundedCornerShape(12.dp))
            .background(SurfaceWhite, RoundedCornerShape(12.dp))
            .tap { }
            .padding(vertical = 3.dp)
    ) {
            if (editing) {
                Text(
                    s.title.ifBlank { stringResource(R.string.ag_new_session) },
                    fontSize = 9.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 3.dp)
                )
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    textStyle = TextStyle(fontSize = 12.sp, color = TextPrimary),
                    singleLine = true,
                    cursorBrush = SolidColor(AccentBlue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 9.dp)
                        .background(SurfaceMuted, RoundedCornerShape(7.dp))
                        .padding(horizontal = 7.dp, vertical = 6.dp)
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MenuBtn(stringResource(R.string.dw_cancel), false, Modifier.weight(1f)) { onCancelEdit() }
                    MenuBtn(stringResource(R.string.dw_save), true, Modifier.weight(1f)) { onRename(name) }
                }
                Spacer(Modifier.height(3.dp))
            } else {
                MenuRow(Icons.Filled.Star, if (s.pinned) stringResource(R.string.dw_unpin) else stringResource(R.string.dw_pin)) { onPin() }
                MenuDivider()
                MenuRow(Icons.Filled.Edit, stringResource(R.string.dw_rename_menu)) { onEdit() }
                MenuDivider()
                MenuRow(Icons.Filled.Check, stringResource(R.string.dw_multi)) { onMulti() }
                MenuDivider()
                MenuRow(
                    Icons.Filled.Delete,
                    if (confirmDel) stringResource(R.string.dw_del_confirm) else stringResource(R.string.dw_del),
                    danger = true
                ) { if (confirmDel) onDelete() else onAskDelete() }
            }
    }
} //长按会话弹出的菜单（置顶 / 改名就地编辑 / 多选 / 删除二次确认）

@Composable
private fun MenuRow(icon: ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    val tint = if (danger) DangerRed else TextPrimary
    Row(
        Modifier
            .fillMaxWidth()
            .tap { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 11.sp, color = tint, maxLines = 1)
    }
} //菜单一行：图标 + 文字

@Composable
private fun MenuDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 30.dp, end = 10.dp)
            .height(1.dp)
            .background(BorderGray.copy(alpha = 0.45f))
    )
} //菜单条目之间的细分割线

@Composable
private fun MenuBtn(label: String, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 11.sp,
        color = if (primary) Color.White else TextSecondary,
        textAlign = TextAlign.Center,
        modifier = modifier
            .background(if (primary) AccentBlue else SurfaceMuted, RoundedCornerShape(7.dp))
            .tap { onClick() }
            .padding(vertical = 7.dp)
    )
} //菜单底部两个小按钮
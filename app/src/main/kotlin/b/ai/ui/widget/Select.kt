/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:26
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package b.ai.ui.widget
import b.ai.ui.dialog.ScrimCard
import b.ai.ui.glass.glassScrollState
import b.ai.ui.glass.liquidBg
import b.ai.net.api.llm.text

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import b.ai.R
import b.ai.ui.theme.BgGray
import b.ai.ui.theme.BorderGray
import b.ai.ui.theme.DarkColors
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.AccentBlueSoft
import b.ai.ui.theme.SurfaceWhite
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility

//一个可选项：label 是显示文案，selected 决定打勾，onPick 在点中时执行
internal class PickItem(val label: String, val selected: Boolean, val onPick: () -> Unit)

//把「打开选择面板」的入口往下传，四个 tab 不用各自多带一个回调参数
internal val LocalPicker = staticCompositionLocalOf<(String, List<PickItem>) -> Unit> { { _, _ -> } }

/**
 * 下拉选择行：左边标题（可带副标题），右边当前值胶囊 + 下箭头，整行可点；menu 的锚点就是这颗胶囊。
 * open 决定箭头朝向（点开时转 180°），由 SelectField 传进来，其它调用方用默认值即是收起态。
 */
@Composable
internal fun SelectRow(
    label: String,
    value: String,
    desc: String? = null,
    onClick: () -> Unit,
    open: Boolean = false,
    enabled: Boolean = true,
    menu: @Composable BoxScope.(DpSize) -> Unit = {}
) {
    Row(
        Modifier
            .fillMaxWidth()
            //禁用态整行压暗：壁纸取色开着时，色系这一行由背景图决定，点了也没意义
            .alpha(if (enabled) 1f else 0.38f)
            .heightIn(min = 44.dp)
            .tap(enabled) { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 13.5.sp, color = TextPrimary)
            if (desc != null) Text(desc, fontSize = 10.5.sp, color = TextSecondary)
        }
        Spacer(Modifier.width(10.dp))
        val den = LocalDensity.current
        var pill by remember { mutableStateOf(DpSize.Zero) }
        //箭头跟随开合转 180°：跟面板的缩放同步，读的是同一个 open
        val arrow by animateFloatAsState(
            targetValue = if (open) 180f else 0f,
            animationSpec = tween(durationMillis = 180),
            label = "selectArrow"
        )
        Box(Modifier.onSizeChanged { pill = with(den) { DpSize(it.width.toDp(), it.height.toDp()) } }) {
            Row(
                Modifier
                    //下拉胶囊用纯平涂：这里原来挂 gate=0.dp 的强制玻璃，24dp 高的小胶囊照样跑一遍
                    //离屏+高光，深色底上透出来就是一层「半透明阴影」，被用户当成下拉框自身的投影
                    .background(AccentBlueSoft, RoundedCornerShape(9.dp))
                    .padding(start = 9.dp, end = 5.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(value, fontSize = 12.sp, color = AccentBlue, maxLines = 1)
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = AccentBlue,
                    modifier = Modifier
                        .size(15.dp)
                        .rotate(arrow)
                )
            }
            menu(pill)
        }
    }
} //下拉选择行

/** 类型化的选择字段：点行就地展开下拉，选中后行尾胶囊回显，不再走居中弹窗 */
@Composable
internal fun <T> SelectField(
    label: String,
    value: String,
    options: List<T>,
    desc: String? = null,
    optionLabel: @Composable (T) -> String,
    isSelected: (T) -> Boolean,
    enabled: Boolean = true,
    onSelect: (T) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    //shown 是 Popup 内部的进场开关：先组合出来再置 true，进场动画才有起点
    var shown by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val labels = ArrayList<String>(options.size)
    for (o in options) labels.add(optionLabel(o))
    //菜单宽度固定，右边缘跟胶囊右边缘对齐；用原生 Popup 自绘，材质和位置都自己拿，
    //不走 Material3 的 DropdownMenu（它内部那层 background lambda 会盖掉 liquidBg）
    val menuW = 216.dp
    val den = LocalDensity.current
    val gapPx = with(den) { 4.dp.roundToPx() }
    val padPx = with(den) { 8.dp.roundToPx() }
    val pos = remember(gapPx, padPx) { MenuPos(gapPx, padPx) }
    //先播退场再销毁 Popup：直接把 open 置 false 会让面板瞬间消失，动画等于没有
    val close: (() -> Unit) -> Unit = { after ->
        shown = false
        scope.launch {
            delay(110)
            open = false
            after()
        }
    }
    SelectRow(label, value, desc, onClick = {
        if (!enabled) return@SelectRow
        if (open) close {}
        else {
            shown = false
            open = true
        }
    }, open = open, enabled = enabled) { _ ->
        if (open) {
            Popup(
                popupPositionProvider = pos,
                onDismissRequest = { close {} },
                properties = PopupProperties(focusable = true)
            ) {
                LaunchedEffect(Unit) { shown = true }
                AnimatedVisibility(
                    visible = shown,
                    //右上方为锚点缩放 + 淡入：面板是从胶囊位置「长出来」的，不是凭空闪出
                    enter = fadeIn(tween(140)) + scaleIn(
                        animationSpec = tween(160),
                        initialScale = 0.92f,
                        transformOrigin = TransformOrigin(1f, 0f)
                    ),
                    exit = fadeOut(tween(100)) + scaleOut(
                        animationSpec = tween(120),
                        targetScale = 0.96f,
                        transformOrigin = TransformOrigin(1f, 0f)
                    )
                ) {
                    Column(
                        Modifier
                            .width(menuW)
                            .heightIn(max = 320.dp)
                            //Popup 是独立窗口，拿不到宿主页面的 backdrop：liquidBg 在这里采样不到背景，
                            //模糊出来是一层灰（用户看到的「灰色阴影」）。下拉改用不透明表面色 + 细边框，
                            //既没有灰影，也不会在半透明下透出下层文字。
                            .background(
                                if (DarkColors) BgGray else SurfaceWhite,
                                RoundedCornerShape(12.dp)
                            )
                            .border(0.8.dp, BorderGray, RoundedCornerShape(12.dp))
                            .verticalScroll(glassScrollState())
                            .padding(vertical = 4.dp)
                    ) {
                        options.forEachIndexed { i, o ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    //选中后先收面板再回调，避免列表项在动画期间被上层重组掉
                                    .tap { close { onSelect(o) } }
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    labels[i],
                                    fontSize = 12.5.sp,
                                    color = if (isSelected(o)) AccentBlue else TextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected(o)) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = AccentBlue,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
} //下拉选择字段（贴胶囊页内展开）

/** 选择面板：当前项打勾，点一项就关面板并回调 */
@Composable
internal fun PickSheet(title: String, items: List<PickItem>, onClose: () -> Unit) {
    ScrimCard(onDismiss = onClose) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Column(
            Modifier
                .heightIn(max = 380.dp)
                .verticalScroll(glassScrollState())
        ) {
            items.forEach { o ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 46.dp)
                        .tap { onClose(); o.onPick() }
                        .padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        o.label,
                        fontSize = 13.5.sp,
                        color = if (o.selected) AccentBlue else TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    if (o.selected) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = AccentBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            GhostButton(stringResource(R.string.se_142), modifier = Modifier.height(36.dp), onClick = onClose)
        }
    }
} //选择面板

//下拉菜单定位：右边缘贴胶囊右缘，下方放不下就翻到胶囊上方，四边留 pad 不出屏
private class MenuPos(private val gap: Int, private val pad: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val w = popupContentSize.width
        val h = popupContentSize.height
        val maxX = (windowSize.width - w - pad).coerceAtLeast(pad)
        val maxY = (windowSize.height - h - pad).coerceAtLeast(pad)
        val x = (anchorBounds.right - w).coerceIn(pad, maxX)
        val below = windowSize.height - anchorBounds.bottom - gap
        val above = anchorBounds.top - gap
        val y = if (h > below && above > below) anchorBounds.top - h - gap else anchorBounds.bottom + gap
        return IntOffset(x, y.coerceIn(pad, maxY))
    }
} //下拉菜单定位
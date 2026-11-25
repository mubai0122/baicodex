/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:25
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package b.ai.ui.widget
import b.ai.ui.glass.LiquidCard
import b.ai.ui.glass.LocalCardBackdrop
import b.ai.ui.glass.liquidBg
import b.ai.net.api.llm.text

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.AccentBlueSoft
import b.ai.ui.theme.BorderGray
import b.ai.core.cfg.app.Cfg
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.SurfaceWhite
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.composed
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import b.ai.core.media.img.Avatar
import b.ai.ui.theme.DangerRed
import b.ai.ui.theme.DangerRedSoft
import androidx.compose.ui.res.stringResource
import b.ai.R

/** 无原生涟漪的点击：卡片、胶囊、抽屉行一律用它 */
fun Modifier.tap(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
}

/**
 * 按压回弹：按下缩到 96%，松手用低阻尼弹簧弹回。
 * 全站按钮共用这一份，引导页、弹窗、设置页的按钮手感因此完全一致。
 */
@Composable
fun Modifier.pressFx(src: InteractionSource, down: Float = 0.96f): Modifier {
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) down else 1f,
        animationSpec = spring(dampingRatio = 0.42f, stiffness = 900f),
        label = "pressFx"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
} //按压回弹

/** 无原生涟漪的长按：消息气泡用它打开操作菜单 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun Modifier.longPress(onLong: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    combinedClickable(
        interactionSource = src,
        indication = null,
        onClick = {},
        onLongClick = onLong
    )
}

/** 点击 + 长按同一条手势：两个 detector 分开挂会互相抢事件，抽屉里的多选行用这个 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun Modifier.tapLong(onClick: () -> Unit, onLong: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    combinedClickable(
        interactionSource = src,
        indication = null,
        onClick = onClick,
        onLongClick = onLong
    )
}

/** 卡片容器：开了液态玻璃卡片、且这一页登记了背景层，就走真玻璃，否则还是白卡（底色不透明度按设置走） */
@Composable
fun CardBox(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val backdrop = LocalCardBackdrop.current
    //外壳会在「白卡 / 玻璃卡」两种容器之间切（玻璃开关、背景层异步就绪都会翻），
    //用 movableContentOf 把内容搬过去：切换时子组合不重建，卡里编辑到一半的表单与输入态不会丢
    val latest by rememberUpdatedState(content)
    val body = remember { movableContentOf<ColumnScope> { scope -> latest.invoke(scope) } }
    //圆角跟着设置走：0 = 直角，16 是原来的观感，32 接近胶囊；白卡与玻璃卡共用同一个值
    val corner = Cfg.cardCorner.coerceIn(0, 32).dp
    if (Cfg.glassCard && backdrop != null) {
        LiquidCard(backdrop, modifier.fillMaxWidth(), corner = corner) { body(this) }
        return
    }
    val shape = RoundedCornerShape(corner)
    val alpha = Cfg.cardAlpha.coerceIn(0, 100) / 100f
    Column(
        modifier = modifier
            .fillMaxWidth()
            .liquidBg(shape, tint = SurfaceWhite.copy(alpha = alpha))
            .border(1.dp, BorderGray, shape)
            .padding(16.dp)
    ) { body(this) }
} //白卡 / 玻璃卡

/**
 * 分类标题：设置页里每张卡片上方那一行（h2 级别，加粗、比正文明显大一号）。
 * 上间距 18dp 给「标题—卡片」之间留出呼吸位，卡片不会顶到标题线上。
 */
@Composable
fun SectionTitle(text: String, sub: String? = null) {
    Column(Modifier.padding(top = 18.dp, bottom = 8.dp)) {
        Text(
            text,
            fontSize = 17.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.2.sp
        )
        if (sub != null) {
            Spacer(Modifier.height(2.dp))
            Text(sub, fontSize = 11.sp, color = TextSecondary)
        }
    }
} //分类标题（h2）

@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = src,
        modifier = modifier.height(46.dp).pressFx(src),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AccentBlue,
            contentColor = Color.White,
            disabledContainerColor = AccentBlue.copy(alpha = 0.35f),
            disabledContentColor = Color.White
        )
    ) { Text(text, fontSize = 14.sp) }
}

/** 次要按钮：玻璃底 + 描边，材质跟卡片一致（原来用 OutlinedButton，是 Material 的白底描边） */
@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    Box(
        modifier
            .height(42.dp)
            .pressFx(src)
            .liquidBg(RoundedCornerShape(12.dp), veil = 0.22f, gate = 0.dp)
            .border(1.dp, BorderGray, RoundedCornerShape(12.dp))
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 13.sp, color = TextPrimary) }
} //次要按钮

/**
 * 圆形箭头按钮：引导流程的「下一步」用它。
 * 主操作只留一个圆，底部行不再被一条宽按钮占满，小屏上也不挤。
 */
@Composable
fun CircleArrowButton(
    enabled: Boolean = true,
    size: Dp = 52.dp,
    desc: String = "",
    onClick: () -> Unit
) {
    val src = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(size)
            .pressFx(src, down = 0.9f)
            .liquidBg(CircleShape, tint = if (enabled) AccentBlue else AccentBlue.copy(alpha = 0.35f), always = true)
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        IcArrowRight(Color.White, side = size * 0.42f, stroke = 2.2.dp)
    }
} //圆形箭头按钮

@Composable
fun Tag(text: String, fg: Color = AccentBlue, bg: Color = AccentBlueSoft) {
    Text(
        text = text,
        fontSize = 11.sp,
        color = fg,
        modifier = Modifier
            .background(bg, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
fun Field(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    secret: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE
) {
    Column(Modifier.padding(top = 10.dp)) {
        //label 为空时省掉标题行，给「只有一个输入框」的场景用
        if (label.isNotBlank()) {
            Text(label, fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.height(4.dp))
        }
        //输入框也接液态玻璃：原来用 OutlinedTextField，材质跟卡片对不上（白的描边框）
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                //输入框不做玻璃：liquidBg 的 veil 是「乳白/墨黑遮罩」不是不透明度，
                //深色下那就是一层黑（用户看到的就是输入框阴影/发灰），浅色下是一层白。
                //这里直接给不透明的 SurfaceMuted 实底，只保留一条细描边，干净且文字对比稳。
                .background(SurfaceMuted, RoundedCornerShape(12.dp))
                .border(1.dp, BorderGray, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 11.dp),
            textStyle = TextStyle(fontSize = 13.sp, color = TextPrimary),
            singleLine = singleLine,
            minLines = minLines,
            //maxLines 封顶：超出的行在框内滚动，长文本（cookie / token）不会把卡片按钮顶出屏幕
            maxLines = maxLines,
            cursorBrush = SolidColor(AccentBlue),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            decorationBox = { inner ->
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(placeholder, fontSize = 13.sp, color = TextSecondary)
                }
                inner()
            }
        )
    }
} //输入框

/** 单选行：标题 + 说明 + 右侧对勾 */
@Composable
fun ChoiceRow(title: String, desc: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .liquidBg(RoundedCornerShape(12.dp), tint = if (selected) AccentBlueSoft else SurfaceWhite, gate = 0.dp)
            .border(1.dp, if (selected) AccentBlue else BorderGray, RoundedCornerShape(12.dp))
            .tap { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
            if (desc != null) Text(desc, fontSize = 11.sp, color = TextSecondary)
        }
        if (selected) {
            Text("✓", fontSize = 14.sp, color = AccentBlue)
            Spacer(Modifier.size(2.dp))
        }
    }
}

/**紧凑单行输入框：高度压到 44dp，右侧可挂一个方形按钮，配置名用它 */
@Composable
fun FieldBox(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    trailing: (@Composable () -> Unit)? = null
) {
    Column(Modifier.padding(top = 10.dp)) {
        Text(label, fontSize = 12.sp, color = TextSecondary)
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                //同上：紧凑输入框也不做玻璃，避免深色下一层黑遮罩
                .background(SurfaceMuted, RoundedCornerShape(12.dp))
                .border(1.dp, BorderGray, RoundedCornerShape(12.dp))
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(placeholder, fontSize = 13.sp, color = TextSecondary, maxLines = 1)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 13.sp, color = TextPrimary),
                    cursorBrush = SolidColor(AccentBlue),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (trailing != null) {
                Spacer(Modifier.width(6.dp))
                trailing()
            }
        }
    }
} //紧凑单行输入框

/**方形小图标按钮：34dp 圆角方块，用在紧凑输入框右侧 */
@Composable
fun SquareIconBox(
    icon: ImageVector,
    desc: String = "",
    onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .liquidBg(RoundedCornerShape(9.dp), tint = AccentBlueSoft)
            .tap(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = desc, tint = AccentBlue, modifier = Modifier.size(17.dp))
    }
} //方形小图标按钮

/** 圆形小图标按钮：刷新 / 重跑这类轻动作放标题行右侧，不占一整行 */
@Composable
fun CircleIconBox(
    icon: ImageVector,
    desc: String = "",
    size: Dp = 30.dp,
    onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(size)
            .liquidBg(CircleShape, tint = AccentBlueSoft)
            .tap(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = desc, tint = AccentBlue, modifier = Modifier.size(size * 0.5f))
    }
} //圆形小图标按钮

/**头像圆图：有图片就画图，没有就画默认人形；size 是直径 */
@Composable
fun AvatarView(path: String, size: Dp, tint: Color = AccentBlue) {
    val bmp = remember(path) { Avatar.bitmap(path) }
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(AccentBlueSoft),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(size * 0.62f)
            )
        }
    }
} //头像圆图

/**头像设置一行：预览 + 说明 + 选图 / 清除；选完由调用方决定存哪 */
@Composable
fun AvatarPick(title: String, desc: String, path: String, onPicked: (String) -> Unit) {
    val ctx = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val saved = Avatar.importFrom(ctx, uri) ?: return@rememberLauncherForActivityResult
        onPicked(saved)
    }
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AvatarView(path, 44.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 13.sp, color = TextPrimary)
                Text(desc, fontSize = 10.sp, color = TextSecondary)
            }
            Text(
                stringResource(R.string.cm_pick_image),
                fontSize = 11.sp,
                color = AccentBlue,
                modifier = Modifier
                    .liquidBg(RoundedCornerShape(8.dp), tint = AccentBlueSoft)
                    .tap { pick.launch("image/*") }
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            )
        }
        if (path.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.cm_clear_avatar),
                fontSize = 11.sp,
                color = DangerRed,
                modifier = Modifier
                    .background(DangerRedSoft, RoundedCornerShape(8.dp))
                    .tap { onPicked("") }
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            )
        }
    }
} //头像设置一行
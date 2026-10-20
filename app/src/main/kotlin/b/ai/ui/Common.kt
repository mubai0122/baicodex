package b.ai.ui

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
import androidx.compose.runtime.remember
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
import b.ai.ui.theme.SurfaceWhite
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import b.ai.core.Avatar
import b.ai.ui.theme.DangerRed
import b.ai.ui.theme.DangerRedSoft
import androidx.compose.ui.res.stringResource
import b.ai.R

/** 无原生涟漪的点击：卡片、胶囊、抽屉行一律用它 */
fun Modifier.tap(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
}

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

/** 白色圆角卡片容器 */
@Composable
fun CardBox(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceWhite, RoundedCornerShape(16.dp))
            .border(1.dp, BorderGray, RoundedCornerShape(16.dp))
            .padding(16.dp),
        content = content
    )
}

@Composable
fun SectionTitle(text: String, sub: String? = null) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(text, fontSize = 13.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
        if (sub != null) Text(sub, fontSize = 11.sp, color = TextSecondary)
    }
}

@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AccentBlue,
            contentColor = Color.White,
            disabledContainerColor = AccentBlue.copy(alpha = 0.35f),
            disabledContentColor = Color.White
        )
    ) { Text(text, fontSize = 14.sp) }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(42.dp),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGray)
    ) { Text(text, fontSize = 13.sp, color = TextPrimary) }
}

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
    minLines: Int = 1
) {
    Column(Modifier.padding(top = 10.dp)) {
        Text(label, fontSize = 12.sp, color = TextSecondary)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, fontSize = 13.sp, color = TextSecondary) },
            singleLine = singleLine,
            minLines = minLines,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
            shape = RoundedCornerShape(12.dp),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None
        )
    }
}

/** 单选行：标题 + 说明 + 右侧对勾 */
@Composable
fun ChoiceRow(title: String, desc: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(if (selected) AccentBlueSoft else SurfaceWhite, RoundedCornerShape(12.dp))
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
                .background(SurfaceWhite, RoundedCornerShape(12.dp))
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
            .clip(RoundedCornerShape(9.dp))
            .background(AccentBlueSoft)
            .tap(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = desc, tint = AccentBlue, modifier = Modifier.size(17.dp))
    }
} //方形小图标按钮

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
                    .background(AccentBlueSoft, RoundedCornerShape(8.dp))
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

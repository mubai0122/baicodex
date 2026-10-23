package b.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.R
import b.ai.core.Noti
import b.ai.core.Usage
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary

/** 注入类权限的提示开关：值是 noti / usage，挂载点在设置页根弹窗层 */
object PermAsk {
    val kind = mutableStateOf<String?>(null)
}

/** 开关一打开就弹这个：讲清要什么权限、为什么，然后直接把用户送到系统页 */
@Composable
fun PermAskDialog(kind: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val noti = kind == "noti"
    ScrimCard(onDismiss = onClose) {
        Text(
            stringResource(if (noti) R.string.se_332 else R.string.se_334),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(if (noti) R.string.se_333 else R.string.se_335),
            fontSize = 12.sp,
            color = TextSecondary
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            GhostButton(stringResource(R.string.up_later)) { onClose() }
            Spacer(Modifier.width(10.dp))
            PrimaryButton(stringResource(R.string.se_336), enabled = true) {
                if (noti) Noti.openPerm(ctx) else Usage.openPerm(ctx)
                onClose()
            }
        }
    }
} //注入权限提示
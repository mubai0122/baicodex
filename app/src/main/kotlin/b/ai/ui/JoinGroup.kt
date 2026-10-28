package b.ai.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.R
import b.ai.core.Cfg
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.AccentBlueSoft
import b.ai.ui.theme.DangerRed
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary

/** QQ 群号与分享链接：分享链接优先直接交给 QQ，避免先起一次浏览器再跳走 */
object JoinGroup {
    const val QUN = "1105803672"

    const val SHARE = "https://qun.qq.com/universal-share/share?ac=1&authKey=HwLsT%2FZytsEIxPeTazvBA27cYI4vDHGhY%2BIVPoG9%2B4lx%2FUrTGnslN%2BixG5spNNBp" +
        "&busi_data=eyJncm91cENvZGUiOiIxMTA1ODAzNjcyIiwidG9rZW4iOiJKcVlNWENpV2tvRGx0aDRaYm1IRkJPRmFPUjdtdWpOLzBLWkQ2RnFxa0J5bVZxWTJiM0Z0RjFTQzFidExaQTRxIiwidWluIjoiMzc2MzkwMzA5OCJ9" +
        "&data=Z_fT5q1hFTOddgI4UBkXY2WNDHtl703cDkG3dmtFH3_mdZ3m22UVJlhojMxDiOYYr6dpoEq8IM7FzYJVcdggTg&svctype=4&tempid=h5_group_info"

    private const val QQ_PKG = "com.tencent.mobileqq"

    /** 拉起 QQ：先让 QQ 直接吃分享链接，再退群卡片 scheme，都不行才交给浏览器 */
    fun open(ctx: Context): Boolean {
        val share = Uri.parse(SHARE)
        val tries = listOf(
            Intent(Intent.ACTION_VIEW, share).setPackage(QQ_PKG),
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("mqqapi://card/show_pslcard?src_type=internal&version=1&uin=$QUN&card_type=group&source=qrcode")
            ).setPackage(QQ_PKG),
            Intent(Intent.ACTION_VIEW, Uri.parse("mqqapi://card/show_pslcard?src_type=internal&version=1&uin=$QUN&card_type=group&source=qrcode")),
            Intent(Intent.ACTION_VIEW, share)
        )
        for (i in tries) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val ok = runCatching { ctx.packageManager.queryIntentActivities(i, 0).isNotEmpty() }.getOrDefault(false)
            if (!ok) continue
            if (runCatching { ctx.startActivity(i) }.isSuccess) return true
        }
        return false
    } //拉起 QQ 群

    /** 复制群号：跳转失败时的手动兜底 */
    fun copy(ctx: Context) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText("bai", QUN))
    } //复制群号
} //QQ 群入口

/** 「加入我们」卡片：勾了「以后永不提醒」再关，就再也不弹；按钮文案跟着勾选变 */
@Composable
fun JoinGroupDialog(onClose: (never: Boolean) -> Unit) {
    val ctx = LocalContext.current
    //已经跳转过 QQ 群的人，默认勾上「我已加入，下次不再提示」
    var never by remember { mutableStateOf(Cfg.joinJoined) }
    var tip by remember { mutableStateOf("") }
    //返回键按「下次再说」处理，别让卡片卡住根弹窗层
    BackHandler { onClose(never) }
    ScrimCard(onDismiss = { onClose(never) }) {
        Text(
            stringResource(R.string.qq_title),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.qq_sub),
            fontSize = 12.sp,
            color = TextSecondary,
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .liquidBg(RoundedCornerShape(12.dp), tint = SurfaceMuted)
                .padding(start = 12.dp, end = 8.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.qq_label), fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.width(8.dp))
            Text(
                JoinGroup.QUN,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )
            Text(stringResource(R.string.qq_hint), fontSize = 9.sp, color = DangerRed)
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .liquidBg(RoundedCornerShape(8.dp), tint = AccentBlueSoft)
                    .tap {
                        JoinGroup.copy(ctx)
                        tip = ctx.getString(R.string.qq_copied)
                    }
                    .padding(6.dp)
            ) { IcCopy(AccentBlue, 13.dp) }
        }
        if (tip.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(tip, fontSize = 10.sp, color = TextSecondary)
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().tap { never = !never },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(16.dp)
                    .liquidBg(RoundedCornerShape(5.dp), tint = if (never) AccentBlue else SurfaceMuted),
                contentAlignment = Alignment.Center
            ) { if (never) IcTick(Color.White, 10.dp) }
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(if (Cfg.joinJoined) R.string.qq_joined else R.string.qq_noremind),
                fontSize = 11.sp,
                color = TextSecondary
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(if (never) R.string.qq_never else R.string.qq_later),
                fontSize = 12.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .liquidBg(RoundedCornerShape(10.dp), tint = SurfaceMuted)
                    .tap { onClose(never) }
                    .padding(vertical = 11.dp)
            )
            Text(
                stringResource(R.string.qq_join),
                fontSize = 12.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .liquidBg(RoundedCornerShape(10.dp), tint = AccentBlue)
                    .tap {
                        //跳不动就把群号复制好并留在卡片上，让用户看到提示再手动进群
                        if (JoinGroup.open(ctx)) {
                            //记一笔：下次弹卡片时勾选项换成「我已加入」
                            Cfg.joinJoined = true
                            onClose(never)
                        } else {
                            JoinGroup.copy(ctx)
                            tip = ctx.getString(R.string.qq_fail)
                        }
                    }
                    .padding(vertical = 11.dp)
            )
        }
    } //加入我们卡片
} //群卡片
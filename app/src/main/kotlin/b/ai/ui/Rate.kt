package b.ai.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.R
import b.ai.core.Cfg
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.DangerRed
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary

/** 评价调研：累计前台用满 2 小时问一次要不要去点 star，只弹一次，设置里可以彻底关掉 */
object Rate {
    /** 触发阈值：2 小时 */
    const val NEED_SEC = 7200

    /** 置 true 由根弹窗层渲染卡片，关掉后回到 false */
    var ask by mutableStateOf(false)

    /** 手动点「去 GitHub 点个 Star」打开时为 true：换成问句文案，且不显示「不再提示」勾选 */
    var manual by mutableStateOf(false)

    /** 前台每次回来调一次：已经弹过、用户关掉了、时长不够，三种情况都不置位 */
    fun check() {
        if (ask || Cfg.noRate || Cfg.starShown) return
        if (Cfg.usedSec >= NEED_SEC) ask = true
    } //判定是否该弹

    /** 设置页手动点开：不受时长与 starShown 限制，noRate 也不拦（用户自己点的） */
    fun askManual() {
        manual = true
        ask = true
    } //手动打开
} //评价调研

/** 星级邀请卡片：带 GitHub 标，只问一次，勾选后永久不再出现 */
@Composable
fun StarDialog(manual: Boolean = false, onClose: (never: Boolean) -> Unit) {
    val ctx = LocalContext.current
    var never by remember { mutableStateOf(false) }
    BackHandler { onClose(never) }
    ScrimCard(onDismiss = { onClose(never) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(SurfaceMuted, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painterResource(R.drawable.ic_github),
                    contentDescription = "GitHub",
                    modifier = Modifier.size(24.dp),
                    colorFilter = ColorFilter.tint(TextPrimary)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(if (manual) R.string.rate_star_title else R.string.rate_star_auto),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (manual) stringResource(R.string.rate_star_body)
            else stringResource(R.string.star_body, Cfg.usedSec / 3600),
            fontSize = 12.sp,
            color = TextSecondary,
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(12.dp))
        if (!manual) Row(
            Modifier.fillMaxWidth().tap { never = !never },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(16.dp)
                    .background(if (never) DangerRed else SurfaceMuted, RoundedCornerShape(5.dp)),
                contentAlignment = Alignment.Center
            ) { if (never) IcTick(Color.White, 10.dp) }
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.star_off), fontSize = 11.sp, color = TextSecondary)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.star_later),
                fontSize = 12.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .background(SurfaceMuted, RoundedCornerShape(10.dp))
                    .tap { onClose(never) }
                    .padding(vertical = 11.dp)
            )
            Text(
                stringResource(R.string.star_go),
                fontSize = 12.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .background(AccentBlue, RoundedCornerShape(10.dp))
                    .tap {
                        openUrl(ctx, GITHUB_URL)
                        onClose(never)
                    }
                    .padding(vertical = 11.dp)
            )
        }
    } //评价调研卡片
} //星级邀请
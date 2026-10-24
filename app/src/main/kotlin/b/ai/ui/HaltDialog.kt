package b.ai.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.R
import b.ai.ui.theme.DangerRed
import b.ai.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/** 停机提示：服务端 isuse=false 时唯一能看到的弹窗，点外部、返回键都关不掉，倒计时走完自动退 */
@Composable
fun HaltDialog(onExit: () -> Unit) {
    var left by remember { mutableStateOf(3) }
    LaunchedEffect(Unit) {
        while (left > 0) {
            delay(1000)
            left -= 1
        }
        onExit()
    }
    BackHandler { }
    ScrimCard {
        Text(
            stringResource(R.string.halt_title),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = DangerRed
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.halt_body), fontSize = 12.sp, color = TextSecondary, lineHeight = 18.sp)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.halt_left, left), fontSize = 11.sp, color = TextSecondary)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                stringResource(R.string.halt_exit),
                fontSize = 13.sp,
                color = Color.White,
                modifier = Modifier
                    .background(DangerRed, RoundedCornerShape(10.dp))
                    .tap { onExit() }
                    .padding(horizontal = 18.dp, vertical = 9.dp)
            )
        }
    } //停机提示卡片
} //停机弹窗
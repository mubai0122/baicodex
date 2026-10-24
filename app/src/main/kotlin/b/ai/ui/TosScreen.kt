package b.ai.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.R
import b.ai.core.Cfg
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.BgGray
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 用户协议：正文放 assets/tos，中文一份、英文一份，日法繁沿用英文版 */
object Tos {
    /** 协议版本号：改正文就 +1，老用户会重新看到同意页 */
    const val VERSION = 1

    /** 按当前界面语言挑正文：中文（简/繁）走 zh，其余走 en */
    fun load(): String = runCatching {
        val name = if (Cfg.lang.startsWith("zh")) "tos/zh.txt" else "tos/en.txt"
        b.ai.App.self.assets.open(name).bufferedReader().use { it.readText() }
    }.getOrDefault("") //读协议正文
} //用户协议

/** 协议正文 + 同意勾选：引导最后一步和「已激活用户补弹」共用同一块 */
@Composable
fun TosBody(
    checked: Boolean,
    onCheck: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        text = withContext(Dispatchers.IO) { Tos.load() }
    }
    val loading = stringResource(R.string.tos_loading)
    Column(modifier) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(SurfaceMuted, RoundedCornerShape(14.dp))
                .padding(14.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                if (text.isBlank()) loading else text,
                fontSize = 12.sp,
                color = TextPrimary,
                lineHeight = 19.sp
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().tap { onCheck(!checked) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(18.dp)
                    .background(if (checked) AccentBlue else SurfaceMuted, RoundedCornerShape(5.dp)),
                contentAlignment = Alignment.Center
            ) { if (checked) IcTick(Color.White, 11.dp) }
            Spacer(Modifier.width(9.dp))
            Text(stringResource(R.string.tos_read), fontSize = 12.sp, color = TextPrimary)
        }
    }
}

/** 已激活用户升级后单独补一次协议：全屏，没有返回，勾选才能进主界面 */
@Composable
fun TosScreen(onAgree: () -> Unit) {
    var checked by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .background(BgGray)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(18.dp))
        Text(
            stringResource(R.string.tos_title),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            stringResource(R.string.tos_ver, Tos.VERSION),
            fontSize = 11.sp,
            color = TextSecondary
        )
        Spacer(Modifier.height(12.dp))
        TosBody(checked, { checked = it }, Modifier.weight(1f))
        Spacer(Modifier.height(14.dp))
        PrimaryButton(stringResource(R.string.tos_agree), enabled = checked) { onAgree() }
        Spacer(Modifier.height(18.dp))
    }
} //协议页
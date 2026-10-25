package b.ai.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.R
import b.ai.core.Cfg
import b.ai.core.PetSay
import b.ai.core.PetSound
import b.ai.ui.theme.BorderGray
import b.ai.ui.theme.SurfaceWhite
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 首次进入的桌宠引导：图 + 两句说明 + 去设置，看过写 petSeen */
@Composable
fun PetIntroDialog(onClose: () -> Unit, onOpen: () -> Unit = {}) {
    ScrimCard(onDismiss = onClose) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            val t = rememberInfiniteTransition(label = "petIntroFloat")
            val dy by t.animateFloat(
                initialValue = 0f,
                targetValue = -6f,
                animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "petIntroDy"
            )
            Image(
                painterResource(R.drawable.whale),
                contentDescription = null,
                modifier = Modifier.size(96.dp).graphicsLayer { translationY = dy }
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.pet_intro_t),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.pet_intro_body), fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                GhostButton(stringResource(R.string.pet_intro_later)) { onClose() }
                Spacer(Modifier.width(10.dp))
                PrimaryButton(stringResource(R.string.pet_intro_on)) {
                    Cfg.petOn = true
                    onOpen()
                }
            }
        }
    }
} //桌宠引导

/** 应用内那只桌宠：只在「仅应用内展示」时出现，位置固定，弹跳与缓动都做在图片自己身上 */
@Composable
fun PetInApp(modifier: Modifier = Modifier) {
    if (!Cfg.petOn || Cfg.petGlobal) return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val t = rememberInfiniteTransition(label = "petFloat")
    val ms = if (Cfg.petFloatMs < 400) 400 else Cfg.petFloatMs
    val amp = -(Cfg.petFloatAmp.toFloat() * 3f)
    val dy by t.animateFloat(
        initialValue = 0f,
        targetValue = if (Cfg.petFloat) amp else 0f,
        animationSpec = infiniteRepeatable(tween(ms, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "petDy"
    )
    var say by remember { mutableStateOf("") }
    val jy = remember { Animatable(0f) }

    fun qBounce() {
        PetSound.boing(ctx, if (Cfg.petSound) Cfg.petSoundVol / 100f else 0f)
        if (!Cfg.petBounce) return
        scope.launch {
            jy.snapTo(0f)
            jy.animateTo(-Cfg.petBouncePct.toFloat() * 3f, tween(110, easing = FastOutSlowInEasing))
            jy.animateTo(0f, spring(dampingRatio = 0.42f, stiffness = 300f))
        }
    }

    val px = Cfg.petSize.dp
    Column(modifier, horizontalAlignment = Alignment.End) {
        if (say.isNotBlank()) {
            Text(
                say,
                fontSize = Cfg.petFont.sp,
                fontWeight = if (Cfg.petFontBold) FontWeight.Bold else FontWeight.Normal,
                color = if (Cfg.petBubbleDark) Color.White else TextPrimary,
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .widthIn(max = Cfg.petBubbleW.dp)
                    .background(
                        if (Cfg.petBubbleDark) Color(0xF21B1F26) else SurfaceWhite,
                        RoundedCornerShape(Cfg.petBubbleR.dp)
                    )
                    .border(
                        if (Cfg.petBubbleDark) 0.dp else 1.dp,
                        if (Cfg.petBubbleDark) Color.Transparent else BorderGray,
                        RoundedCornerShape(Cfg.petBubbleR.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
        Image(
            painterResource(R.drawable.whale),
            contentDescription = stringResource(R.string.pet_t),
            modifier = Modifier
                .size(px)
                .graphicsLayer {
                    translationY = dy + jy.value
                    alpha = Cfg.petAlpha / 100f
                    scaleX = if (Cfg.petFlip) -1f else 1f
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            qBounce()
                            if (Cfg.petTalk) say = PetSay.next(ctx)
                        },
                        onDoubleTap = {
                            qBounce()
                            if (Cfg.petDblBal) PetSay.balance(ctx) { say = it }
                            else if (Cfg.petTalk) say = PetSay.next(ctx)
                        }
                    )
                }
        )
    }
    LaunchedEffect(say) {
        if (say.isNotBlank()) {
            delay(Cfg.petBubbleMs.toLong())
            say = ""
        }
    }
} //应用内桌宠

/** 桌宠默认全局展示，但没悬浮窗权限时先讲清再要权限 */
@Composable
fun PetPermAsk(onClose: () -> Unit, onGrant: () -> Unit, onOpen: () -> Unit) {
    ScrimCard(onDismiss = onClose) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.pet_t),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.pet_need_perm), fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                GhostButton(stringResource(R.string.pet_intro_later)) { onClose() }
                Spacer(Modifier.width(8.dp))
                GhostButton(stringResource(R.string.pet_perm_go)) { onOpen() }
                Spacer(Modifier.width(8.dp))
                PrimaryButton(stringResource(R.string.pet_perm_auto)) { onGrant() }
            }
        }
    }
} //桌宠权限弹窗

package b.ai.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.agent.Agent
import b.ai.core.Cfg
import b.ai.exec.FChat
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.DangerRed
import b.ai.ui.theme.DangerRedSoft
import b.ai.ui.theme.OkGreen
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.SurfaceWhite
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary

/**
 * 悬浮聊天窗的内容：标题栏（拖它移动整窗）+ 消息（Markdown）+ 输入区 + 预览调节。
 * 只读 Agent 的公共状态，跟主界面共用同一个会话，不存在第二套聊天逻辑。
 */
@Composable
fun FChatUi(onClose: () -> Unit) {
    val list = rememberLazyListState()
    //只留能看的：纯工具调用产生的空助手消息不占屏幕
    val shown = remember(Agent.messages) {
        Agent.messages.filter { it.role == "user" || (it.role == "assistant" && it.content.isNotBlank()) }
    }
    var draft by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    var tune by remember { mutableStateOf(false) }
    //宽度 / 高度 / 透明度一变就重排窗口，滑杆就是实时的
    LaunchedEffect(Cfg.fcW, Cfg.fcH, Cfg.fcAlpha) { FChat.apply() }
    LaunchedEffect(shown.size) { if (shown.isNotEmpty()) list.animateScrollToItem(shown.lastIndex) }
    val fs = Cfg.fcFont.coerceIn(10, 22).sp

    Column(Modifier.fillMaxSize().liquidBg(RoundedCornerShape(18.dp))) {
        //标题栏：按住这里拖整窗，别把拖动挂到列表上，否则滚动会被抢
        Row(
            Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, d ->
                            change.consume()
                            FChat.moveBy(d.x, d.y)
                        },
                        onDragEnd = { FChat.savePos() },
                        onDragCancel = { FChat.savePos() }
                    )
                }
                .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(7.dp).liquidBg(CircleShape, tint = if (Agent.busy) AccentBlue else OkGreen))
            Spacer(Modifier.width(6.dp))
            Text("Baicodex", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            Text(
                if (tune) "收起" else "调节",
                fontSize = 11.sp,
                color = AccentBlue,
                modifier = Modifier.tap { tune = !tune }.padding(horizontal = 6.dp, vertical = 2.dp)
            )
            Box(Modifier.size(22.dp).tap { onClose() }, contentAlignment = Alignment.Center) {
                IcClose(TextSecondary, side = 11.dp)
            }
        }
        if (tune) Tune()
        //工具确认与 ask_user 都挂在主 Activity 的弹窗层，悬浮窗里画不出来，只能给个回 App 的入口
        if (Agent.pending != null || Agent.ask != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .background(DangerRedSoft, RoundedCornerShape(9.dp))
                    .tap {
                        runCatching {
                            ctx.startActivity(
                                Intent(ctx, b.ai.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("有工具等你确认：点这里回 App 处理", fontSize = 10.sp, color = DangerRed)
            }
        }

        if (shown.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("说点什么，回复按 Markdown 渲染", fontSize = 11.sp, color = TextSecondary)
            }
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                state = list,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(shown) { m ->
                    if (m.role == "user") {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                            Text(
                                m.content,
                                fontSize = fs,
                                color = Color.White,
                                modifier = Modifier
                                    .liquidBg(RoundedCornerShape(10.dp), tint = AccentBlue)
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    } else {
                        MdText(m.content, color = if (m.isError) DangerRed else TextPrimary, size = fs)
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .liquidBg(RoundedCornerShape(12.dp), tint = SurfaceMuted)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                if (draft.isEmpty()) Text("问点什么…", fontSize = 12.sp, color = TextSecondary)
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    textStyle = TextStyle(fontSize = 12.sp, color = TextPrimary),
                    maxLines = 4,
                    cursorBrush = SolidColor(AccentBlue),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.width(8.dp))
            val canSend = draft.isNotBlank()
            Box(
                Modifier
                    .size(34.dp)
                    .background(if (Agent.busy) DangerRed else if (canSend) AccentBlue else SurfaceMuted, CircleShape)
                    .tap {
                        if (Agent.busy) {
                            Agent.stop()
                        } else if (canSend) {
                            val t = draft.trim()
                            draft = ""
                            Agent.send(t)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (Agent.busy) IcStop4(Color.White, side = 12.dp)
                else IcSend(if (canSend) Color.White else TextSecondary, side = 14.dp)
            }
        }
    }
} //悬浮聊天窗

/** 预览调节：宽度 / 高度 / 透明度 / 字号四根滑杆，拖动即时改窗口 */
@Composable
private fun Tune() {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
        TuneRow("宽度", Cfg.fcW, 220f..480f) { Cfg.fcW = it.toInt() }
        TuneRow("高度", Cfg.fcH, 260f..900f) { Cfg.fcH = it.toInt() }
        TuneRow("透明度", Cfg.fcAlpha, 40f..100f) { Cfg.fcAlpha = it.toInt() }
        TuneRow("字号", Cfg.fcFont, 10f..22f) { Cfg.fcFont = it.toInt() }
    }
} //预览调节面板

@Composable
private fun TuneRow(label: String, v: Int, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 10.sp, color = TextSecondary, modifier = Modifier.width(38.dp))
        Slider(
            value = v.toFloat().coerceIn(range),
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.weight(1f).height(26.dp)
        )
        Text("$v", fontSize = 10.sp, color = TextSecondary, modifier = Modifier.width(34.dp))
    }
} //一行滑杆
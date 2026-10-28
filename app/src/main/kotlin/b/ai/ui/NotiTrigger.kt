package b.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.R
import b.ai.core.Noti
import b.ai.core.NoteTrigger
import b.ai.core.Triggers
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.AccentBlueSoft
import b.ai.ui.theme.DangerRed
import b.ai.ui.theme.DangerRedSoft
import b.ai.ui.theme.OkGreen
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TriggerDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    var app by remember { mutableStateOf("") }
    var kw by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    val rules = Triggers.list
    val allow = Noti.allow(ctx)
    val fmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.CHINA) }

    ScrimCard(onDismiss = onClose) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.ntg_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.tk_close),
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier
                    .liquidBg(RoundedCornerShape(8.dp), tint = SurfaceMuted)
                    .tap { onClose() }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp)
                .verticalScroll(glassScrollState())
        ) {
            //没有通知使用权就一条通知都读不到，先把人送去系统页
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.ntg_perm), fontSize = 11.sp, color = TextPrimary)
                Spacer(Modifier.width(6.dp))
                Text(
                    if (allow) stringResource(R.string.tk_ready) else stringResource(R.string.tk_off),
                    fontSize = 10.sp,
                    color = if (allow) OkGreen else DangerRed
                )
                Spacer(Modifier.weight(1f))
                if (!allow) {
                    Text(
                        stringResource(R.string.ntg_open),
                        fontSize = 10.sp,
                        color = AccentBlue,
                        modifier = Modifier
                            .liquidBg(RoundedCornerShape(8.dp), tint = AccentBlueSoft)
                            .tap { Noti.openPerm(ctx) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Field(
                label = stringResource(R.string.ntg_app),
                value = app,
                onValueChange = { app = it },
                placeholder = stringResource(R.string.ntg_app_hint)
            )
            Field(
                label = stringResource(R.string.ntg_kw),
                value = kw,
                onValueChange = { kw = it },
                placeholder = stringResource(R.string.ntg_kw_hint)
            )
            Field(
                label = stringResource(R.string.ntg_prompt),
                value = prompt,
                onValueChange = { prompt = it },
                placeholder = stringResource(R.string.ntg_prompt_hint),
                singleLine = false,
                minLines = 3
            )
            if (err.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(err, fontSize = 11.sp, color = DangerRed)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.ntg_save),
                fontSize = 13.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidBg(RoundedCornerShape(10.dp), tint = AccentBlue)
                    .tap {
                        if (prompt.isBlank()) {
                            err = ctx.getString(R.string.ntg_err_prompt)
                        } else {
                            err = ""
                            Triggers.add(app, kw, prompt)
                            app = ""
                            kw = ""
                            prompt = ""
                        }
                    }
                    .padding(vertical = 10.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.ntg_list),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary
            )
            if (rules.isEmpty()) {
                Text(
                    stringResource(R.string.ntg_empty),
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            } else {
                val any = stringResource(R.string.ntg_any)
                val all = stringResource(R.string.ntg_allkw)
                val never = stringResource(R.string.ntg_never)
                rules.forEach { t -> TriggerRow(t, any, all, never, fmt) }
            }
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.ntg_note), fontSize = 10.sp, color = TextSecondary)
        }
    }
} //通知触发面板

@Composable
private fun TriggerRow(t: NoteTrigger, any: String, all: String, never: String, fmt: SimpleDateFormat) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .liquidBg(RoundedCornerShape(10.dp), tint = SurfaceMuted)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                t.app.ifBlank { any } + " · " + t.keyword.ifBlank { all },
                fontSize = 12.sp,
                color = if (t.on) TextPrimary else TextSecondary,
                maxLines = 1
            )
            Text(t.prompt.replace("\n", " ").take(46), fontSize = 10.sp, color = TextSecondary, maxLines = 1)
            Text(
                if (t.lastAt > 0) stringResource(R.string.ntg_last, fmt.format(Date(t.lastAt))) else never,
                fontSize = 9.sp,
                color = TextSecondary
            )
        }
        Switch(
            checked = t.on,
            onCheckedChange = { on -> Triggers.toggle(t.id, on) },
            colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue),
            modifier = Modifier.height(28.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(R.string.ntg_del),
            fontSize = 11.sp,
            color = DangerRed,
            modifier = Modifier
                .background(DangerRedSoft, RoundedCornerShape(8.dp))
                .tap { Triggers.remove(t.id) }
                .padding(horizontal = 8.dp, vertical = 5.dp)
        )
    }
} //单条触发规则
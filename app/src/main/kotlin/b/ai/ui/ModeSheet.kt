package b.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.R
import b.ai.core.AgentMode
import b.ai.core.Cfg
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape

/** 运行模式选择面板：一档一行，带一句说明，点中即存盘并关面板 */
@Composable
internal fun ModeSheet(onClose: () -> Unit) {
    ScrimCard(onDismiss = onClose) {
        Text(stringResource(R.string.mode_title), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
        ) {
            AgentMode.entries.forEach { m ->
                val on = Cfg.agentMode == m
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .liquidBg(RoundedCornerShape(12.dp)
                        , tint = if (on) b.ai.ui.theme.AccentBlueSoft else SurfaceMuted)
                        .tap {
                            Cfg.agentMode = m
                            onClose()
                        }
                        .padding(horizontal = 11.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(m.labelRes),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (on) AccentBlue else TextPrimary
                        )
                        Text(
                            stringResource(m.descRes),
                            fontSize = 10.5.sp,
                            color = TextSecondary
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    if (on) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = AccentBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            GhostButton(stringResource(R.string.se_142), modifier = Modifier.height(36.dp), onClick = onClose)
        }
    }
} //运行模式面板
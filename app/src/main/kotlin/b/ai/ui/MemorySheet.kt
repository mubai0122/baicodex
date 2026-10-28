package b.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.R
import b.ai.core.Memory
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.AccentBlueSoft
import b.ai.ui.theme.DangerRed
import b.ai.ui.theme.DangerRedSoft
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 永久记忆面板：一条一行，右侧删除；不删就一直随系统提示发给 AI */
@Composable
fun MemorySheet(onClose: () -> Unit) {
    val rows = Memory.rows
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }
    //每次打开重读一遍库，别拿上次的快照糊弄
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { Memory.reload() } }
    ScrimCard(onDismiss = onClose) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.mem_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(Modifier.width(6.dp))
            Text("${rows.size}", fontSize = 11.sp, color = TextSecondary)
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.ss_close),
                fontSize = 12.sp,
                color = AccentBlue,
                modifier = Modifier
                    .liquidBg(RoundedCornerShape(9.dp), tint = AccentBlueSoft)
                    .tap { onClose() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.mem_hint), fontSize = 10.sp, color = TextSecondary)
        Spacer(Modifier.height(8.dp))
        if (rows.isEmpty()) {
            Text(
                stringResource(R.string.mem_empty),
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.padding(vertical = 10.dp)
            )
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 430.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rows.forEach { r ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .liquidBg(RoundedCornerShape(10.dp), tint = SurfaceMuted)
                            .padding(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            r.text,
                            fontSize = 11.sp,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.mem_del),
                            fontSize = 10.sp,
                            color = AccentBlue,
                            modifier = Modifier
                                .liquidBg(RoundedCornerShape(8.dp), tint = AccentBlueSoft)
                                .tap { scope.launch { withContext(Dispatchers.IO) { Memory.delete(r.id) } } }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (confirmClear) stringResource(R.string.mem_clear_ok) else stringResource(R.string.mem_clear),
                fontSize = 10.sp,
                color = DangerRed,
                modifier = Modifier
                    .background(DangerRedSoft, RoundedCornerShape(8.dp))
                    .tap {
                        if (confirmClear) {
                            confirmClear = false
                            scope.launch { withContext(Dispatchers.IO) { Memory.clear() } }
                        } else {
                            confirmClear = true
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
} //永久记忆面板
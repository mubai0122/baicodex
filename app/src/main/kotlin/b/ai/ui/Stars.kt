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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.R
import b.ai.core.Repo
import b.ai.core.StarHit
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.AccentBlueSoft
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun starTime(t: Long): String =
    if (t <= 0L) "" else SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(t))

/** 收藏夹：跨会话列出收藏过的消息，点一行跳回原会话，右侧单独取消收藏 */
@Composable
fun StarSheet(onClose: () -> Unit, onOpen: (String) -> Unit) {
    var list by remember { mutableStateOf<List<StarHit>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            list = withContext(Dispatchers.IO) { Repo.starred() }
            loaded = true
        }
    }

    LaunchedEffect(Unit) { reload() }

    ScrimCard(onDismiss = onClose) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.star_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(Modifier.width(6.dp))
            Text("${list.size}", fontSize = 11.sp, color = TextSecondary)
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
        if (loaded && list.isEmpty()) {
            Text(stringResource(R.string.star_empty), fontSize = 11.sp, color = TextSecondary)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 430.dp)
                .verticalScroll(glassScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            list.forEach { hit ->
                StarRow(
                    hit = hit,
                    onOpen = { onOpen(hit.sessionId) },
                    onUnstar = {
                        scope.launch {
                            withContext(Dispatchers.IO) { Repo.setStarred(hit.msg.id, false) }
                            reload()
                        }
                    }
                )
            }
        }
    }
} //收藏夹面板

@Composable
private fun StarRow(hit: StarHit, onOpen: () -> Unit, onUnstar: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .liquidBg(RoundedCornerShape(10.dp), tint = SurfaceMuted)
            .tap { onOpen() }
            .padding(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (hit.title.isBlank()) stringResource(R.string.star_untitled) else hit.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                hit.msg.content.take(160),
                fontSize = 11.sp,
                color = TextSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Text(starTime(hit.at), fontSize = 10.sp, color = TextSecondary)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.star_unstar),
            fontSize = 10.sp,
            color = AccentBlue,
            modifier = Modifier
                .liquidBg(RoundedCornerShape(8.dp), tint = AccentBlueSoft)
                .tap { onUnstar() }
                .padding(horizontal = 8.dp, vertical = 5.dp)
        )
    }
} //收藏条目
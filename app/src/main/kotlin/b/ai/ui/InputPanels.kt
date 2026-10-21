package b.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.core.Cfg
import b.ai.core.Profiles
import b.ai.core.Skills
import b.ai.core.Mcp
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.AccentBlueSoft
import b.ai.ui.theme.BorderGray
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.SurfaceWhite
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import b.ai.R

/** 输入区上方的内联面板外壳：白卡 + 阴影，高度封顶可滚动，不再是全屏弹窗 */
@Composable
private fun SheetShell(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 6.dp)
            .shadow(10.dp, RoundedCornerShape(16.dp))
            .background(SurfaceWhite, RoundedCornerShape(16.dp))
            .border(1.dp, BorderGray, RoundedCornerShape(16.dp))
    ) {
        Column(
            Modifier
                .heightIn(max = 178.dp)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            content = content
        )
    }
} //内联面板外壳

@Composable
private fun SheetHead(title: String, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
        Spacer(Modifier.weight(1f))
        androidx.compose.foundation.layout.Box(
            Modifier.size(22.dp).background(SurfaceMuted, CircleShape).tap { onClose() },
            contentAlignment = Alignment.Center
        ) { IcClose(TextSecondary, side = 10.dp) }
    }
} //面板标题行（带关闭）

/** 加号面板：分类附件 + 技能包；插入纯文本，不写 @，不会和 @ 面板抢输入 */
@Composable
internal fun AttachPanel(
    onClose: () -> Unit,
    onPickImage: () -> Unit,
    onPickFile: (kind: String, mime: String) -> Unit,
    onSkill: () -> Unit
) {
    val ctx = LocalContext.current
    SheetShell {
        SheetHead(stringResource(R.string.ip_title), onClose)
        AttachRow(stringResource(R.string.ip_img), stringResource(R.string.ip_img_d)) { onPickImage() }
        AttachRow(stringResource(R.string.ip_file), stringResource(R.string.ip_file_d)) { onPickFile(ctx.getString(R.string.ip_file), "*/*") }
        AttachRow(stringResource(R.string.ip_audio), stringResource(R.string.ip_audio_d)) { onPickFile(ctx.getString(R.string.ip_audio), "audio/*") }
        AttachRow(stringResource(R.string.ip_video), stringResource(R.string.ip_video_d)) { onPickFile(ctx.getString(R.string.ip_video), "video/*") }
        AttachRow(stringResource(R.string.ip_music), stringResource(R.string.ip_music_d)) { onPickFile(ctx.getString(R.string.ip_music), "audio/*") }
        Row(
            Modifier
                .fillMaxWidth()
                .background(AccentBlueSoft, RoundedCornerShape(10.dp))
                .tap { onSkill() }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.ip_skill), fontSize = 12.sp, color = AccentBlue)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.ip_skill_d), fontSize = 10.sp, color = TextSecondary)
            Spacer(Modifier.weight(1f))
            Text("›", fontSize = 14.sp, color = AccentBlue)
        }
    }
} //附件分类面板

@Composable
private fun AttachRow(label: String, desc: String, onTap: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .background(SurfaceMuted, RoundedCornerShape(10.dp))
            .tap { onTap() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, color = TextPrimary)
        Spacer(Modifier.width(8.dp))
        Text(desc, fontSize = 10.sp, color = TextSecondary, maxLines = 1)
    }
} //一行附件分类

/** 技能包：点一条把技能正文带进输入框 */
@Composable
internal fun SkillPanel(onClose: () -> Unit, onInsert: (String) -> Unit) {
    SheetShell {
        SheetHead(stringResource(R.string.ip_skill), onClose)
        val ctx = LocalContext.current
        val pfx = ctx.getString(R.string.ip_skill_pre)
        Skills.list.forEach { s ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
                    .background(SurfaceMuted, RoundedCornerShape(10.dp))
                    .tap { onInsert(pfx + s.name + "\n" + s.body.take(1200) + "\n\n任务：") }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        Skills.displayName(ctx, s) + if (s.on) "" else stringResource(R.string.ip_off),
                        fontSize = 12.sp,
                        color = TextPrimary,
                        maxLines = 1
                    )
                    Text(
                        Skills.displayDesc(ctx, s).ifBlank { s.body.take(40) },
                        fontSize = 10.sp,
                        color = TextSecondary,
                        maxLines = 1
                    )
                }
                Text("＋", fontSize = 13.sp, color = AccentBlue)
            }
        }
        //MCP 能力当技能列：点了插「用某能力做某事」，具体参数由模型自己拼
        val mcpTools = Mcp.tools
        if (mcpTools.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.mcp_group), fontSize = 10.sp, color = TextSecondary)
            Spacer(Modifier.height(4.dp))
            mcpTools.forEach { t ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                        .background(SurfaceMuted, RoundedCornerShape(10.dp))
                        .tap { onInsert(ctx.getString(R.string.mcp_use_pre, t.name, t.server) + "\n") }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "MCP·" + t.server + "·" + t.name,
                            fontSize = 12.sp,
                            color = TextPrimary,
                            maxLines = 1
                        )
                        Text(t.desc, fontSize = 10.sp, color = TextSecondary, maxLines = 1)
                    }
                    Text("＋", fontSize = 13.sp, color = AccentBlue)
                }
            }
        }
    }
} //技能包面板

/** 三条横线面板：本轮模型 / 是否思考 / 最大模式 */
@Composable
internal fun CfgPanel(onClose: () -> Unit) {
    SheetShell {
        SheetHead(stringResource(R.string.ip_cfg), onClose)
        Text(stringResource(R.string.ip_model), fontSize = 10.sp, color = TextSecondary)
        Spacer(Modifier.height(4.dp))
        Profiles.list.forEach { p ->
            val sel = p.id == Cfg.profileId
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
                    .background(if (sel) AccentBlueSoft else SurfaceMuted, RoundedCornerShape(10.dp))
                    .tap {
                        //切走前先把当前表单值写回旧配置，改了一半的输入不丢
                        Profiles.capture()
                        Profiles.select(p.id)
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    p.label,
                    fontSize = 12.sp,
                    color = if (sel) AccentBlue else TextPrimary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                if (sel) Text("✓", fontSize = 12.sp, color = AccentBlue)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ip_think), fontSize = 12.sp, color = TextPrimary)
                Text(
                    if (Cfg.think) "reasoning_effort=${Cfg.thinkEffort}" else stringResource(R.string.ip_think_off),
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
            Switch(
                checked = Cfg.think,
                onCheckedChange = { Cfg.think = it },
                colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
            )
        }
        //思考强度：三档滑杆，比三个小胶囊好按，拖动即生效
        val levels = listOf("low", "medium", "high")
        val li = levels.indexOf(Cfg.thinkEffort).coerceAtLeast(0)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.ip_strength, levels[li]), fontSize = 10.sp, color = TextSecondary)
            Spacer(Modifier.width(8.dp))
            Slider(
                value = li.toFloat(),
                onValueChange = { v -> Cfg.thinkEffort = levels[v.toInt().coerceIn(0, 2)] },
                valueRange = 0f..2f,
                steps = 1,
                enabled = Cfg.think,
                modifier = Modifier.weight(1f).height(22.dp),
                colors = SliderDefaults.colors(
                    thumbColor = AccentBlue,
                    activeTrackColor = AccentBlue,
                    inactiveTrackColor = SurfaceMuted
                )
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ip_max), fontSize = 12.sp, color = TextPrimary)
                Text(
                    if (Cfg.maxMode) stringResource(R.string.ip_max_d) else stringResource(R.string.ip_max_off),
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
            Switch(
                checked = Cfg.maxMode,
                onCheckedChange = { Cfg.maxMode = it },
                colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
            )
        }
    }
} //本轮配置面板

/** 模型面板：输入区左下角 chip 打开，选本轮模型，或直接进设置页改配置 */
@Composable
internal fun ModelPanel(onClose: () -> Unit, onOpenSettings: () -> Unit) {
    SheetShell {
        SheetHead(stringResource(R.string.ip_model), onClose)
        if (Profiles.list.isEmpty()) {
            Text(stringResource(R.string.ip_no_profile), fontSize = 10.sp, color = TextSecondary)
            Spacer(Modifier.height(6.dp))
        }
        Profiles.list.forEach { p ->
            val sel = p.id == Cfg.profileId
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
                    .background(if (sel) AccentBlueSoft else SurfaceMuted, RoundedCornerShape(10.dp))
                    .tap {
                        //切走前先把当前表单值写回旧配置，改了一半的输入不丢
                        Profiles.capture()
                        Profiles.select(p.id)
                        onClose()
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        p.label,
                        fontSize = 12.sp,
                        color = if (sel) AccentBlue else TextPrimary,
                        maxLines = 1
                    )
                    Text(
                        p.model.ifBlank { stringResource(R.string.ip_no_model) },
                        fontSize = 10.sp,
                        color = TextSecondary,
                        maxLines = 1
                    )
                }
                if (sel) Text("✓", fontSize = 12.sp, color = AccentBlue)
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(AccentBlueSoft, RoundedCornerShape(10.dp))
                .tap { onOpenSettings() }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.ip_profile), fontSize = 12.sp, color = AccentBlue)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.ip_profile_sub), fontSize = 10.sp, color = TextSecondary)
            Spacer(Modifier.weight(1f))
            Text("›", fontSize = 14.sp, color = AccentBlue)
        }
    }
} //模型面板：本轮模型 + 配置入口
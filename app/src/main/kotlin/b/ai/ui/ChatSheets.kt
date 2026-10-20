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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.core.Cfg
import b.ai.core.Profiles
import b.ai.core.Skills
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.AccentBlueSoft
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary

/** 加号面板：分类附件 + 技能包；插入的是纯文本，不写 @，不会和 @ 提及面板抢输入 */
@Composable
internal fun AttachSheet(
    onClose: () -> Unit,
    onPickImage: () -> Unit,
    onPickFile: (kind: String, mime: String) -> Unit,
    onSkill: () -> Unit
) {
    ScrimCard(onDismiss = onClose) {
        Text("添加内容", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text("选一类塞进输入区，模型按路径自己读", fontSize = 11.sp, color = TextSecondary)
        Spacer(Modifier.height(10.dp))
        AttachRow("图片", "进多模态，模型直接看图", "image/*", onPickImage, onPickFile)
        AttachRow("文件", "任意文档，路径插进输入框", "*/*", null, onPickFile)
        AttachRow("音频", "录音 / 语音文件", "audio/*", null, onPickFile)
        AttachRow("视频", "视频文件，路径插进输入框", "video/*", null, onPickFile)
        AttachRow("音乐", "歌曲与音频素材", "audio/*", null, onPickFile)
        Row(
            Modifier
                .fillMaxWidth()
                .background(AccentBlueSoft, RoundedCornerShape(12.dp))
                .tap { onSkill() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("技能包", fontSize = 13.sp, color = AccentBlue)
                Text("内置技能带进本轮，让模型照步骤做", fontSize = 10.sp, color = TextSecondary)
            }
            Text("›", fontSize = 15.sp, color = AccentBlue)
        }
    }
} //附件分类面板

@Composable
private fun AttachRow(
    label: String,
    desc: String,
    mime: String,
    onImage: (() -> Unit)?,
    onFile: (kind: String, mime: String) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .background(SurfaceMuted, RoundedCornerShape(12.dp))
            .tap { if (onImage != null) onImage() else onFile(label, mime) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 13.sp, color = TextPrimary)
            Text(desc, fontSize = 10.sp, color = TextSecondary)
        }
        Text("›", fontSize = 15.sp, color = TextSecondary)
    }
} //一行附件分类

/** 技能包：点一条把技能正文带进输入框，等于手写一句「按这条技能做」 */
@Composable
internal fun SkillSheet(onClose: () -> Unit, onInsert: (String) -> Unit) {
    ScrimCard(onDismiss = onClose) {
        Text("技能包", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text("点一条带进输入框，随本轮消息发给模型", fontSize = 11.sp, color = TextSecondary)
        Spacer(Modifier.height(10.dp))
        Column(Modifier.fillMaxWidth().heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
            Skills.list.forEach { s ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .background(SurfaceMuted, RoundedCornerShape(12.dp))
                        .tap {
                            onInsert("按这条技能做：" + s.name + "\n" + s.body.take(1200) + "\n\n任务：")
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            s.name + if (s.on) "（已启用）" else "",
                            fontSize = 13.sp,
                            color = TextPrimary,
                            maxLines = 1
                        )
                        Text(
                            s.desc.ifBlank { s.body.take(40) },
                            fontSize = 10.sp,
                            color = TextSecondary,
                            maxLines = 1
                        )
                    }
                    Text("＋", fontSize = 15.sp, color = AccentBlue)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("技能内容在 设置 → 对话 → 技能专区 里增删改", fontSize = 10.sp, color = TextSecondary)
    }
} //技能包面板

/** 输入区三条横线面板：本轮模型 / 是否思考 / 最大模式 */
@Composable
internal fun ChatCfgSheet(onClose: () -> Unit) {
    ScrimCard(onDismiss = onClose) {
        Text("本轮配置", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text("改完对下一轮请求立刻生效", fontSize = 11.sp, color = TextSecondary)
        Spacer(Modifier.height(10.dp))
        Text("模型", fontSize = 11.sp, color = TextSecondary)
        Spacer(Modifier.height(4.dp))
        Column(Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
            Profiles.list.forEach { p ->
                val sel = p.id == Cfg.profileId
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .background(if (sel) AccentBlueSoft else SurfaceMuted, RoundedCornerShape(10.dp))
                        .tap {
                            //切走前先存回旧配置，改了一半的 Key / 模型不会丢
                            Profiles.capture()
                            Profiles.select(p.id)
                        }
                        .padding(horizontal = 10.dp, vertical = 9.dp),
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
                            p.model.ifBlank { "未选模型" },
                            fontSize = 10.sp,
                            color = TextSecondary,
                            maxLines = 1
                        )
                    }
                    if (sel) Text("✓", fontSize = 12.sp, color = AccentBlue)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("深度思考", fontSize = 13.sp, color = TextPrimary)
                Text(
                    if (Cfg.think) "下发 reasoning_effort=${Cfg.thinkEffort}" else "不思考，直接回答",
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
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("low", "medium", "high").forEach { v ->
                PolicyChip(v, Cfg.thinkEffort == v) { Cfg.thinkEffort = v }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("最大模式", fontSize = 13.sp, color = TextPrimary)
                Text(
                    if (Cfg.maxMode) "不下发 max_tokens，思考顶到 high" else "按配置的 max_tokens 走",
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
        Spacer(Modifier.height(10.dp))
        Text("增删模型配置去 设置 → 模型；压缩阈值在 设置 → 对话", fontSize = 10.sp, color = TextSecondary)
    }
} //本轮配置面板
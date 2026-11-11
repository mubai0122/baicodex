package b.ai.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.core.cfg.app.PromptTags
import b.ai.ui.dialog.HostInOverlay
import b.ai.ui.dialog.ScrimCard
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import b.ai.ui.widget.CardBox
import b.ai.ui.widget.Field
import b.ai.ui.widget.GhostButton
import b.ai.ui.widget.SectionTitle
import b.ai.ui.widget.tap

/** 提示词标签子页：列表、启用开关、新建 / 编辑 / 删除 */
fun LazyListScope.promptTagSec() {
    item { PromptTagBody() }
}

@Composable
private fun PromptTagBody() {
    var list by remember { mutableStateOf(PromptTags.list()) }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PromptTags.Tag?>(null) }
    var name by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var arming by remember { mutableStateOf<String?>(null) }
    val open = creating || editing != null

    fun close() {
        creating = false
        editing = null
    } //关弹窗

    HostInOverlay(open) {
        if (open) {
            ScrimCard(onDismiss = { close() }) {
                Text(if (creating) "新建片段" else "编辑片段", fontSize = 16.sp, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                Field("名字", name, { name = it }, placeholder = "比如：只回代码")
                Field(
                    "正文",
                    body,
                    { body = it },
                    placeholder = "要贴给模型的原文，比如：不要解释思路，只给最终代码",
                    singleLine = false,
                    minLines = 5
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        "取消",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        modifier = Modifier
                            .tap { close() }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                    Text(
                        "保存",
                        fontSize = 13.sp,
                        color = AccentBlue,
                        modifier = Modifier
                            .tap {
                                if (body.isBlank() && name.isBlank()) {
                                    close()
                                    return@tap
                                }
                                val cur = editing
                                if (cur == null) PromptTags.add(name, body)
                                else PromptTags.update(cur.copy(name = name, body = body))
                                list = PromptTags.list()
                                close()
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }

    Column {
        SectionTitle("提示词标签", "启用的片段每轮都拼进系统提示；插入只影响这一次发言")
        val on = list.count { it.on && it.body.isNotBlank() }
        CardBox {
            Text(
                "启用 $on 条 · 共 ${list.size} 条" + if (on > 0) "（每轮多带这些字，别放太长的）" else "",
                fontSize = 11.sp,
                color = TextSecondary
            )
        }
        if (list.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            CardBox {
                Text("还没有片段。新建一条后可以在聊天输入框的附件面板里点它插入。", fontSize = 11.sp, color = TextSecondary)
            }
        }
        list.forEach { t ->
            Spacer(Modifier.height(8.dp))
            CardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.name, fontSize = 13.sp, color = TextPrimary)
                        Text(
                            t.body.replace("\n", " ").take(60).ifBlank { "（正文为空，启用也不会生效）" },
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = t.on,
                        onCheckedChange = {
                            PromptTags.setOn(t.id, it)
                            list = PromptTags.list()
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        "编辑",
                        fontSize = 11.sp,
                        color = AccentBlue,
                        modifier = Modifier
                            .tap {
                                editing = t
                                creating = false
                                name = t.name
                                body = t.body
                                arming = null
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (arming == t.id) "确认删除" else "删除",
                        fontSize = 11.sp,
                        color = if (arming == t.id) AccentBlue else TextSecondary,
                        modifier = Modifier
                            .tap {
                                if (arming == t.id) {
                                    PromptTags.remove(t.id)
                                    list = PromptTags.list()
                                    arming = null
                                } else {
                                    arming = t.id
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        CardBox {
            GhostButton("新建片段") {
                creating = true
                editing = null
                name = ""
                body = ""
                arming = null
            }
        }
    }
} //提示词标签子页
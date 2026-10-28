package b.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.R
import b.ai.core.Dsl
import b.ai.core.Secret
import b.ai.core.SkillFn
import b.ai.core.SkillPack
import b.ai.core.SkillPacks
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.DangerRed
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 技能包详情：README、skill.json、每个函数的模板体都能就地改；
 * 函数里声明了参数或引用了 $secret.xxx 时，这里自动长出对应的可配置项。
 */
@Composable
fun PackDialog(pack: SkillPack, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val dir = pack.dir
    var readme by remember(pack.id) { mutableStateOf(pack.readme) }
    var json by remember(pack.id) {
        mutableStateOf(runCatching { File(dir, "skill.json").readText() }.getOrDefault(""))
    }
    var tplOf by remember { mutableStateOf("") } //正在编辑模板的函数名
    var tpl by remember { mutableStateOf("") }
    var tip by remember { mutableStateOf("") }
    var secrets by remember { mutableStateOf(Secret.all()) }
    //包内所有函数引用到的密钥，去重后生成输入框
    val need = remember(pack.id) { pack.funcs.flatMap { SkillPacks.secretsOf(it.body) }.distinct() }

    fun save(f: File, text: String) {
        tip = runCatching {
            f.parentFile?.mkdirs()
            f.writeText(text)
            SkillPacks.reload(ctx)
            ctx.getString(R.string.pk_saved)
        }.getOrElse { ctx.getString(R.string.pk_bad, it.message ?: "?") }
    } //写文件并重扫技能包

    ScrimCard(onDismiss = onClose) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                pack.name.ifBlank { pack.id },
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(Modifier.width(8.dp))
            Text(pack.id, fontSize = 10.sp, color = TextSecondary, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.pk_close),
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier
                    .liquidBg(RoundedCornerShape(8.dp), tint = SurfaceMuted)
                    .tap { onClose() }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
        Text(
            stringResource(R.string.pk_dir, dir.ifBlank { "-" }),
            fontSize = 9.sp,
            color = TextSecondary,
            maxLines = 1
        )
        if (tip.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(tip, fontSize = 10.sp, color = TextSecondary)
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 430.dp)
                .verticalScroll(glassScrollState())
        ) {
            Column {
                //函数清单：参数列出来，改模板后保存前先 lint
                Text(stringResource(R.string.pk_fns, pack.funcs.size), fontSize = 12.sp, color = TextPrimary)
                pack.funcs.forEach { f ->
                    FnBlock(
                        f = f,
                        editing = tplOf == f.name,
                        tpl = tpl,
                        onTpl = { tpl = it },
                        onEdit = {
                            if (tplOf == f.name) {
                                tplOf = ""
                            } else {
                                tplOf = f.name
                                tpl = f.body
                            }
                        },
                        onSave = {
                            val err = Dsl.lint(tpl, f.kind, f.params)
                            if (err != null) tip = ctx.getString(R.string.pk_bad, err)
                            else save(File(File(dir, "function"), f.file), tpl)
                        }
                    )
                }
                //需要的密钥：模板里有 $secret.x 但没配，调用必然失败，这里直接给输入框
                if (need.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.pk_secret_need), fontSize = 12.sp, color = TextPrimary)
                    need.forEach { n ->
                        SecretRow(n, secrets[n].orEmpty()) { v ->
                            Secret.put(n, v)
                            secrets = Secret.all()
                            tip = ctx.getString(R.string.pk_saved)
                        }
                    }
                }
                //README 与 skill.json：原样可改，json 保存前先解析并把每个函数 lint 一遍
                if (dir.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Field(
                        stringResource(R.string.pk_readme),
                        readme,
                        { readme = it },
                        singleLine = false,
                        minLines = 5
                    )
                    GhostButton(stringResource(R.string.pk_save), Modifier.height(32.dp)) {
                        save(File(dir, "README.md"), readme)
                    }
                    Spacer(Modifier.height(8.dp))
                    Field(stringResource(R.string.pk_json), json, { json = it }, singleLine = false, minLines = 6)
                    GhostButton(stringResource(R.string.pk_save), Modifier.height(32.dp)) {
                        val err = checkJson(dir, json)
                        if (err != null) tip = ctx.getString(R.string.pk_bad, err)
                        else save(File(dir, "skill.json"), json)
                    }
                }
            }
        }
    }
} //技能包详情弹窗

/** 单个函数：名字 / kind / 参数清单 / 校验错误 / 模板体编辑 */
@Composable
private fun FnBlock(
    f: SkillFn,
    editing: Boolean,
    tpl: String,
    onTpl: (String) -> Unit,
    onEdit: () -> Unit,
    onSave: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .liquidBg(RoundedCornerShape(10.dp), tint = SurfaceMuted)
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(f.name, fontSize = 12.sp, color = TextPrimary, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(6.dp))
            Text(f.kind, fontSize = 9.sp, color = TextSecondary)
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.pk_edit),
                fontSize = 10.sp,
                color = AccentBlue,
                modifier = Modifier
                    .liquidBg(RoundedCornerShape(8.dp), tint = SurfaceMuted)
                    .tap { onEdit() }
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        Text(
            if (f.params.isEmpty()) stringResource(R.string.pk_no_param)
            else stringResource(R.string.pk_params, f.params.entries.joinToString("、") { it.key }),
            fontSize = 9.sp,
            color = TextSecondary,
            maxLines = 2
        )
        if (f.bad.isNotEmpty()) Text(f.bad, fontSize = 9.sp, color = DangerRed)
        if (editing) {
            Spacer(Modifier.height(6.dp))
            Field(stringResource(R.string.pk_tpl, f.file), tpl, onTpl, singleLine = false, minLines = 6)
            GhostButton(stringResource(R.string.pk_save), Modifier.height(30.dp)) { onSave() }
        }
    }
} //函数块

/** 一个密钥输入框：已配置的只显示末四位 */
@Composable
private fun SecretRow(name: String, value: String, onSave: (String) -> Unit) {
    var v by remember(name, value) { mutableStateOf("") }
    Column {
        Field(
            if (value.isEmpty()) name else "$name（••••${value.takeLast(4)}）",
            v,
            { v = it },
            secret = true
        )
        if (value.isEmpty()) {
            Text(stringResource(R.string.pk_secret_miss, name), fontSize = 9.sp, color = DangerRed)
        }
        GhostButton(stringResource(R.string.pk_secret_save), Modifier.height(30.dp)) { onSave(v) }
    }
} //密钥行

/** 保存 skill.json 前先验一遍：JSON 能解析，且每个声明的函数按新 kind/params 仍然 lint 通过 */
private fun checkJson(dir: String, text: String): String? = runCatching {
    val o = JSONObject(text)
    val arr = o.optJSONArray("funcs") ?: JSONArray()
    for (i in 0 until arr.length()) {
        val f = arr.optJSONObject(i) ?: continue
        val name = f.optString("name").trim()
        if (name.isEmpty()) continue
        val kind = f.optString("kind").trim().ifEmpty { "cmd" }
        val file = f.optString("file").trim().ifEmpty { "$name.js" }
        val ps = LinkedHashMap<String, String>()
        f.optJSONObject("params")?.let { p -> p.keys().forEach { k -> ps[k] = p.optString(k) } }
        val body = runCatching { File(File(dir, "function"), file).readText() }.getOrNull()
            ?: throw IllegalStateException("缺 function/$file")
        Dsl.lint(body, kind, ps)?.let { throw IllegalStateException("$name: $it") }
    }
    null
}.getOrElse { it.message ?: "?" } //校验新 json 与模板是否还对得上
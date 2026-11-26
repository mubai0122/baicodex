/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27 01:28:54
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * 账号页：GitHub 登录（对齐手机端的账号卡）。
 * 桌面端用 PAT，不内嵌浏览器；登录后 token 只落本机 auth.json，
 * 命令工具执行时注入 GITHUB_TOKEN / GH_TOKEN，git 不再弹账号密码。
 */

package b.ai.desktop.feature.settings.sections

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.desktop.app.AppState
import b.ai.desktop.core.config.ConfigStore
import b.ai.desktop.core.util.openUrl
import b.ai.desktop.feature.settings.PillButton
import b.ai.desktop.feature.settings.SectionTitle
import b.ai.desktop.feature.settings.SettingRow
import b.ai.desktop.feature.settings.TextSetting
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AccountSettings(state: AppState) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val account = state.gitAccount

    var token by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var tip by remember { mutableStateOf<String?>(null) }
    var tipOk by remember { mutableStateOf(true) }

    SectionTitle("GitHub", "登录后命令工具会带上 GITHUB_TOKEN，私有仓库 clone / push 不用再输密码")

    if (account == null) {
        Text(
            "用 Personal Access Token 登录。只需要 read:user 权限；要推送私有仓库的话勾上 repo。\n" +
                "token 只写到本机 ${File(ConfigStore.appDir(), "auth.json").absolutePath}，不经过任何服务器。",
            fontSize = 10.sp,
            lineHeight = 17.sp,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.size(6.dp))
        TextSetting(
            label = "Personal Access Token",
            value = token,
            onChange = { token = it },
            placeholder = "ghp_… 或 github_pat_…",
            password = true,
        )
        Row(
            Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PillButton("登录", primary = true, enabled = !busy && token.isNotBlank()) {
                if (busy) return@PillButton
                busy = true
                scope.launch {
                    val result = state.gitSignIn(token)
                    busy = false
                    tipOk = result.startsWith("已登录")
                    tip = result
                    if (tipOk) token = ""
                }
            }
            PillButton("生成 token…") {
                openUrl("https://github.com/settings/tokens/new?scopes=repo,read:user&description=Baicodex%20Windows")
            }
        }
        if (busy) {
            Text("正在校验 token…", fontSize = 10.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
    } else {
        Row(
            Modifier.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(account.avatarFile, account.display, size = 44)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    account.display,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface,
                )
                Text("@${account.login}", fontSize = 11.sp, color = colors.onSurfaceVariant)
            }
            PillButton("退出登录") {
                state.gitSignOut()
                tip = null
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = colors.outlineVariant)
        SettingRow("Token", "已保存（末尾 ${account.token.takeLast(4)}）") {
            Text("auth.json", fontSize = 10.sp, color = colors.onSurfaceVariant)
        }
        SettingRow("生效范围", "run_command、插件命令；文件工具不需要它") {
            Text("环境变量", fontSize = 10.sp, color = colors.onSurfaceVariant)
        }
    }

    if (tip != null) {
        Text(
            tip!!,
            fontSize = 10.sp,
            color = if (tipOk) colors.primary else colors.error,
            modifier = Modifier.padding(top = 8.dp),
        )
    }

    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)

    SectionTitle("Git 环境", "登录只解决凭据；commit / clone 还需要本机装了 git")
    var gitVer by remember { mutableStateOf<String?>(null) }
    var checked by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        gitVer = withContext(Dispatchers.IO) { gitVersion() }
        checked = true
    }
    SettingRow(
        "本机 git",
        when {
            !checked -> "正在检测…"
            gitVer == null -> "没找到 git：装完 Git for Windows 后点重测"
            else -> gitVer!!
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PillButton("重新检测") {
                scope.launch {
                    gitVer = withContext(Dispatchers.IO) { gitVersion() }
                    checked = true
                }
            }
            if (checked && gitVer == null) {
                PillButton("下载 Git…") { openUrl("https://git-scm.com/download/win") }
            }
        }
    }
}

private fun gitVersion(): String? = runCatching {
    val process = ProcessBuilder("git", "--version").redirectErrorStream(true).start()
    val text = process.inputStream.bufferedReader().use { it.readText() }.trim()
    if (!process.waitFor(4, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        return@runCatching null
    }
    text.ifBlank { null }
}.getOrNull()

/** 头像：读本地缓存 PNG，失败退回首字母，不让登录因为图片挂掉 */
@Composable
private fun Avatar(fileName: String, name: String, size: Int) {
    val colors = MaterialTheme.colorScheme
    val bitmap = remember(fileName) {
        if (fileName.isBlank()) null
        else runCatching {
            val file = File(ConfigStore.appDir(), fileName)
            if (!file.exists()) return@runCatching null
            val image = org.jetbrains.skia.Image.makeFromEncoded(file.readBytes())
            image.toComposeImageBitmap()
        }.getOrNull()
    }

    Box(
        Modifier.size(size.dp).clip(CircleShape).background(colors.primary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                name.take(1).uppercase().ifBlank { "?" },
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.primary,
            )
        }
    }
}

/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:23
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package b.ai.ui.screen.settings.ui

import b.ai.ui.screen.settings.about.*
import b.ai.ui.screen.settings.agent.*
import b.ai.ui.screen.settings.chat.*
import b.ai.ui.screen.settings.core.*
import b.ai.ui.screen.settings.ia.*
import b.ai.ui.screen.settings.kit.*
import b.ai.ui.screen.settings.model.*
import b.ai.ui.screen.settings.perm.*
import b.ai.agent.memory.MemOrganizer
import b.ai.agent.memory.Memory
import b.ai.native.security.BxKernel
import b.ai.ui.dialog.guardStateText
import b.ai.ui.glass.glassScrollState
import b.ai.ui.dialog.GuardBusyDialog
import b.ai.ui.dialog.GuardOffDialog
import b.ai.ui.dialog.GuardProgressDialog
import b.ai.ui.dialog.GuardWarnDialog
import b.ai.ui.dialog.MaskWarnDialog
import b.ai.ui.dialog.ScrimCard
import b.ai.ui.dialog.SimpleDialog
import b.ai.ui.screen.user.Rate
import b.ai.ui.widget.AvatarView
import b.ai.ui.dialog.FlowSheet
import b.ai.ui.dialog.HostInOverlay
import b.ai.ui.dialog.PackDialog
import b.ai.ui.dialog.PermAsk
import b.ai.ui.dialog.PermAskDialog
import b.ai.ui.glass.GlassPerf
import b.ai.ui.glass.ProvideCardBackdrop
import b.ai.ui.glass.backdropPageInset
import b.ai.ui.glass.drawPageBg
import b.ai.ui.glass.glassLazyListState
import b.ai.ui.glass.glassPageLayer
import b.ai.ui.glass.liquidBg
import b.ai.ui.glass.pageBg
import b.ai.ui.screen.stat.StatUi
import b.ai.ui.screen.stat.statTab
import b.ai.ui.widget.AvatarPick
import b.ai.core.data.db.Repo
import b.ai.ui.widget.CardBox
import b.ai.ui.widget.ChoiceRow
import b.ai.ui.widget.Field
import b.ai.ui.widget.FloatTab
import b.ai.ui.widget.FloatTabBar
import b.ai.ui.widget.GhostButton
import b.ai.ui.widget.IcChevron
import b.ai.ui.widget.IcTrash
import b.ai.ui.widget.LocalPicker
import b.ai.ui.widget.MdText
import b.ai.ui.widget.PickItem
import b.ai.ui.widget.PickSheet
import b.ai.ui.widget.SectionTitle
import b.ai.ui.widget.PrimaryButton
import b.ai.ui.widget.SelectField
import b.ai.ui.widget.WsPurge
import b.ai.ui.widget.WsPurgeDialog
import b.ai.ui.widget.tap
import b.ai.core.cfg.app.Bai
import b.ai.core.net.WebWipe
import b.ai.core.task.hook.Noti
import b.ai.device.sys.a11y.AutoA11y
import b.ai.device.pet.PetSvc
import b.ai.net.api.llm.text
import b.ai.net.api.llm.ThinkRules
import b.ai.net.mi.MiConf
import b.ai.native.security.MiCrypto

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.os.Handler
import android.os.Build
import android.os.Looper
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.agent.run.Agent
import b.ai.agent.tool.Tools
import android.app.Activity
import b.ai.core.media.img.Avatar
import b.ai.core.model.voice.Audio
import b.ai.core.model.voice.AsrPresets
import b.ai.core.data.io.Backup
import b.ai.native.security.Guard
import b.ai.core.cfg.app.Cfg
import b.ai.device.sys.island.Island
import b.ai.device.sys.island.Vendor
import b.ai.core.cfg.app.Lang
import b.ai.net.mcp.Mcp
import b.ai.native.security.Mask
import b.ai.net.mcp.McpServer
import b.ai.core.cfg.env.Env
import b.ai.core.cfg.env.PromptVars
import b.ai.core.model.voice.Speak
import b.ai.core.perm.Perms
import b.ai.agent.persona.Persona
import b.ai.agent.persona.Personas
import b.ai.core.data.profile.Profiles
import b.ai.net.api.a2a.A2aNodes
import b.ai.agent.flow.Flows
import b.ai.net.api.a2a.A2aSrv
import b.ai.core.task.alarm.Schedules
import b.ai.core.task.hook.Triggers
import b.ai.net.search.Search
import b.ai.core.plugin.skill.Skill
import b.ai.core.plugin.skill.Skills
import b.ai.core.cfg.secret.Secret
import b.ai.core.plugin.skill.SkillPacks
import b.ai.core.plugin.skill.SkillPack
import b.ai.core.media.wall.Wallpaper
import b.ai.core.model.chat.WorkMode
import b.ai.device.shell.root.Shiz
import b.ai.device.sys.alarm.Alarms
import b.ai.device.app.FChat
import b.ai.device.shell.local.Linux
import b.ai.device.pet.Pet
import b.ai.device.sys.host.PetGrant
import b.ai.device.sys.host.SecureWin
import b.ai.device.sys.probe.Router
import b.ai.device.shell.local.Toolchain
import b.ai.device.fs.Workspace
import b.ai.net.api.update.Update
import b.ai.net.doc.MaskRules
import b.ai.MainActivity
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.AccentBlueSoft
import b.ai.ui.theme.BgGray
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import b.ai.ui.theme.BorderGray
import b.ai.ui.theme.DangerRed
import b.ai.ui.theme.DangerRedSoft
import b.ai.ui.theme.OkGreen
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.SurfaceWhite
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import b.ai.R
//miuix 风格滑杆自绘要用的一批：Canvas 画轨道、pointerInput 吃拖动、offset 摆滑块
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import b.ai.ui.theme.FontStore
import b.ai.ui.theme.LocalAiFont
import b.ai.ui.theme.LocalUserFont
import kotlin.math.roundToInt

//字号档位：存的是倍率，1f = 跟随系统
internal val FontPresets = listOf(R.string.se_163 to 0.9f, R.string.se_164 to 1f, R.string.se_165 to 1.15f, R.string.se_166 to 1.3f)

/**工具权限一次先铺多少条：几十个下拉同屏组合是设置页最贵的一帧 */
internal const val TOOL_PAGE = 12

//回到底部圆钮的三档配色文案：0 默认白 / 1 继承液态玻璃 / 2 继承主题色
internal fun scrollBtnStyleLabels() = listOf("默认白", "继承液态玻璃", "继承主题色")

//外观总页：语言 / 主题色 / 字号 / 背景 / 玻璃质感与卡片外观 / 输入框 / Markdown 排版 / 输入提示
//（原「主题」与「输入与质感」两页合并：用户要的所有观感配置都收在这一页，不再分家）
internal fun LazyListScope.uiLookSec() {
    item {
        //切语言：存完配置要重建 Activity，界面才会真的换语言
        val act = LocalContext.current as? Activity
        val pickLang: (String) -> Unit = { code ->
            if (Cfg.lang != code) {
                Cfg.lang = code
                act?.recreate()
            }
        }
        SectionTitle(stringResource(R.string.se_167), stringResource(R.string.se_168))
        CardBox {
            val langs = Lang.options
            SelectField(
                stringResource(R.string.se_167),
                langs.firstOrNull { it.first == Cfg.lang }?.second ?: stringResource(R.string.se_187),
                langs.map { it.first },
                optionLabel = { code -> langs.first { it.first == code }.second },
                isSelected = { Cfg.lang == it },
                onSelect = { pickLang(it) }
            )
        }
    }
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.se_030), stringResource(R.string.se_185))
        CardBox {
            SelectField(
                stringResource(R.string.se_030),
                when (Cfg.darkTheme) {
                    1 -> stringResource(R.string.se_188)
                    2 -> stringResource(R.string.se_189)
                    else -> stringResource(R.string.se_187)
                },
                listOf(0, 1, 2),
                optionLabel = {
                    when (it) {
                        1 -> stringResource(R.string.se_188)
                        2 -> stringResource(R.string.se_189)
                        else -> stringResource(R.string.se_187)
                    }
                },
                isSelected = { Cfg.darkTheme == it },
                onSelect = { Cfg.darkTheme = it }
            )
            Spacer(Modifier.height(6.dp))
            Text(
                when (Cfg.darkTheme) {
                    1 -> stringResource(R.string.se_190)
                    2 -> stringResource(R.string.se_191)
                    else -> stringResource(R.string.se_192)
                },
                fontSize = 11.sp,
                color = TextSecondary
            )
        }
    }
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.se_tint_t), stringResource(R.string.se_tint_d))
        CardBox {
            SelectField(
                stringResource(R.string.se_tint_t),
                stringResource(TINT_NAMES[Cfg.themeTint.coerceIn(0, TINT_NAMES.lastIndex)]),
                TINT_NAMES.indices.toList(),
                optionLabel = { stringResource(TINT_NAMES[it]) },
                isSelected = { Cfg.themeTint == it },
                onSelect = { Cfg.themeTint = it }
            )
        }
    }
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.se_032), stringResource(R.string.se_193))
        CardBox {
            SelectField(
                stringResource(R.string.se_032),
                FontPresets.firstOrNull { kotlin.math.abs(Cfg.fontScale - it.second) < 0.01f }?.let { stringResource(it.first) }
                    ?: stringResource(R.string.se_164),
                FontPresets.map { it.second },
                optionLabel = { v -> stringResource(FontPresets.first { it.second == v }.first) },
                isSelected = { kotlin.math.abs(Cfg.fontScale - it) < 0.01f },
                onSelect = { Cfg.fontScale = it }
            )
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.se_font_cur, (Cfg.fontScale * 100).toInt()), fontSize = 11.sp, color = TextSecondary)
        }
    }
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.tx_8456bc40), stringResource(R.string.tx_0b5342ec))
        CardBox {
            //字体目录每次进页面重扫：用户可能刚用文件管理器把字体丢进 fonts
            var fontVer by remember { mutableIntStateOf(0) }
            val opts = remember(fontVer) { FontStore.options() }
            val ctx = LocalContext.current
            val pickFont = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) {
                    val f = FontStore.import(ctx, uri)
                    if (f != null) {
                        fontVer = fontVer + 1
                        Toast.makeText(ctx, ctx.getString(R.string.tx_946262ed) + f.name, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(ctx, ctx.getString(R.string.tx_84defbe5), Toast.LENGTH_SHORT).show()
                    }
                }
            } //导入字体
            SelectField(
                stringResource(R.string.tx_99557469),
                FontStore.label(Cfg.uiFontFile),
                opts,
                desc = stringResource(R.string.tx_a5bccdfb),
                optionLabel = { if (it.isEmpty()) stringResource(R.string.tx_a52f5c20) else FontStore.label(it) },
                isSelected = { Cfg.uiFontFile == it },
                onSelect = { Cfg.uiFontFile = it }
            )
            SelectField(
                stringResource(R.string.tx_c4e93556),
                FontStore.label(Cfg.userFontFile),
                opts,
                optionLabel = { if (it.isEmpty()) stringResource(R.string.tx_112be922) else FontStore.label(it) },
                isSelected = { Cfg.userFontFile == it },
                onSelect = { Cfg.userFontFile = it }
            )
            SelectField(
                stringResource(R.string.tx_f2af02ae),
                FontStore.label(Cfg.aiFontFile),
                opts,
                optionLabel = { if (it.isEmpty()) stringResource(R.string.tx_112be922) else FontStore.label(it) },
                isSelected = { Cfg.aiFontFile == it },
                onSelect = { Cfg.aiFontFile = it }
            )
            Spacer(Modifier.height(8.dp))
            GlassSlider(
                stringResource(R.string.tx_7b5ff17c),
                "${Cfg.globalIndent} dp",
                Cfg.globalIndent.toFloat(),
                0f..32f,
                1f
            ) { Cfg.globalIndent = it.toInt() }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GhostButton(stringResource(R.string.tx_d94fd817), modifier = Modifier.height(36.dp)) {
                    pickFont.launch(arrayOf("*/*"))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    if (opts.size <= 1) stringResource(R.string.tx_4d6e461d) else stringResource(R.string.tx_935695b1, opts.size - 1),
                    fontSize = 10.5.sp,
                    color = TextSecondary
                )
            }
        }
    }
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.tx_366d6528), stringResource(R.string.tx_73d7e891))
        CardBox {
            GlassSlider(
                stringResource(R.string.tx_4210b61b),
                "${Cfg.dialogCorner} dp",
                Cfg.dialogCorner.toFloat(),
                0f..32f,
                2f
            ) { Cfg.dialogCorner = it.toInt() }
            GlassSlider(
                stringResource(R.string.tx_b972541e),
                "${Cfg.sliderTrack}%",
                Cfg.sliderTrack.toFloat(),
                5f..60f,
                5f
            ) { Cfg.sliderTrack = it.toInt() }
            GlassSlider(
                stringResource(R.string.tx_a83942eb),
                "${Cfg.sliderTrackH} dp",
                Cfg.sliderTrackH.toFloat(),
                12f..48f,
                2f
            ) { Cfg.sliderTrackH = it.toInt() }
            Text(stringResource(R.string.tx_5d94fa40), fontSize = 10.sp, color = TextSecondary)
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.tx_9bfd20b1), fontSize = 13.5.sp, color = TextPrimary)
                    Text(
                        if (Cfg.sliderAccent) stringResource(R.string.tx_0556c32b) else stringResource(R.string.tx_daf51302),
                        fontSize = 10.5.sp,
                        color = TextSecondary
                    )
                }
                Switch(
                    checked = Cfg.sliderAccent,
                    onCheckedChange = { Cfg.sliderAccent = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = SurfaceWhite,
                        checkedTrackColor = AccentBlue
                    )
                )
            }
        }
    }
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.se_bg), stringResource(R.string.se_bg_d))
        CardBox {
            SelectField(
                stringResource(R.string.se_bg),
                when (Cfg.bgStyle) {
                    1 -> stringResource(R.string.se_bg_grad)
                    2 -> stringResource(R.string.se_bg_dots)
                    else -> stringResource(R.string.se_bg_plain)
                },
                listOf(0, 1, 2),
                optionLabel = {
                    when (it) {
                        1 -> stringResource(R.string.se_bg_grad)
                        2 -> stringResource(R.string.se_bg_dots)
                        else -> stringResource(R.string.se_bg_plain)
                    }
                },
                isSelected = { Cfg.bgStyle == it },
                onSelect = { Cfg.bgStyle = it }
            )
            Spacer(Modifier.height(10.dp))
            //背景图：从相册选一张铺满页面，模糊度单独一条滑杆
            BgImagePick()
        }
    }
    //玻璃质感：卡片 / 底栏 / 模糊 / 折射 / 高光 / 乳白，外加两种模式都生效的卡片外观（不透明度、圆角）
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.se_glass_t), stringResource(R.string.se_glass_d))
        CardBox {
            CapSwitch(
                stringResource(R.string.se_glass_card),
                stringResource(R.string.se_glass_card_d),
                Cfg.glassCard
            ) { Cfg.glassCard = it }
            Spacer(Modifier.height(10.dp))
            CapSwitch(
                stringResource(R.string.se_tactile),
                stringResource(R.string.se_tactile_d),
                Cfg.tactileBtn
            ) { Cfg.tactileBtn = it }
            Spacer(Modifier.height(10.dp))
            CapSwitch(
                stringResource(R.string.se_glass_bar),
                stringResource(R.string.se_glass_bar_d),
                Cfg.glassBar
            ) { Cfg.glassBar = it }
            Spacer(Modifier.height(10.dp))
            //模糊强度 0-20dp：0 只留折射，越大越糊；底栏与底部面板共用这个值
            //走统一滑杆：这样才跟其它滑杆一样能继承主题色 / 调轨道浓度
            GlassSlider(
                stringResource(R.string.se_glass_blur),
                stringResource(R.string.se_glass_blur_v, Cfg.glassBlur),
                Cfg.glassBlur.toFloat(),
                0f..20f,
                step = 1f
            ) { Cfg.glassBlur = it.toInt() }
            Text(stringResource(R.string.se_glass_blur_d), fontSize = 10.sp, color = TextSecondary)
            //开了玻璃卡片才展开细调：折射 / 高光 / 乳白，三个直接影响观感的参数
            FollowBlock(Cfg.glassCard) {
                Spacer(Modifier.height(10.dp))
                GlassSlider(stringResource(R.string.tx_713fd45a), "${Cfg.glassLens} dp", Cfg.glassLens.toFloat(), 0f..24f, step = 1f) {
                    Cfg.glassLens = it.toInt()
                }
                Text(stringResource(R.string.tx_1b605a1e), fontSize = 10.sp, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                GlassSlider(stringResource(R.string.tx_d73f1ed6), "${Cfg.glassHi}%", Cfg.glassHi.toFloat(), 0f..100f, step = 5f) {
                    Cfg.glassHi = it.toInt()
                }
                Text(stringResource(R.string.tx_e82c50b0), fontSize = 10.sp, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                GlassSlider(stringResource(R.string.tx_fd5f796f), "${Cfg.glassVeil}%", Cfg.glassVeil.toFloat(), 0f..60f, step = 2f) {
                    Cfg.glassVeil = it.toInt()
                }
                Text(stringResource(R.string.tx_ba3f1876), fontSize = 10.sp, color = TextSecondary)
            }
            //卡片不透明度：两种模式都生效——白卡模式直接调白底 alpha，玻璃模式按比例缩乳白遮罩
            Spacer(Modifier.height(10.dp))
            GlassSlider(stringResource(R.string.tx_fa5d97dd), "${Cfg.cardAlpha}%", Cfg.cardAlpha.toFloat(), 0f..100f, step = 5f) {
                Cfg.cardAlpha = it.toInt()
            }
            Text(stringResource(R.string.tx_47aeba9f), fontSize = 10.sp, color = TextSecondary)
            //卡片圆角：预设档位滑杆，不做输入框；白卡与玻璃卡共用
            Spacer(Modifier.height(10.dp))
            GlassSlider(stringResource(R.string.tx_3d86509a), "${Cfg.cardCorner} dp", Cfg.cardCorner.toFloat(), 0f..32f, step = 4f) {
                Cfg.cardCorner = it.toInt()
            }
            Text(stringResource(R.string.tx_ec83af0d), fontSize = 10.sp, color = TextSecondary)
            //回到底部圆钮的配色：三档都给白卡 / 玻璃卡两种模式用，选哪个当场生效
            Spacer(Modifier.height(10.dp))
            SelectField(
                label = stringResource(R.string.tx_f778da7e),
                value = scrollBtnStyleLabels()[Cfg.scrollBtnStyle],
                options = listOf(0, 1, 2),
                desc = stringResource(R.string.tx_96a5feb3),
                optionLabel = { scrollBtnStyleLabels()[it] },
                isSelected = { it == Cfg.scrollBtnStyle },
                onSelect = { Cfg.scrollBtnStyle = it }
            )
        }
    }
    //输入框：浮动输入、全屏入口、超长粘贴转文件
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.se_033), stringResource(R.string.se_195))
        CardBox {
            CapSwitch(
                stringResource(R.string.se_196),
                stringResource(R.string.se_197),
                Cfg.floatInput
            ) { Cfg.floatInput = it }
            Spacer(Modifier.height(10.dp))
            //新逻辑：超 700 字才出现全屏入口；关掉就让它一直挂在语音右边
            CapSwitch(
                stringResource(R.string.se_fullin_t),
                stringResource(R.string.se_fullin_d),
                Cfg.fullInputNew
            ) { Cfg.fullInputNew = it }
            Spacer(Modifier.height(10.dp))
            //粘贴超长文本自动转文件：默认开，阈值滑杆 500 起、500 一档
            CapSwitch(
                stringResource(R.string.se_paste_t),
                stringResource(R.string.se_paste_d),
                Cfg.pasteToFile
            ) { Cfg.pasteToFile = it }
            FollowBlock(Cfg.pasteToFile) {
                Spacer(Modifier.height(6.dp))
                //同上：统一走 miuix 滑杆，500 一档，能继承主题色
                GlassSlider(
                    stringResource(R.string.se_paste_limit),
                    stringResource(R.string.se_paste_limit_v, Cfg.pasteLimit),
                    Cfg.pasteLimit.toFloat(),
                    500f..20000f,
                    step = 500f
                ) { Cfg.pasteLimit = it.toInt() }
                Text(stringResource(R.string.se_paste_limit_d), fontSize = 10.sp, color = TextSecondary)
            }
        }
    }
    //输入提示文案：输入框空白时的占位提示
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.se_035), stringResource(R.string.se_198))
        CardBox {
            Field(
                label = stringResource(R.string.se_199),
                value = Cfg.hintText,
                onValueChange = { Cfg.hintText = it },
                placeholder = stringResource(R.string.se_200),
                singleLine = false,
                minLines = 3
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (Cfg.hintText.isBlank()) stringResource(R.string.se2_hint_none)
                else stringResource(R.string.se2_hint_n, Cfg.hintText.lines().count { it.isNotBlank() }),
                fontSize = 11.sp,
                color = TextSecondary
            )
        }
    }
} //外观总页

//个性化子页：头像、气泡胶囊与桌宠
internal fun LazyListScope.uiPersonSec(onOpenPet: () -> Unit) {
    item {
        SectionTitle(stringResource(R.string.se_169), stringResource(R.string.se_170))
        CardBox {
            CapSwitch(stringResource(R.string.se_171), stringResource(R.string.se_172), Cfg.avatarOn) {
                Cfg.avatarOn = it
            }
            Spacer(Modifier.height(8.dp))
            //Waifu 已挪到「回复信息」分区，跟关闭流式输出放一起
            CapSwitch(stringResource(R.string.se_173), stringResource(R.string.se_174), Cfg.bubbleCapsule) {
                Cfg.bubbleCapsule = it
            }
            Spacer(Modifier.height(10.dp))
            //聊天卡片样式：分式（默认，每条一个气泡）/ 整体式（一轮一张卡，点头部折叠形变）
            Text(stringResource(R.string.se_card_style), fontSize = 13.sp, color = TextPrimary)
            Spacer(Modifier.height(6.dp))
            ChoiceRow(
                stringResource(R.string.se_card_style_split),
                stringResource(R.string.se_card_style_split_d),
                Cfg.chatCardStyle == 0
            ) { Cfg.chatCardStyle = 0 }
            Spacer(Modifier.height(6.dp))
            ChoiceRow(
                stringResource(R.string.se_card_style_whole),
                stringResource(R.string.se_card_style_whole_d),
                Cfg.chatCardStyle == 1
            ) { Cfg.chatCardStyle = 1 }
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.se_card_style_note), fontSize = 10.sp, color = TextSecondary)
        }
    }
    item {
        Spacer(Modifier.height(10.dp))
        AvatarPick(stringResource(R.string.se_177), stringResource(R.string.se_178), Cfg.avatarUser) { p ->
            Avatar.forget(Cfg.avatarUser)
            Cfg.avatarUser = p
        }
    }
    item {
        Spacer(Modifier.height(14.dp))
        AvatarPick(stringResource(R.string.se_179), stringResource(R.string.se_180), Avatar.aiPath()) { p ->
            Avatar.forget(Cfg.avatarAi)
            Cfg.avatarAi = p
            //清空就等于放弃自定义，回落到跟角色卡
            Cfg.avatarAiMode = if (p.isEmpty()) "persona" else "custom"
        }
    }
    item {
        Spacer(Modifier.height(16.dp))
        CardBox {
            ChoiceRow(stringResource(R.string.se_181), stringResource(R.string.se_182), Cfg.avatarAiMode != "custom") {
                Cfg.avatarAiMode = "persona"
            }
            ChoiceRow(
                stringResource(R.string.se_183),
                if (Cfg.avatarAi.isEmpty()) stringResource(R.string.se_184) else stringResource(R.string.se_186),
                Cfg.avatarAiMode == "custom"
            ) {
                if (Cfg.avatarAi.isNotBlank()) Cfg.avatarAiMode = "custom"
            }
        }
    }
    item {
        Spacer(Modifier.height(10.dp))
        //桌宠 + 回车发送合成一张卡：两行都走 CardBox，圆角 / 描边 / 玻璃 / 内边距跟设置页其它卡片一致
        CardBox {
            //桌宠：一整行独立开关，配置图标就在开关左边，点它进桌宠设置页
            PetEntryRow(onOpenPet)
            Spacer(Modifier.height(10.dp))
            //回车发送：外接键盘/物理键盘才有意义；关掉后回车只换行，发送靠右侧按钮
            CapSwitch(
                "回车键发送",
                if (Cfg.enterSend) "回车直接发送，Shift+回车换行" else "回车只换行，不发送",
                Cfg.enterSend
            ) { Cfg.enterSend = it }
        }
    }
    //余额通知：进 App 自动查一次 DeepSeek 余额，低于阈值发一条系统通知（个性化页最下方）
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.se_bal), stringResource(R.string.se_bal_sub))
        CardBox {
            CapSwitch(
                stringResource(R.string.se_bal_on),
                stringResource(R.string.se_bal_on_d),
                Cfg.balNotify
            ) { Cfg.balNotify = it }
            FollowBlock(Cfg.balNotify) {
                Spacer(Modifier.height(6.dp))
                GlassSlider(
                    stringResource(R.string.se_bal_th),
                    stringResource(R.string.se_bal_th_v, Cfg.balThreshold),
                    Cfg.balThreshold.toFloat(),
                    1f..100f,
                    step = 1f
                ) { Cfg.balThreshold = it.toInt() }
                Text(stringResource(R.string.se_bal_th_d), fontSize = 10.sp, color = TextSecondary)
                Spacer(Modifier.height(10.dp))
                //余额查询来源：多 Key 用户必须能指定查哪个（/user/balance 只认 DeepSeek 官方 Key）
                val balProfiles = Profiles.list
                val balSrc = balProfiles.firstOrNull { it.id == Cfg.balProfileId }
                SelectField(
                    stringResource(R.string.se_bal_src),
                    balSrc?.label?.ifBlank { balSrc.model } ?: stringResource(R.string.se_bal_src_auto),
                    listOf("") + balProfiles.map { it.id },
                    optionLabel = { id ->
                        if (id.isBlank()) stringResource(R.string.se_bal_src_auto)
                        else balProfiles.firstOrNull { it.id == id }?.label?.ifBlank { id } ?: id
                    },
                    isSelected = { Cfg.balProfileId == it },
                    onSelect = { Cfg.balProfileId = it }
                )
                Text(stringResource(R.string.se_bal_src_d), fontSize = 10.sp, color = TextSecondary)
            }
        }
    }
    } //个性化子页

//（原「输入与质感」子页内容已合并到「外观」总页 uiLookSec）

internal fun LazyListScope.uiNavSec() {
    //悬浮聊天窗：先自查权限，再给开关，最后是窗口参数——三项都在一页里调完
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.tx_2ecb1bb9), stringResource(R.string.tx_bb007351))
        CardBox {
            val fctx = LocalContext.current
            val overlayOk = Pet.canShow(fctx)
            val aliveOk = Perms.batteryFree(fctx)
            //权限自查：没悬浮窗权限时开关打开也显示不出来，以前只表现为「点了没反应」
            SelfCheckRow(
                stringResource(R.string.tx_720ab972),
                if (overlayOk) stringResource(R.string.tx_0c47bf01) else stringResource(R.string.tx_b6f6ccf7),
                overlayOk
            ) { PetGrant.openOverlaySetting(fctx) }
            Spacer(Modifier.height(8.dp))
            SelfCheckRow(
                stringResource(R.string.tx_2911c1e5),
                if (aliveOk) stringResource(R.string.tx_cd36e684) else stringResource(R.string.tx_adc2cb46),
                aliveOk
            ) { Perms.openSpecial(fctx, Perms.SP_BATTERY) }
            Spacer(Modifier.height(10.dp))
            CapSwitch(
                stringResource(R.string.tx_3b4c7ed4),
                if (Cfg.fcOn) stringResource(R.string.tx_d742e670) else stringResource(R.string.tx_9ec0f5da),
                Cfg.fcOn
            ) { on ->
                if (!on) {
                    Cfg.fcOn = false
                    FChat.hide()
                } else if (Pet.canShow(fctx)) {
                    Cfg.fcOn = true
                    FChat.show(fctx)
                } else {
                    //没悬浮窗权限：开关不落地，先送去系统设置授权
                    Cfg.fcOn = false
                    PetGrant.openOverlaySetting(fctx)
                }
            }
            //窗口参数：改完立刻 apply()，窗口不重建、位置不变
            Spacer(Modifier.height(10.dp))
            GlassSlider(stringResource(R.string.tx_cae01fea), "${Cfg.fcW} dp", Cfg.fcW.toFloat(), 220f..480f, step = 10f) {
                Cfg.fcW = it.toInt()
                FChat.apply()
            }
            Spacer(Modifier.height(8.dp))
            GlassSlider(stringResource(R.string.tx_c73258a2), "${Cfg.fcH} dp", Cfg.fcH.toFloat(), 260f..900f, step = 20f) {
                Cfg.fcH = it.toInt()
                FChat.apply()
            }
            Spacer(Modifier.height(8.dp))
            GlassSlider(stringResource(R.string.tx_e013ea26), "${Cfg.fcAlpha}%", Cfg.fcAlpha.toFloat(), 40f..100f, step = 5f) {
                Cfg.fcAlpha = it.toInt()
                FChat.apply()
            }
            Text(stringResource(R.string.tx_529f8817), fontSize = 10.sp, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            GlassSlider(stringResource(R.string.tx_48f369a7), "${Cfg.fcFont} sp", Cfg.fcFont.toFloat(), 10f..22f, step = 1f) {
                Cfg.fcFont = it.toInt()
                FChat.apply()
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                //位置复位：把 fcX / fcY 归 -1，apply() 时会落回默认的右上角
                GhostButton(stringResource(R.string.tx_e90e4777), modifier = Modifier.height(36.dp)) {
                    Cfg.fcX = -1
                    Cfg.fcY = -1
                    FChat.apply()
                }
                GhostButton(
                    if (Cfg.fcOn) stringResource(R.string.tx_5f4766cc) else stringResource(R.string.tx_ea02bb1f),
                    modifier = Modifier.height(36.dp)
                ) {
                    if (Cfg.fcOn) {
                        Cfg.fcOn = false
                        FChat.hide()
                    } else if (Pet.canShow(fctx)) {
                        Cfg.fcOn = true
                        FChat.show(fctx)
                    } else {
                        PetGrant.openOverlaySetting(fctx)
                    }
                }
            }
        }
    }
    //返回手势：预测性返回 + 直达系统手势导航设置
    item {
        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.se_037), stringResource(R.string.se2_back_sub))
        CardBox {
            val gctx = LocalContext.current
            CapSwitch(
                stringResource(R.string.se2_back_title),
                stringResource(R.string.se2_back_desc),
                Cfg.predictiveBack
            ) { Cfg.predictiveBack = it }
            Spacer(Modifier.height(6.dp))
            Text(
                "安卓 13 起支持「预测性返回」：滑动手势时先把当前页缩小、露出上一页，中途松手还能取消。" +
                    "系统没开这个能力时开关不会有效果，开关状态由应用自己记。",
                fontSize = 10.sp,
                color = TextSecondary
            )
            Spacer(Modifier.height(8.dp))
            GhostButton(stringResource(R.string.tx_0c1e4c99), modifier = Modifier.height(36.dp)) {
                //直接跳到系统手势导航页：各家 ROM 的入口不同，跳不过去就退到应用详情页
                runCatching {
                    //用字面量 action：部分编译 SDK 里没有 ACTION_GESTURE_NAVIGATION_SETTINGS 这个常量
                    gctx.startActivity(
                        Intent("android.settings.GESTURE_NAVIGATION_SETTINGS")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }.onFailure { Perms.openAppSettings(gctx) }
            }
        }
    }
} //外观分类：悬浮与手势


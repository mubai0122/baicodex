/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:26
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package b.ai.ui.screen.user

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import b.ai.R
import b.ai.net.vbai.VbaiAuth
import b.ai.core.cfg.app.Cfg
import b.ai.core.data.profile.Profiles
import b.ai.ui.dialog.ScrimCard
import b.ai.ui.glass.liquidBg
import b.ai.ui.theme.DangerRed
import b.ai.ui.theme.OkGreen
import b.ai.ui.theme.SurfaceWhite
import b.ai.ui.theme.TextPrimary
import b.ai.ui.theme.TextSecondary
import b.ai.ui.widget.GhostButton
import b.ai.ui.widget.IcClose
import b.ai.ui.widget.PrimaryButton
import b.ai.ui.widget.tap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.shape.RoundedCornerShape
import b.ai.ui.theme.SurfaceMuted

/** 文案取用：切换语言会重建 Activity，所以直接查资源表，不必依赖 Compose 作用域 */
private fun tx(id: Int, vararg args: Any): String = try {
    b.ai.App.self.getString(id, *args)
} catch (e: Exception) {
    ""
} //取文案

/** Vbai 账号卡的开关状态：true = 卡片在场，由主界面弹窗层统一挂载 */
object VbaiUi {
    var open by mutableStateOf(false)

    fun show() {
        VbaiAuth.sync()
        open = true
    } //打开

    fun close() {
        //卡片一关就取消轮询：用户不想等了就别继续打平台接口
        VbaiAuth.cancel()
        open = false
    } //关闭
} //Vbai 账号卡状态

/** Vbai 账号卡组：跟 GitHub 账号卡同一个挂载点，互不干扰 */
@Composable
fun VbaiDialogs() {
    if (VbaiUi.open) VbaiCard()
} //账号卡组

/** 账号卡本体：未登录给设备码引导，已登录给账号信息 + 一键填入模型配置 */
@Composable
private fun VbaiCard() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tip by remember { mutableStateOf("") }
    var ok by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val on = VbaiAuth.logged
    val busy = VbaiAuth.busy

    ScrimCard(onDismiss = { if (!busy) VbaiUi.close() }) {
        //标题行：右边一枚玻璃叉，跟 GitHub 账号卡保持同一套观感
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(tx(R.string.vb_title), fontSize = 15.sp, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(26.dp)
                    .liquidBg(CircleShape, tint = SurfaceWhite, veil = 0.3f, gate = 0.dp)
                    .tap { VbaiUi.close() },
                contentAlignment = Alignment.Center
            ) { IcClose(TextSecondary, side = 12.dp) }
        }
        Text(
            tx(R.string.vb_sub),
            fontSize = 10.sp,
            color = TextSecondary,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )
        Spacer(Modifier.height(10.dp))

        if (on) {
            //已登录：账号 / 套餐 / 余额 / Key 掩码
            Text(VbaiAuth.user.ifBlank { tx(R.string.vb_logged) }, fontSize = 14.sp, color = TextPrimary)
            Text(
                buildString {
                    append(tx(R.string.vb_authorized))
                    if (VbaiAuth.plan.isNotBlank()) append(" · ").append(tx(R.string.vb_plan, VbaiAuth.plan))
                    if (VbaiAuth.balance.isNotBlank()) append(" · ").append(tx(R.string.vb_balance, VbaiAuth.balance))
                },
                fontSize = 10.sp,
                color = OkGreen,
                modifier = Modifier.padding(top = 2.dp)
            )
            if (VbaiAuth.masked.isNotBlank()) {
                Text(
                    tx(R.string.vb_key, VbaiAuth.masked),
                    fontSize = 10.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            GhostButton(tx(R.string.vb_fill_model), modifier = Modifier.height(38.dp)) {
                ok = VbaiAuth.fillModel()
                tip = if (ok) tx(R.string.vb_filled, VbaiAuth.base()) else tx(R.string.vb_no_key)
            }
            Spacer(Modifier.height(8.dp))
            Row {
                GhostButton(tx(R.string.vb_relogin), modifier = Modifier.height(38.dp)) {
                    scope.launch { withContext(Dispatchers.IO) { startLogin() } }
                }
                Spacer(Modifier.width(10.dp))
                GhostButton(tx(R.string.vb_logout), modifier = Modifier.height(38.dp)) {
                    VbaiAuth.logout()
                    ok = true
                    tip = tx(R.string.vb_logout_done)
                }
            }
        } else if (busy || VbaiAuth.stage == "wait") {
            //等确认：显示倒计时 + 重开授权页 + 取消
            Text(VbaiAuth.msg, fontSize = 11.sp, color = TextPrimary, modifier = Modifier.fillMaxWidth())
            Text(
                tx(R.string.vb_code_left, VbaiAuth.left),
                fontSize = 10.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(10.dp))
            Row {
                GhostButton(tx(R.string.vb_reopen), modifier = Modifier.height(38.dp)) {
                    reopen(ctx)
                }
                Spacer(Modifier.width(10.dp))
                GhostButton(tx(R.string.vb_copy_code), modifier = Modifier.height(38.dp)) {
                    //登录码就在 verify_url 的 code 参数里，复制整条链接最省事
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    cm?.setPrimaryClip(android.content.ClipData.newPlainText("vbai", VbaiAuth.verify))
                    copied = true
                }
            }
            Spacer(Modifier.height(8.dp))
            GhostButton(tx(R.string.vb_cancel), modifier = Modifier.height(38.dp)) { VbaiUi.close() }
            if (copied) {
                Text(tx(R.string.vb_copied), fontSize = 10.sp, color = OkGreen, modifier = Modifier.padding(top = 6.dp))
            }
        } else {
            //未登录：一句话讲清流程 + 开始登录
            Text(tx(R.string.vb_step1), fontSize = 11.sp, color = TextPrimary)
            Text(tx(R.string.vb_step2), fontSize = 11.sp, color = TextPrimary)
            Text(tx(R.string.vb_step3), fontSize = 11.sp, color = TextPrimary)
            Spacer(Modifier.height(10.dp))
            GhostButton(tx(R.string.vb_start), modifier = Modifier.height(38.dp)) {
                scope.launch { withContext(Dispatchers.IO) { startLogin() } }
            }
            Text(
                tx(R.string.vb_note),
                fontSize = 10.sp,
                color = TextSecondary,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }

        if (tip.isNotBlank()) {
            Text(
                tip,
                fontSize = 11.sp,
                color = if (ok) OkGreen else DangerRed,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }
    }
} //账号卡

/** 起一次登录：VbaiAuth 内部自己开浏览器 + 轮询，这里只负责结果提示 */
private suspend fun startLogin() {
    val got = VbaiAuth.login()
    if (!got && VbaiAuth.stage == "expired") {
        //过期/失败原因已经在 VbaiAuth.msg 里，卡片上直接能看到倒计时变成 0 与提示
    }
} //执行登录

/** 重新用系统浏览器打开授权页：用户可能中途关掉了浏览器 */
private fun reopen(ctx: Context) {
    val u = VbaiAuth.verify
    if (u.isBlank()) return
    runCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
} //重开授权页

/**
 * 模型配置表单里的 Vbai 内联登录块：选中「Vbai 中转站」时插在 base_url 之前。
 * 已登录只给「填入 Key」按钮（不重复走授权）；未登录点一下就地跑完设备码流程，
 * 成功后把 Key 与 base_url 回调给表单，表单本身不需要知道登录细节。
 */
@Composable
fun VbaiInlineFill(onFill: (key: String, base: String) -> Unit) {
    val scope = rememberCoroutineScope()
    var tip by remember { mutableStateOf("") }
    val on = VbaiAuth.logged
    val busy = VbaiAuth.busy

    Column(
        Modifier
            .fillMaxWidth()
            //上边距做在组件自己身上：不管谁把它贴在什么下面，都不会和上方卡片粘连
            .padding(top = 12.dp)
            .liquidBg(RoundedCornerShape(12.dp), tint = SurfaceMuted, veil = 0.3f, gate = 0.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(if (on) tx(R.string.vb_inline_on) else tx(R.string.vb_inline_login), fontSize = 13.sp, color = TextPrimary)
        Text(
            when {
                busy -> VbaiAuth.msg.ifBlank { tx(R.string.vb_logging) }
                on -> buildString {
                    append(VbaiAuth.user.ifBlank { tx(R.string.vb_authorized) })
                    if (VbaiAuth.plan.isNotBlank()) append(" · ").append(VbaiAuth.plan)
                    if (VbaiAuth.masked.isNotBlank()) append(" · ").append(VbaiAuth.masked)
                } //账号摘要
                else -> tx(R.string.vb_inline_hint)
            },
            fontSize = 10.sp,
            color = if (on && !busy) OkGreen else TextSecondary,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
        )
        Spacer(Modifier.height(8.dp))
        Row {
            if (on) {
                GhostButton(tx(R.string.vb_fill_key), modifier = Modifier.height(34.dp)) {
                    val k = VbaiAuth.key()
                    if (k.isBlank()) {
                        tip = tx(R.string.vb_no_key)
                    } else {
                        onFill(k, VbaiAuth.base())
                        tip = tx(R.string.vb_filled_key)
                    } //有 Key 才回填
                }
                Spacer(Modifier.width(10.dp))
            }
            GhostButton(
                if (busy) tx(R.string.vb_cancel_login) else if (on) tx(R.string.vb_relogin) else tx(R.string.vb_oneclick),
                modifier = Modifier.height(34.dp)
            ) {
                if (busy) {
                    VbaiAuth.cancel()
                } else {
                    scope.launch {
                        tip = ""
                        //网络与轮询都在 IO 线程，避免主线程卡住
                        withContext(Dispatchers.IO) { VbaiAuth.login() }
                        tip = if (VbaiAuth.logged) tx(R.string.vb_login_ok) else VbaiAuth.msg
                    } //跑一次完整登录
                }
            }
        }
        if (tip.isNotBlank()) {
            Text(
                tip,
                fontSize = 10.sp,
                color = if (VbaiAuth.logged) OkGreen else DangerRed,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            )
        }
    }
} //Vbai 内联登录

/** 宣传页的全局触发位：配置列表按钮只置位，真正弹卡由 ModelSetupPanel 的弹窗层消费 */
object VbaiIntro {
    var ask by mutableStateOf(false)
} //宣传页触发位

/** 无可用配置时的新建询问卡：是 = 进宣传页，否 = 走原来的空白配置 */
@Composable
fun VbaiAskCard(onYes: () -> Unit, onNo: () -> Unit) {
    ScrimCard(onDismiss = onNo) {
        Text(tx(R.string.vb_ask_title), fontSize = 15.sp, color = TextPrimary)
        Text(
            tx(R.string.vb_ask_body),
            fontSize = 11.sp,
            color = TextSecondary,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            GhostButton(tx(R.string.vb_ask_no), modifier = Modifier.weight(1f).height(40.dp)) { onNo() }
            Spacer(Modifier.width(10.dp))
            PrimaryButton(tx(R.string.vb_ask_yes), modifier = Modifier.weight(1f).height(40.dp)) { onYes() }
        }
    }
} //新建询问卡

/**
 * Vbai 宣传页：引导页与「无配置新建」共用。
 * 两个出口：打开官网（注册/充值）与登录账号（设备码授权）。
 * 登录成功回调 onDone(key, base)，由调用方决定是落成新配置还是填入当前表单。
 */
@Composable
fun VbaiIntroCard(onDone: ((String, String) -> Unit)? = null, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tip by remember { mutableStateOf("") }
    var got by remember { mutableStateOf(false) }
    val busy = VbaiAuth.busy
    val on = VbaiAuth.logged || got

    ScrimCard(onDismiss = { if (!busy) onClose() }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(tx(R.string.vb_name), fontSize = 16.sp, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(26.dp)
                    .liquidBg(CircleShape, tint = SurfaceWhite, veil = 0.3f, gate = 0.dp)
                    .tap { if (!busy) onClose() },
                contentAlignment = Alignment.Center
            ) { IcClose(TextSecondary, side = 12.dp) }
        }
        Spacer(Modifier.height(8.dp))
        //卖点三条：登录方式 / 计费 / 兼容性，别写成广告，写成能验证的事实
        Text(tx(R.string.vb_intro_1), fontSize = 11.sp, color = TextPrimary, modifier = Modifier.padding(bottom = 4.dp))
        Text(tx(R.string.vb_intro_2), fontSize = 11.sp, color = TextPrimary, modifier = Modifier.padding(bottom = 4.dp))
        Text(tx(R.string.vb_intro_3), fontSize = 11.sp, color = TextPrimary)
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .liquidBg(RoundedCornerShape(10.dp), tint = SurfaceMuted, veil = 0.3f, gate = 0.dp)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(tx(R.string.vb_cur_key), fontSize = 10.sp, color = TextSecondary)
                Text(
                    if (on) VbaiAuth.masked.ifBlank { tx(R.string.vb_logged) } else tx(R.string.vb_not_login),
                    fontSize = 11.sp,
                    color = if (on) OkGreen else TextSecondary
                )
            }
            Text(
                VbaiAuth.base(),
                fontSize = 9.sp,
                color = TextSecondary,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            if (busy) tx(R.string.vb_cancel_login) else if (on) tx(R.string.vb_go) else tx(R.string.vb_login_btn),
            modifier = Modifier.fillMaxWidth().height(42.dp)
        ) {
            when {
                //登录进行中：给一个明确的出口，不然卡片关不掉会卡住
                busy -> VbaiAuth.cancel()
                //已登录或刚拿到 Key：直接把结果抛给调用方
                on -> {
                    val k = VbaiAuth.key()
                    if (k.isBlank()) {
                        tip = tx(R.string.vb_no_key)
                    } else {
                        onDone?.invoke(k, VbaiAuth.base())
                    }
                }
                else -> scope.launch {
                    tip = ""
                    withContext(Dispatchers.IO) { VbaiAuth.login() }
                    if (VbaiAuth.logged) {
                        got = true
                        tip = tx(R.string.vb_ok)
                        onDone?.invoke(VbaiAuth.key(), VbaiAuth.base())
                    } else {
                        tip = VbaiAuth.msg
                    }
                } //跑一次设备码登录
            }
        }
        Spacer(Modifier.height(8.dp))
        GhostButton(tx(R.string.vb_open_site), modifier = Modifier.fillMaxWidth().height(40.dp)) {
            //官网用外部浏览器：注册、充值都在那边，App 内不做仿浏览器
            runCatching {
                ctx.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(VbaiAuth.SITE))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.onFailure { tip = tx(R.string.vb_no_browser) }
        }
        if (tip.isNotBlank() || busy) {
            Text(
                tip.ifBlank { VbaiAuth.msg },
                fontSize = 10.sp,
                color = if (got) OkGreen else TextSecondary,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }
    }
} //Vbai 宣传页

/**
 * 把已登录的 Key 落成一条「Vbai 中转站」配置并选中。
 * 当前配置还是空白（没 Key、没模型、没 base）就原地改成 Vbai，避免列表里堆废条目；
 * 否则新建一条，不动用户已有的配置。返回配置名，空串代表没有可用 Key。
 */
fun vbaiWriteProfile(): String {
    val k = VbaiAuth.key()
    if (k.isBlank()) return ""
    val base = VbaiAuth.base()
    val cur = Profiles.selected
    val blank = cur != null && cur.apiKey.isBlank() && cur.model.isBlank() && cur.baseUrl.isBlank()
    Profiles.capture() //先把表单/当前值刷回原配置，再决定改哪一条
    val id = if (blank) cur!!.id else Profiles.add()
    val p = Profiles.byId(id) ?: return ""
    val name = "Vbai 官方中转"
    Profiles.upsert(p.copy(providerId = "vbai", name = name, custom = true, baseUrl = base))
    Cfg.setProfileKey(id, k)
    Profiles.select(id)
    return name
} //落成 Vbai 配置
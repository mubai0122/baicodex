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

package b.ai.net.vbai

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import b.ai.App
import b.ai.R
import b.ai.core.cfg.app.Cfg
import b.ai.core.cfg.secret.Secret
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Vbai 官方编程工具的设备码登录（device code flow）。
 *
 * 全程三步，客户端不需要任何密钥：
 * ① POST /api/app/device/start 领码 → 拿到 verify_url；
 * ② 用系统浏览器打开 verify_url，用户自己登录并点「确认授权」；
 * ③ 每 poll_after 秒 POST /api/app/device/poll，拿到 api_key.value 后存本机加密库。
 *
 * 安全口径：Key 与会话 token 只进 Secret（Vault 加密存储），界面只回显 masked，
 * 不进日志、不上传。code 一次性，被兑换后立即作废，过期就回第 ① 步重领。
 */

/** 文案取用：不用 Context，直接用全局 App；取不到就返回空串（不会因缺资源崩掉登录流程） */
private fun tx(id: Int, vararg args: Any): String = try {
    App.self.getString(id, *args)
} catch (e: Exception) {
    ""
} //取文案

object VbaiAuth {
    /** 官方编程工具的 app_id，平台侧登记固定值 */
    const val APP_ID = "vbai-code"

    /** 平台 API 根（设备码接口都在这里） */
    const val API = "https://ai.baiccb.cn/api"

    /** OpenAI 兼容中转根，登录成功后作为当前模型配置的 base_url */
    const val BASE = "https://ai.baiccb.cn/v1"

    /** 官网（宣传页「打开官网」按钮用；注册、充值、控制台都在这里） */
    const val SITE = "https://ai.baiccb.cn/"

    //加密库键名：Key 明文只在这里存在一次
    private const val K_KEY = "vbai_key"
    private const val K_MASK = "vbai_mask"
    private const val K_USER = "vbai_user"
    private const val K_PLAN = "vbai_plan"
    private const val K_BAL = "vbai_balance"
    private const val K_TOKEN = "vbai_token"
    private const val K_BASE = "vbai_base"

    /** 领码失败时同一 IP 有 30/小时限额，本地再压一层：60s 内不许重复发起 */
    private const val START_GUARD_MS = 60_000L

    @Volatile
    private var lastStartAt = 0L

    /** 流程阶段：空 / start 领码中 / wait 等确认 / ok 成功 / expired 过期 / error 失败 */
    var stage by mutableStateOf("")
        private set

    /** 给用户看的一句话状态 */
    var msg by mutableStateOf("")
        private set

    /** 授权页地址：界面上的「再次打开」按钮用它 */
    var verify by mutableStateOf("")
        private set

    /** 剩余秒数：界面上做倒计时，让用户知道还有多久要重来 */
    var left by mutableIntStateOf(0)
        private set

    var busy by mutableStateOf(false)
        private set

    /** 账号信息：登录成功后回填，界面上只读展示 */
    var user by mutableStateOf(Secret.get(K_USER))
        private set
    var plan by mutableStateOf(Secret.get(K_PLAN))
        private set
    var balance by mutableStateOf(Secret.get(K_BAL))
        private set
    var masked by mutableStateOf(Secret.get(K_MASK))
        private set

    /** 已登录：加密库里有 Key 就算 */
    val logged: Boolean get() = Secret.get(K_KEY).isNotBlank()

    /** Key 原文：只在「填入模型配置」和发请求时取，界面别直接展示 */
    fun key(): String = Secret.get(K_KEY)

    /** 中转根：平台返回什么就用什么，缺省用内置常量 */
    fun base(): String = Secret.get(K_BASE).ifBlank { BASE }

    /** 静默重扫一次账号信息（App 启动、切换页面时调，保证和加密库一致） */
    fun sync() {
        user = Secret.get(K_USER)
        plan = Secret.get(K_PLAN)
        balance = Secret.get(K_BAL)
        masked = Secret.get(K_MASK)
    } //同步账号信息

    /**
     * 全流程登录：领码 → 开浏览器 → 轮询。协程内部自己 delay，调用方只管启动一次。
     * 返回 true 表示拿到 Key；false 表示过期/失败/被取消（原因在 msg）。
     */
    suspend fun login(): Boolean {
        if (occupied()) return false
        busy = true
        stage = "start"
        msg = tx(R.string.vb_s_starting)
        left = 0
        try {
            //① 领码
            val start = post("/app/device/start", JSONObject().put("app_id", APP_ID))
            if (start == null) {
                stage = "error"
                msg = lastErr
                return false
            }
            val d = start.optJSONObject("data") ?: JSONObject()
            val code = d.optString("code")
            if (code.isBlank()) {
                stage = "error"
                msg = start.optString("message").ifBlank { tx(R.string.vb_s_nocode) }
                return false
            }
            val ex = d.optInt("expires_in", 300)
            val wait = d.optInt("poll_after", 2).coerceIn(1, 10)
            verify = d.optString("verify_url")
            stage = "wait"
            msg = tx(R.string.vb_s_wait)
            openBrowser(verify)

            //② 轮询：严格按平台给的间隔，过期一次就收工，不复用旧 code
            val deadline = System.currentTimeMillis() + ex * 1000L
            while (System.currentTimeMillis() < deadline) {
                delay(wait * 1000L)
                if (!busy) return false //被 cancel()：用户自己关了卡片
                left = ((deadline - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
                val r = post("/app/device/poll", JSONObject().put("app_id", APP_ID).put("code", code))
                if (r == null) {
                    //网络抖动不算失败：只要还有时间就继续轮询，末尾再统一报错
                    msg = tx(R.string.vb_s_retry, left)
                    continue
                }
                when (r.optJSONObject("data")?.optString("status") ?: "") {
                    "ok" -> {
                        adopt(r.optJSONObject("data")!!)
                        stage = "ok"
                        msg = tx(R.string.vb_s_ok)
                        return true
                    }
                    "expired" -> {
                        stage = "expired"
                        msg = tx(R.string.vb_s_expired)
                        return false
                    }
                    else -> msg = tx(R.string.vb_s_waiting, left)
                }
            }
            stage = "expired"
            msg = tx(R.string.vb_s_expired)
            return false
        } catch (e: Exception) {
            stage = "error"
            msg = e.message ?: tx(R.string.vb_s_fail)
            return false
        } finally {
            busy = false
        }
    } //设备码登录

    /** 用户主动取消：轮询循环下一拍看到 busy=false 就退出，不会继续打平台接口 */
    fun cancel() {
        if (busy) {
            busy = false
            msg = tx(R.string.vb_s_cancel)
        }
    } //取消

    /** 退出登录：清本地 Key 与账号缓存；平台侧 Key 全账号唯一，不会被删除 */
    fun logout() {
        listOf(K_KEY, K_MASK, K_USER, K_PLAN, K_BAL, K_TOKEN, K_BASE).forEach { Secret.put(it, "") }
        user = ""
        plan = ""
        balance = ""
        masked = ""
        stage = ""
        msg = ""
        verify = ""
        left = 0
    } //退出

    /**
     * 把 Key 填进「当前模型配置」：base_url + api_key +（模型为空时给个默认）。
     * 这是显式操作，登录本身不动用户已有配置。
     */
    fun fillModel(): Boolean {
        val k = key()
        if (k.isBlank()) return false
        Cfg.baseUrl = base()
        Cfg.apiKey = k
        if (Cfg.model.isBlank()) Cfg.model = "deepseek-flash"
        return true
    } //填入模型配置

    /** 成功回调：把 Key / 账号信息一次性落库，明文只出现这一次 */
    private fun adopt(d: JSONObject) {
        val app = d.optJSONObject("app")
        val us = d.optJSONObject("user")
        val ak = d.optJSONObject("api_key")
        val ss = d.optJSONObject("session")
        Secret.put(K_KEY, ak?.optString("value").orEmpty())
        Secret.put(K_MASK, ak?.optString("masked").orEmpty())
        Secret.put(K_BASE, ak?.optString("base_url").orEmpty().ifBlank { BASE })
        Secret.put(K_TOKEN, ss?.optString("token").orEmpty())
        Secret.put(K_USER, us?.optString("username").orEmpty())
        Secret.put(K_PLAN, us?.optString("plan").orEmpty())
        val bal = us?.optDouble("balance", 0.0) ?: 0.0
        Secret.put(K_BAL, fmtMoney(bal))
        sync()
        //app.name 目前只用于日志留痕，不上报
        if (app != null) android.util.Log.d("Vbai", "authorized by " + app.optString("name"))
    } //落库

    /** 余额格式化：两位小数，避免界面上出现 0.8900000001 这种东西 */
    private fun fmtMoney(v: Double): String = String.format(java.util.Locale.US, "%.2f", v)

    /** 正在跑就别再起一条：避免叠着领码把 IP 限额刷爆 */
    private fun occupied(): Boolean {
        val now = System.currentTimeMillis()
        if (busy) return true
        if (now - lastStartAt < START_GUARD_MS) {
            stage = "error"
            msg = tx(R.string.vb_s_guard, (START_GUARD_MS - (now - lastStartAt)) / 1000)
            return true
        }
        lastStartAt = now
        return false
    } //节流

    /** 用系统浏览器打开授权页：内嵌 WebView 会被登录页的滑块挡住，文档也推荐外部浏览器 */
    private fun openBrowser(url: String) {
        if (url.isBlank()) return
        runCatching {
            App.self.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    } //打开浏览器

    //上次请求失败原因：给界面用，不抛异常
    @Volatile
    private var lastErr = ""

    /**
     * POST JSON：成功拿到可解析的 JSON 就返回它，否则返回 null（原因写 lastErr）。
     * 平台失败响应也是 JSON（{ok:false,code,message}），这里顺手把 message 抽出来当提示。
     */
    private fun post(path: String, body: JSONObject): JSONObject? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(API + path).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 12_000
                readTimeout = 20_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Baicodex-Android")
            }
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val txt = runCatching {
                (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
            }.getOrDefault("")
            val j = runCatching { JSONObject(txt) }.getOrNull()
            if (j == null) {
                lastErr = when (code) {
                    429 -> tx(R.string.vb_s_429)
                    410 -> tx(R.string.vb_s_410)
                    else -> tx(R.string.vb_s_http, code)
                }
                null
            } else {
                //平台约定：HTTP 非 2xx 或 ok=false 都算失败
                if (code !in 200..299 || !j.optBoolean("ok", false)) {
                    lastErr = j.optString("message").ifBlank { tx(R.string.vb_s_failhttp, code) }
                    null
                } else j
            }
        } catch (e: Exception) {
            lastErr = tx(R.string.vb_s_net, e.message ?: e.javaClass.simpleName)
            null
        } finally {
            runCatching { conn?.disconnect() }
        }
    } //POST JSON
} //Vbai 设备码登录
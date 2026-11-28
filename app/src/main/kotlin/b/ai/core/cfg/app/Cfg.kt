/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:04
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package b.ai.core.cfg.app
import b.ai.core.cfg.io.Store
import b.ai.core.model.chat.WorkMode
import b.ai.native.security.Vault
import b.ai.net.api.llm.text

import android.content.Context
import b.ai.core.cfg.io.Prefs
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import java.io.File

object Cfg {
    const val SYSTEM_PROMPT_FILE = "system_prompt.md"
    private var sp: Prefs? = null
    private val cache = HashMap<String, MutableState<Any?>>()

    @Suppress("UNCHECKED_CAST")
    private fun <T> mem(key: String, def: T): MutableState<T> =
        cache.getOrPut(key) { mutableStateOf(def as Any?) } as MutableState<T>

    fun init(c: Context) {
        if (sp != null) return
        val p = Prefs.get(c)
        sp = p
        val legacy = LinkedHashMap<String, String>()
        listOf("api_key", "asr_key", "tts_key", "a2a_token", "tavily_key").forEach { k ->
            p.getString(k, "")?.takeIf { it.isNotEmpty() }?.let { v -> legacy[k] = v }
        }
        p.all().forEach { (k, v) ->
            if (v is String && v.isNotEmpty() && (k.startsWith("profkey_") || (k.startsWith("search_cfg_") && k.endsWith("_key")))) {
                legacy[k] = v
            }
        }
        val legacyUser = LinkedHashMap<String, String>()
        listOf("fb_token", "ssh_pass").forEach { k ->
            p.getString(k, "")?.takeIf { it.isNotEmpty() }?.let { v -> legacyUser[k] = v }
        }
        if (legacy.isNotEmpty() || legacyUser.isNotEmpty()) {
            if (legacy.isNotEmpty()) Vault.adopt(legacy, Vault.API)
            if (legacyUser.isNotEmpty()) Vault.adopt(legacyUser, Vault.USER)
            val e = p.edit()
            legacy.keys.forEach { e.remove(it) }
            legacyUser.keys.forEach { e.remove(it) }
            e.apply()
        }
        runCatching {
            fun move(k: String, v: String) {
                if (v.isNotBlank() && Vault.get(k, Vault.SSH).isBlank()) Vault.put(k, v, Vault.SSH)
            }
            move("host", p.getString("ssh_host", "").orEmpty())
            val port = p.getInt("ssh_port", 0)
            if (port in 1..65535) move("port", port.toString())
            move("user", p.getString("ssh_user", "").orEmpty())
            move("pass", Vault.get("ssh_pass", Vault.USER))
            Vault.put("ssh_pass", "", Vault.USER)
            p.edit().remove("ssh_host").remove("ssh_port").remove("ssh_user").apply()
        }
        mem("onboarded", false).value = p.getBoolean("onboarded", false)
        //引导停在哪一步也要恢复：中途退回桌面/被系统回收后再进来，接着上次的步骤走
        mem("onboard_step", 0).value = p.getInt("onboard_step", 0)
        //旧自有服务器账号体系已删除：清掉残留的 acc_* 与加密库里的 token
        runCatching {
            Vault.put("acc_token", "", Vault.USER)
            p.edit().remove("acc_token").remove("acc_uid").remove("acc_uname")
                .remove("acc_nick").remove("acc_avatar").remove("acc_onboarded").apply()
        }
        mem("a11y_done", false).value = p.getBoolean("a11y_done", false)
        mem("join_dismissed", false).value = p.getBoolean("join_dismissed", false)
        mem("join_seen", 0).value = p.getInt("join_seen", 0)
        mem("join_joined", false).value = p.getBoolean("join_joined", false)
        mem("used_sec", 0).value = p.getInt("used_sec", 0)
        mem("star_shown", false).value = p.getBoolean("star_shown", false)
        mem("no_rate", false).value = p.getBoolean("no_rate", false)
        mem("fb_token", "").value = Vault.get("fb_token", Vault.USER)
        mem("fb_email", "").value = p.getString("fb_email", "") ?: ""
        mem("tos_agreed", 0).value = p.getInt("tos_agreed", 0)
        mem("tos_sent", 0).value = p.getInt("tos_sent", 0)
        mem("work_mode", WorkMode.NORMAL.id).value = p.getString("work_mode", WorkMode.NORMAL.id)!!
        mem("agent_mode", AgentMode.STANDARD.id).value = p.getString("agent_mode", AgentMode.STANDARD.id)!!
        mem("zero_width", false).value = p.getBoolean("zero_width", false)
        mem("llm_threads", 0).value = p.getInt("llm_threads", 0)
        mem("llm_ctx", 4096).value = p.getInt("llm_ctx", 4096)
        mem("llm_gpu", 0).value = p.getInt("llm_gpu", 0)
        mem("llm_temp", 0.8f).value = p.getFloat("llm_temp", 0.8f)
        mem("llm_top_p", 0.9f).value = p.getFloat("llm_top_p", 0.9f)
        mem("llm_rep", 1.1f).value = p.getFloat("llm_rep", 1.1f)
        mem("llm_model", "").value = p.getString("llm_model", "")!!
        mem("provider_id", "deepseek").value = p.getString("provider_id", "deepseek")!!
        mem("base_url", "").value = p.getString("base_url", "")!!
        mem("http_confirmed", false).value = p.getBoolean("http_confirmed", false)
        mem("profile_models", emptyList<String>())
        mem("pet_model_tip", false).value = p.getBoolean("pet_model_tip", false)
        mem("api_key", "").value = Vault.get("api_key")
        mem("model", "").value = p.getString("model", "")!!
        mem("workflow", "general").value = p.getString("workflow", "general")!!
        mem("ctx_auto", true).value = p.getBoolean("ctx_auto", true)
        mem("ctx_budget", 12000).value = p.getInt("ctx_budget", 12000)
        mem("ctx_keep", 4).value = p.getInt("ctx_keep", 4)
        mem("ctx_msg_count", 16).value = p.getInt("ctx_msg_count", 16)
        mem("summary_prompt", "").value = p.getString("summary_prompt", "")!!
        if (p.contains("summary_level")) {
            mem("summary_level", 2).value = p.getInt("summary_level", 1).coerceIn(0, 2)
        } else {
            //新装用户没有旧的字数配置：直接给「全面」，跟 getter 默认值保持一致
            if (!p.contains("summary_words")) {
                mem("summary_level", 2).value = 2
            } else {
                val old = p.getInt("summary_words", 300)
                mem("summary_level", 2).value = when {
                    old <= 0 -> 2
                    old <= 150 -> 0
                    old <= 800 -> 1
                    else -> 2
                }
            }
        }
        mem("summary_profile", "").value = p.getString("summary_profile", "")!!
        mem("tool_budget_tokens", 0).value = p.getInt("tool_budget_tokens", 0)
        mem("think", false).value = p.getBoolean("think", false)
        mem("think_effort", "medium").value = p.getString("think_effort", "medium")!!
        mem("temperature", -1f).value = p.getFloat("temperature", -1f)
        mem("model_vision", true).value = p.getBoolean("model_vision", true)
        mem("model_tool_call", true).value = p.getBoolean("model_tool_call", true)
        mem("model_reasoning", true).value = p.getBoolean("model_reasoning", true)
        mem("tavily_key", "").value = Vault.get("tavily_key")
        mem("search_provider", "").value = p.getString("search_provider", "")!!
        mem("persona_id", "").value = p.getString("persona_id", "")!!
        mem("system_prompt", "").value = p.getString("system_prompt", "")!!
        mem("extra_params", "").value = p.getString("extra_params", "")!!
        mem("workspace", "").value = p.getString("workspace", "")!!
        mem("default_workspace", "").value = p.getString("default_workspace", "")!!
        mem("font_scale", 1f).value = p.getFloat("font_scale", 1f)
        mem("float_input", false).value = p.getBoolean("float_input", false)
        //回车发送开关：默认开（与旧行为一致），关掉后回车只换行
        mem("enter_send", true).value = p.getBoolean("enter_send", true)
        mem("voice_input", false).value = p.getBoolean("voice_input", false)
        mem("predictive_back", true).value = p.getBoolean("predictive_back", true)
        mem("auto_bind_workspace", true).value = p.getBoolean("auto_bind_workspace", true)
        mem("session_workspace", "").value = p.getString("session_workspace", "")!!
        mem("session_ws_manual", false).value = p.getBoolean("session_ws_manual", false)
        mem("ws_migrated", false).value = p.getBoolean("ws_migrated", false)
        mem("hint_text", "").value = p.getString("hint_text", "")!!
        mem("profile_id", "default").value = p.getString("profile_id", "default")!!
        mem("max_tokens", 0).value = p.getInt("max_tokens", 0)
        mem("lang", "").value = p.getString("lang", "")!!
        mem("ann_seen", "").value = p.getString("ann_seen", "")!!
        mem("ssh_host", "").value = Vault.get("host", Vault.SSH)
        mem("ssh_port", 22).value = Vault.get("port", Vault.SSH).toIntOrNull()?.takeIf { it in 1..65535 } ?: 22
        mem("ssh_user", "").value = Vault.get("user", Vault.SSH)
        mem("ssh_pass", "").value = Vault.get("pass", Vault.SSH)
        mem("gh_user", "").value = Vault.get("gh_user", Vault.SSH)
        mem("gh_token", "").value = Vault.get("gh_token", Vault.SSH)
        mem("max_mode", false).value = p.getBoolean("max_mode", false)
        mem("ctx_total", 128000).value = p.getInt("ctx_total", 128000)
        mem("ctx_pct", 70).value = p.getInt("ctx_pct", 70)
        mem("preset_seed", 0).value = p.getInt("preset_seed", 0)
        mem("persona_seed", 0).value = p.getInt("persona_seed", 0)
        mem("dark_theme", 0).value = p.getInt("dark_theme", 0)
        mem("inject_time", true).value = p.getBoolean("inject_time", true)
        //编排：这几项不回读就会「改完重启回默认」，必须逐个对齐同名 key
        mem("plan_first", false).value = p.getBoolean("plan_first", false)
        mem("plan_dag", false).value = p.getBoolean("plan_dag", false)
        mem("plan_complex_only", true).value = p.getBoolean("plan_complex_only", true)
        mem("plan_show", true).value = p.getBoolean("plan_show", true)
        mem("plan_retry", true).value = p.getBoolean("plan_retry", true)
        mem("plan_max_steps", 0).value = p.getInt("plan_max_steps", 0)
        mem("plan_extra", "").value = p.getString("plan_extra", "").orEmpty()
        mem("inject_battery", false).value = p.getBoolean("inject_battery", false)
        mem("inject_place", false).value = p.getBoolean("inject_place", false)
        mem("inject_precise", false).value = p.getBoolean("inject_precise", false)
        mem("place_geo", true).value = p.getBoolean("place_geo", true)
        mem("inject_noti", false).value = p.getBoolean("inject_noti", false)
        mem("noti_limit", 0).value = p.getInt("noti_limit", 0)
        mem("inject_usage", false).value = p.getBoolean("inject_usage", false)
        mem("auto_a11y", false).value = p.getBoolean("auto_a11y", false)
        mem("show_usage", true).value = p.getBoolean("show_usage", true)
        mem("price_in", 0f).value = p.getFloat("price_in", 0f)
        mem("price_out", 0f).value = p.getFloat("price_out", 0f)
        mem("price_cache", 0f).value = p.getFloat("price_cache", 0f)
        mem("show_meta", true).value = p.getBoolean("show_meta", true)
        mem("no_stream", false).value = p.getBoolean("no_stream", false)
        mem("mask_out", false).value = p.getBoolean("mask_out", false)
        mem("no_shot", false).value = p.getBoolean("no_shot", false)
        mem("speak_replies", false).value = p.getBoolean("speak_replies", false)
        mem("profile_name", "").value = p.getString("profile_name", "")!!
        mem("profile_custom", false).value = p.getBoolean("profile_custom", false)
        mem("avatar_on", true).value = p.getBoolean("avatar_on", true)
        mem("avatar_user", "").value = p.getString("avatar_user", "")!!
        mem("avatar_ai_mode", "persona").value = p.getString("avatar_ai_mode", "persona")!!
        mem("avatar_ai", "").value = p.getString("avatar_ai", "")!!
        mem("bubble_capsule", false).value = p.getBoolean("bubble_capsule", false)
        mem("chat_card_style", 0).value = p.getInt("chat_card_style", 0)
        mem("glass_card", false).value = p.getBoolean("glass_card", false)
        mem("tactile_btn", false).value = p.getBoolean("tactile_btn", false)
        mem("glass_bar", false).value = p.getBoolean("glass_bar", false)
        mem("glass_blur", 4).value = p.getInt("glass_blur", 4)
        mem("glass_lens", 8).value = p.getInt("glass_lens", 8)
        mem("glass_hi", 50).value = p.getInt("glass_hi", 50)
        mem("glass_veil", 12).value = p.getInt("glass_veil", 12)
        mem("theme_tint", 0).value = p.getInt("theme_tint", 0)
        mem("bg_style", 0).value = p.getInt("bg_style", 0)
        val bg = p.getString("bg_img", "")!!
        val bgAlive = bg.isNotBlank() && java.io.File(bg).exists()
        if (bg.isNotBlank() && !bgAlive) put("bg_img", "")
        mem("bg_img", "").value = if (bgAlive) bg else ""
        mem("bg_blur", 0).value = p.getInt("bg_blur", 0)
        mem("wall_tint", false).value = p.getBoolean("wall_tint", false)
        mem("wall_tint_seed", 0).value = p.getInt("wall_tint_seed", 0)
        mem("profile_seed", 0).value = p.getInt("profile_seed", 0)
        mem("waifu_mode", false).value = p.getBoolean("waifu_mode", false)
        mem("waifu_bounce", true).value = p.getBoolean("waifu_bounce", true)
        mem("waifu_real", false).value = p.getBoolean("waifu_real", false)
        mem("waifu_type_ms", 45).value = p.getInt("waifu_type_ms", 45)
        mem("waifu_typing", true).value = p.getBoolean("waifu_typing", true)
        if (!Store.exists(SYSTEM_PROMPT_FILE)) {
            val v = p.getString("system_prompt", "")!!
            if (v.isNotBlank()) {
                Store.write(SYSTEM_PROMPT_FILE, v)
                mem("system_prompt", "").value = v
            }
        } else {
            mem("system_prompt", "").value = Store.read(SYSTEM_PROMPT_FILE) ?: p.getString("system_prompt", "")!!
        }
        mem("pet_on", false).value = p.getBoolean("pet_on", false)
        mem("pet_global", false).value = p.getBoolean("pet_global", false)
        mem("pet_x", -1).value = p.getInt("pet_x", -1)
        mem("pet_y", -1).value = p.getInt("pet_y", -1)
        mem("pet_size", 120).value = p.getInt("pet_size", 120)
        mem("pet_seen", 0).value = p.getInt("pet_seen", 0)
        mem("pet_alpha", 100).value = p.getInt("pet_alpha", 100)
        mem("pet_flip", false).value = p.getBoolean("pet_flip", false)
        mem("pet_shadow", true).value = p.getBoolean("pet_shadow", true)
        mem("pet_card", false).value = p.getBoolean("pet_card", false)
        mem("pet_font", 14).value = p.getInt("pet_font", 14)
        mem("pet_font_bold", true).value = p.getBoolean("pet_font_bold", true)
        mem("pet_bubble_w", 210).value = p.getInt("pet_bubble_w", 210)
        mem("pet_bubble_r", 14).value = p.getInt("pet_bubble_r", 14)
        mem("pet_bubble_dark", false).value = p.getBoolean("pet_bubble_dark", false)
        mem("pet_float", true).value = p.getBoolean("pet_float", true)
        mem("pet_float_amp", 4).value = p.getInt("pet_float_amp", 4)
        mem("pet_float_ms", 1600).value = p.getInt("pet_float_ms", 1600)
        mem("pet_swim", false).value = p.getBoolean("pet_swim", false)
        mem("pet_swim_speed", 60).value = p.getInt("pet_swim_speed", 60)
        mem("pet_swim_bounce", true).value = p.getBoolean("pet_swim_bounce", true)
        mem("pet_bounce", true).value = p.getBoolean("pet_bounce", true)
        mem("pet_bounce_pct", 14).value = p.getInt("pet_bounce_pct", 14)
        mem("pet_talk", true).value = p.getBoolean("pet_talk", true)
        mem("pet_bubble_ms", 2600).value = p.getInt("pet_bubble_ms", 2600)
        mem("pet_timed_pct", 35).value = p.getInt("pet_timed_pct", 35)
        mem("pet_dbl_bal", true).value = p.getBoolean("pet_dbl_bal", true)
        mem("pet_sound", true).value = p.getBoolean("pet_sound", true)
        mem("pet_sound_vol", 70).value = p.getInt("pet_sound_vol", 70)
        mem("fc_on", false).value = p.getBoolean("fc_on", false)
        mem("fc_w", 300).value = p.getInt("fc_w", 300)
        mem("fc_h", 430).value = p.getInt("fc_h", 430)
        mem("fc_font", 13).value = p.getInt("fc_font", 13)
        mem("fc_alpha", 100).value = p.getInt("fc_alpha", 100)
        mem("fc_x", -1).value = p.getInt("fc_x", -1)
        mem("fc_y", -1).value = p.getInt("fc_y", -1)
        mem("dev_show", false).value = p.getBoolean("dev_show", false)
        mem("dev_on", false).value = p.getBoolean("dev_on", false)
        mem("mig_clean", false).value = p.getBoolean("mig_clean", false)
        mem("linux_mirror_node", "").value = p.getString("linux_mirror_node", "")!!
        mem("a2a_on", false).value = p.getBoolean("a2a_on", false)
        mem("a2a_port", 8720).value = p.getInt("a2a_port", 8720)
        mem("a2a_token", "").value = Vault.get("a2a_token")
        mem("tts_mode", "sys").value = p.getString("tts_mode", "sys")!!
        mem("tts_base", "").value = p.getString("tts_base", "")!!
        mem("tts_key", "").value = Vault.get("tts_key")
        mem("tts_model", "tts-1").value = p.getString("tts_model", "tts-1")!!
        mem("tts_voice", "alloy").value = p.getString("tts_voice", "alloy")!!
        mem("asr_mode", "sys").value = p.getString("asr_mode", "sys")!!
        mem("asr_base", "").value = p.getString("asr_base", "")!!
        mem("asr_key", "").value = Vault.get("asr_key")
        mem("asr_model", "whisper-1").value = p.getString("asr_model", "whisper-1")!!
        mem("asr_lang", "zh-CN").value = p.getString("asr_lang", "zh-CN")!!
        mem("asr_vendor", "openai").value = p.getString("asr_vendor", "openai")!!
        mem("asr_rate", 16000).value = p.getInt("asr_rate", 16000)
        mem("asr_max_sec", 60).value = p.getInt("asr_max_sec", 60)
        mem("asr_offline", false).value = p.getBoolean("asr_offline", false)
        //语音整组都要回读：之前只读了 asr_offline，引擎/目录/自定义下载地址改完重启全回默认
        mem("asr_engine", "").value = p.getString("asr_engine", "")!!
        mem("asr_mode", "sys").value = p.getString("asr_mode", "sys")!!
        mem("asr_vendor", "openai").value = p.getString("asr_vendor", "openai")!!
        mem("asr_model", "whisper-1").value = p.getString("asr_model", "whisper-1")!!
        mem("asr_base", "").value = p.getString("asr_base", "")!!
        mem("asr_lang", "zh-CN").value = p.getString("asr_lang", "zh-CN")!!
        mem("asr_dg_model", "nova-3").value = p.getString("asr_dg_model", "nova-3")!!
        mem("asr_dg_url", "https://api.deepgram.com").value = p.getString("asr_dg_url", "https://api.deepgram.com")!!
        mem("asr_sherpa_dir", "").value = p.getString("asr_sherpa_dir", "")!!
        mem("asr_sherpa_src", "").value = p.getString("asr_sherpa_src", "")!!
        mem("asr_key", "").value = Vault.get("asr_key")
        mem("asr_dg_key", "").value = Vault.get("asr_dg_key")
        mem("plg_installed", "").value = p.getString("plg_installed", "")!!
        mem("pl_email", "").value = p.getString("pl_email", "")!!
        mem("paste_to_file", true).value = p.getBoolean("paste_to_file", true)
        mem("paste_limit", 3000).value = p.getInt("paste_limit", 3000)
        // 少了这几行，改完重启就回默认（C 端后缀 / 余额通知开关 / 余额阈值 / 余额查询来源）
        mem("import_suffix", true).value = p.getBoolean("import_suffix", true)
        mem("bal_notify", true).value = p.getBoolean("bal_notify", true)
        mem("bal_threshold", 2).value = p.getInt("bal_threshold", 2)
        mem("bal_profile_id", "").value = p.getString("bal_profile_id", "")!!
        mem("full_input_new", true).value = p.getBoolean("full_input_new", true)
        mem("island_on", true).value = p.getBoolean("island_on", true)
        mem("island_done", true).value = p.getBoolean("island_done", true)
        mem("island_done_sec", 20).value = p.getInt("island_done_sec", 20)
        mem("card_alpha", 100).value = p.getInt("card_alpha", 100)
        mem("bk_secrets", true).value = p.getBoolean("bk_secrets", true)
        mem("models_rev", 0L).value = p.getLong("models_rev", 0L)
        mem("models_file_at", 0L).value = p.getLong("models_file_at", 0L)
        mem("models_snap", "").value = p.getString("models_snap", "")!!
        mem("skills_rev", 0L).value = p.getLong("skills_rev", 0L)
        mem("skills_file_at", 0L).value = p.getLong("skills_file_at", 0L)
        mem("title_profile", "").value = p.getString("title_profile", "")!!
        mem("llm_local", false).value = p.getBoolean("llm_local", false)
        mem("llm_max_tok", 512).value = p.getInt("llm_max_tok", 512)
        mem("card_corner", 16).value = p.getInt("card_corner", 16)
        mem("term_font", 10).value = p.getInt("term_font", 10)
        mem("term_font_file", "").value = p.getString("term_font_file", "")!!
        mem("term_fg", 0xFFD5DBE5.toInt()).value = p.getInt("term_fg", 0xFFD5DBE5.toInt())
        mem("term_bg", 0xFF0E1116.toInt()).value = p.getInt("term_bg", 0xFF0E1116.toInt())
        mem("ui_font_file", "").value = p.getString("ui_font_file", "")!!
        mem("user_font_file", "").value = p.getString("user_font_file", "")!!
        mem("ai_font_file", "").value = p.getString("ai_font_file", "")!!
        mem("global_indent", 0).value = p.getInt("global_indent", 0)
        mem("dialog_corner", 20).value = p.getInt("dialog_corner", 20)
        mem("slider_accent", false).value = p.getBoolean("slider_accent", false)
        mem("slider_track", 18).value = p.getInt("slider_track", 18)
        mem("slider_track_h", 26).value = p.getInt("slider_track_h", 26)
        mem("md_font", 13).value = p.getInt("md_font", 13)
        mem("md_line", 155).value = p.getInt("md_line", 155)
        mem("md_gap", 6).value = p.getInt("md_gap", 6)
        mem("md_indent", 12).value = p.getInt("md_indent", 12)
        mem("md_pad", 0).value = p.getInt("md_pad", 0)
        mem("md_code", 12).value = p.getInt("md_code", 12)

        //本轮补齐：这些键以前只写盘、启动时没回读，重启后会退回默认值
        mem("draft_days", 7).value = p.getInt("draft_days", 7)
        mem("draft_keep_img", false).value = p.getBoolean("draft_keep_img", false)
        mem("dsh_engine", false).value = p.getBoolean("dsh_engine", false)
        mem("frequency_penalty", -9f).value = p.getFloat("frequency_penalty", -9f)
        mem("inject_device", true).value = p.getBoolean("inject_device", true)
        mem("inject_mem", true).value = p.getBoolean("inject_mem", true)
        mem("inject_mem_count", 35).value = p.getInt("inject_mem_count", 35)
        mem("mem_profile_id", "").value = p.getString("mem_profile_id", "")!!
        mem("model_native_search", false).value = p.getBoolean("model_native_search", false)
        //两个采样参数能力开关：老用户没有这两个键时默认 true，行为跟升级前一致
        mem("model_creative", false).value = p.getBoolean("model_creative", false)
        mem("model_repeat", false).value = p.getBoolean("model_repeat", false)
        mem("model_protocol", "chat").value = p.getString("model_protocol", "chat")!!
        mem("presence_penalty", -9f).value = p.getFloat("presence_penalty", -9f)
        mem("prompt_opt_extra_on", false).value = p.getBoolean("prompt_opt_extra_on", false)
        mem("prompt_opt_keep", true).value = p.getBoolean("prompt_opt_keep", true)
        mem("prompt_opt_lang", "auto").value = p.getString("prompt_opt_lang", "auto")!!
        mem("prompt_opt_level", "auto").value = p.getString("prompt_opt_level", "auto")!!
        mem("prompt_opt_noask", true).value = p.getBoolean("prompt_opt_noask", true)
        mem("prompt_opt_pref_on", false).value = p.getBoolean("prompt_opt_pref_on", false)
        mem("prompt_opt_profile", "").value = p.getString("prompt_opt_profile", "")!!
        mem("prompt_opt_style", "").value = p.getString("prompt_opt_style", "")!!
        mem("prompt_opt_tone", "auto").value = p.getString("prompt_opt_tone", "auto")!!
        mem("prompt_order", 0).value = p.getInt("prompt_order", 0)
        mem("repeat_penalty", 0f).value = p.getFloat("repeat_penalty", 0f)
        mem("reply_style", "").value = p.getString("reply_style", "")!!
        mem("scroll_btn_style", 0).value = p.getInt("scroll_btn_style", 0)
        mem("show_first", true).value = p.getBoolean("show_first", true)
        mem("show_latency", true).value = p.getBoolean("show_latency", true)
        mem("show_model", true).value = p.getBoolean("show_model", true)
        mem("show_provider", true).value = p.getBoolean("show_provider", true)
        mem("show_speed", true).value = p.getBoolean("show_speed", true)
        mem("show_time", true).value = p.getBoolean("show_time", true)
        mem("think_level", 0).value = p.getInt("think_level", 0)
        mem("think_rules", "").value = p.getString("think_rules", "")!!
        mem("top_k", 0).value = p.getInt("top_k", 0)
        mem("top_p", -1f).value = p.getFloat("top_p", -1f)
    }

    private fun put(k: String, v: Any?) {
        val e = sp?.edit() ?: return
        when (v) {
            is Boolean -> e.putBoolean(k, v)
            is Int -> e.putInt(k, v)
            is Float -> e.putFloat(k, v)
            else -> e.putString(k, v as? String ?: "")
        }
        e.apply()
    }

    private fun putNow(k: String, v: Any?) {
        val e = sp?.edit() ?: return
        when (v) {
            is Boolean -> e.putBoolean(k, v)
            is Int -> e.putInt(k, v)
            is Long -> e.putLong(k, v)
            is Float -> e.putFloat(k, v)
            else -> e.putString(k, v as? String ?: "")
        }
        e.commit()
    }

    var modelsRev: Long
        get() = mem("models_rev", 0L).value
        set(v) { mem("models_rev", 0L).value = v; putNow("models_rev", v) }
    var modelsFileAt: Long
        get() = mem("models_file_at", 0L).value
        set(v) { mem("models_file_at", 0L).value = v; putNow("models_file_at", v) }

    var modelsSnap: String
        get() = mem("models_snap", "").value
        set(v) { mem("models_snap", "").value = v; putNow("models_snap", v) }

    var skillsRev: Long
        get() = mem("skills_rev", 0L).value
        set(v) { mem("skills_rev", 0L).value = v; putNow("skills_rev", v) }
    var skillsFileAt: Long
        get() = mem("skills_file_at", 0L).value
        set(v) { mem("skills_file_at", 0L).value = v; putNow("skills_file_at", v) }

    var onboarded: Boolean
        get() = mem("onboarded", false).value
        set(v) { mem("onboarded", false).value = v; put("onboarded", v) }
    /** 引导流程停在哪一步（0..7）：中途退出/进程被杀后回来接着走，不再从语言页重来 */
    var onboardStep: Int
        get() = mem("onboard_step", 0).value
        set(v) { mem("onboard_step", 0).value = v; put("onboard_step", v) }
    var a11yDone: Boolean
        get() = mem("a11y_done", false).value
        set(v) { mem("a11y_done", false).value = v; put("a11y_done", v) }

    var joinDismissed: Boolean
        get() = mem("join_dismissed", false).value
        set(v) { mem("join_dismissed", false).value = v; put("join_dismissed", v) }

    var joinSeen: Int
        get() = mem("join_seen", 0).value
        set(v) { mem("join_seen", 0).value = v; put("join_seen", v) }

    var annSeen: String
        get() = mem("ann_seen", "").value
        set(v) { mem("ann_seen", "").value = v; put("ann_seen", v) }

    var sshHost: String
        get() = mem("ssh_host", "").value
        set(v) { mem("ssh_host", "").value = v; Vault.put("host", v.trim(), Vault.SSH) }
    var sshPort: Int
        get() = mem("ssh_port", 22).value
        set(v) {
            val n = if (v in 1..65535) v else 22
            mem("ssh_port", 22).value = n
            Vault.put("port", n.toString(), Vault.SSH)
        }
    var sshUser: String
        get() = mem("ssh_user", "").value
        set(v) { mem("ssh_user", "").value = v; Vault.put("user", v.trim(), Vault.SSH) }
    var sshPass: String
        get() = mem("ssh_pass", "").value
        set(v) { mem("ssh_pass", "").value = v; Vault.put("pass", v, Vault.SSH) }

    var ghUser: String
        get() = mem("gh_user", "").value
        set(v) { mem("gh_user", "").value = v; Vault.put("gh_user", v.trim(), Vault.SSH) }
    var ghToken: String
        get() = mem("gh_token", "").value
        set(v) { mem("gh_token", "").value = v; Vault.put("gh_token", v.trim(), Vault.SSH) }

    var joinJoined: Boolean
        get() = mem("join_joined", false).value
        set(v) { mem("join_joined", false).value = v; put("join_joined", v) }

    var usedSec: Int
        get() = mem("used_sec", 0).value
        set(v) { mem("used_sec", 0).value = v; put("used_sec", v) }

    var starShown: Boolean
        get() = mem("star_shown", false).value
        set(v) { mem("star_shown", false).value = v; put("star_shown", v) }

    var noRate: Boolean
        get() = mem("no_rate", false).value
        set(v) { mem("no_rate", false).value = v; put("no_rate", v) }

    var fbToken: String
        get() = mem("fb_token", "").value
        set(v) { mem("fb_token", "").value = v; Vault.put("fb_token", v, Vault.USER) }

    var fbEmail: String
        get() = mem("fb_email", "").value
        set(v) { mem("fb_email", "").value = v; put("fb_email", v) }

    var plEmail: String
        get() = mem("pl_email", "").value
        set(v) { mem("pl_email", "").value = v; put("pl_email", v) }

    var plgInstalled: String
        get() = mem("plg_installed", "").value
        set(v) { mem("plg_installed", "").value = v; put("plg_installed", v) }

    var tosAgreed: Int
        get() = mem("tos_agreed", 0).value
        set(v) { mem("tos_agreed", 0).value = v; put("tos_agreed", v) }

    var migClean: Boolean
        get() = mem("mig_clean", false).value
        set(v) { mem("mig_clean", false).value = v; put("mig_clean", v) }

    var tosSent: Int
        get() = mem("tos_sent", 0).value
        set(v) { mem("tos_sent", 0).value = v; put("tos_sent", v) }

    var lang: String
        get() = mem("lang", "").value
        set(v) { mem("lang", "").value = v; put("lang", v) }

    var llmThreads: Int
        get() = mem("llm_threads", 0).value
        set(v) { mem("llm_threads", 0).value = v; put("llm_threads", v) }

    var llmCtx: Int
        get() = mem("llm_ctx", 4096).value
        set(v) { mem("llm_ctx", 4096).value = v; put("llm_ctx", v) }

    var llmGpu: Int
        get() = mem("llm_gpu", 0).value
        set(v) { mem("llm_gpu", 0).value = v; put("llm_gpu", v) }

    var llmMaxTok: Int
        get() = mem("llm_max_tok", 512).value
        set(v) { mem("llm_max_tok", 512).value = v; put("llm_max_tok", v) }
    var llmTemp: Float
        get() = mem("llm_temp", 0.8f).value
        set(v) { mem("llm_temp", 0.8f).value = v; put("llm_temp", v) }
    var llmTopP: Float
        get() = mem("llm_top_p", 0.9f).value
        set(v) { mem("llm_top_p", 0.9f).value = v; put("llm_top_p", v) }
    var llmRep: Float
        get() = mem("llm_rep", 1.1f).value
        set(v) { mem("llm_rep", 1.1f).value = v; put("llm_rep", v) }

    var llmModel: String
        get() = mem("llm_model", "").value
        set(v) { mem("llm_model", "").value = v; put("llm_model", v) }

    var llmLocal: Boolean
        get() = mem("llm_local", false).value
        set(v) { mem("llm_local", false).value = v; put("llm_local", v) }
    var workMode: WorkMode
        get() = WorkMode.of(mem("work_mode", WorkMode.NORMAL.id).value)
        set(v) { mem("work_mode", WorkMode.NORMAL.id).value = v.id; put("work_mode", v.id) }

    var agentMode: AgentMode
        get() = AgentMode.of(mem("agent_mode", AgentMode.STANDARD.id).value)
        set(v) { mem("agent_mode", AgentMode.STANDARD.id).value = v.id; put("agent_mode", v.id) }

    var zeroWidth: Boolean
        get() = mem("zero_width", false).value
        set(v) { mem("zero_width", false).value = v; put("zero_width", v) }

    var providerId: String
        get() = mem("provider_id", "deepseek").value
        set(v) { mem("provider_id", "deepseek").value = v; put("provider_id", v) }

    var baseUrl: String
        get() = mem("base_url", "").value
        set(v) { mem("base_url", "").value = v; put("base_url", v) }
    var httpConfirmed: Boolean
        get() = mem("http_confirmed", false).value
        set(v) { mem("http_confirmed", false).value = v; put("http_confirmed", v) }

    var profileModels: List<String>
        get() = mem("profile_models", emptyList<String>()).value
        set(v) { mem("profile_models", emptyList<String>()).value = v }
    var petModelTip: Boolean
        get() = mem("pet_model_tip", false).value
        set(v) { mem("pet_model_tip", false).value = v; put("pet_model_tip", v) }

    var apiKey: String
        get() = mem("api_key", "").value
        set(v) { mem("api_key", "").value = v; Vault.put("api_key", v) }

    var model: String
        get() = mem("model", "").value
        set(v) { mem("model", "").value = v; put("model", v) }

    var workflow: String
        get() = mem("workflow", "general").value
        set(v) { mem("workflow", "general").value = v; put("workflow", v) }

    var ctxAuto: Boolean
        get() = mem("ctx_auto", true).value
        set(v) { mem("ctx_auto", true).value = v; put("ctx_auto", v) }

    /** C 端后缀开关（开关本体在「从 DeepSeek 导入历史」弹窗里）：开 = 导入的会话标题带「（DeepSeek）/（导入）」，关 = 按原标题 */
    var importSuffix: Boolean
        get() = mem("import_suffix", true).value
        set(v) { mem("import_suffix", true).value = v; put("import_suffix", v) }

    /** 余额通知：每次进 App 自动查一次余额，低于阈值就发通知 */
    var balNotify: Boolean
        get() = mem("bal_notify", true).value
        set(v) { mem("bal_notify", true).value = v; put("bal_notify", v) }

    /** 余额阈值（元）：低于这个数才提醒，1~100 */
    var balThreshold: Int
        get() = mem("bal_threshold", 2).value
        set(v) { mem("bal_threshold", 2).value = v; put("bal_threshold", v) }

    /**
     * 余额查询来源：空 = 自动（DeepSeek 官方优先 → 列表第一条）；
     * 非空 = 指定某条模型配置的 id（Profiles.list 里的 id），余额只查这条的 Key
     */
    var balProfileId: String
        get() = mem("bal_profile_id", "").value
        set(v) { mem("bal_profile_id", "").value = v; put("bal_profile_id", v) }

    var ctxBudget: Int
        get() = mem("ctx_budget", 12000).value
        set(v) { mem("ctx_budget", 12000).value = v; put("ctx_budget", v) }

    var ctxKeep: Int
        get() = mem("ctx_keep", 4).value
        set(v) { mem("ctx_keep", 4).value = v; put("ctx_keep", v) }

    var ctxMsgCount: Int
        get() = mem("ctx_msg_count", 16).value
        set(v) { mem("ctx_msg_count", 16).value = v; put("ctx_msg_count", v) }

    var summaryPrompt: String
        get() = mem("summary_prompt", "").value
        set(v) { mem("summary_prompt", "").value = v; put("summary_prompt", v) }

    var summaryLevel: Int
        get() = mem("summary_level", 2).value
        set(v) {
            val n = v.coerceIn(0, 2)
            mem("summary_level", 2).value = n
            put("summary_level", n)
        }

    val summaryWords: Int
        get() = when (summaryLevel.coerceIn(0, 2)) {
            //简洁：只是应急档，长对话一定会丢细节，默认不选
            0 -> 1500
            //详细：够放下一个中大型任务的全部现场，别为了短而删
            1 -> 12000
            // 全面（默认）：不设字数上限，由提示词里的 50000 token 硬上限兜住
            else -> 0
        }

    var summaryProfileId: String
        get() = mem("summary_profile", "").value
        set(v) { mem("summary_profile", "").value = v; put("summary_profile", v) }

    var titleProfileId: String
        get() = mem("title_profile", "").value
        set(v) { mem("title_profile", "").value = v; put("title_profile", v) }

    var memProfileId: String
        get() = mem("mem_profile_id", "").value
        set(v) { mem("mem_profile_id", "").value = v; put("mem_profile_id", v) }

    var think: Boolean
        get() = mem("think", false).value
        set(v) { mem("think", false).value = v; put("think", v) }

    var thinkEffort: String
        get() = mem("think_effort", "medium").value
        set(v) { mem("think_effort", "medium").value = v; put("think_effort", v) }

    var thinkLevel: Int
        get() = mem("think_level", 0).value
        set(v) {
            val n = v.coerceIn(0, 5)
            mem("think_level", 0).value = n
            put("think_level", n)
        }

    var thinkRules: String
        get() = mem("think_rules", "").value
        set(v) { mem("think_rules", "").value = v; put("think_rules", v) }

    var promptOptProfileId: String
        get() = mem("prompt_opt_profile", "").value
        set(v) { mem("prompt_opt_profile", "").value = v; put("prompt_opt_profile", v) }

    var promptOptKeep: Boolean
        get() = mem("prompt_opt_keep", true).value
        set(v) { mem("prompt_opt_keep", true).value = v; put("prompt_opt_keep", v) }

    var promptOrder: Int
        get() = mem("prompt_order", 0).value
        set(v) {
            val n = v.coerceIn(0, 1)
            mem("prompt_order", 0).value = n
            put("prompt_order", n)
        }

    /**编排：接到模糊目标先拆子任务再执行，并按执行反馈调整后续计划（设置 →模型 →编排）*/
    var planFirst: Boolean
        get() = mem("plan_first", false).value
        set(v) { mem("plan_first", false).value = v; put("plan_first", v) }

    /**拆解形式：false =步骤链， true = DAG（带依赖边与并行分支）*/
    var planDag: Boolean
        get() = mem("plan_dag", false).value
        set(v) { mem("plan_dag", false).value = v; put("plan_dag", v) }

    /**只在复杂目标时拆解：一句话能做完的直接做，避免为拆解而拆解 */
    var planComplexOnly: Boolean
        get() = mem("plan_complex_only", true).value
        set(v) { mem("plan_complex_only", true).value = v; put("plan_complex_only", v) }

    /**是否把计划展示给用户：关掉时计划只在模型内部用，回复里不贴清单 */
    var planShow: Boolean
        get() = mem("plan_show", true).value
        set(v) { mem("plan_show", true).value = v; put("plan_show", v) }

    /**失败时自动调整后续计划：关掉时要求停下手说明，不自行重排 */
    var planRetry: Boolean
        get() = mem("plan_retry", true).value
        set(v) { mem("plan_retry", true).value = v; put("plan_retry", v) }

    /**子任务上限：0 =不限，其余值要求先在内部合并同类项 */
    var planMaxSteps: Int
        get() = mem("plan_max_steps", 0).value
        set(v) { mem("plan_max_steps", v.coerceIn(0, 50)).value = v.coerceIn(0, 50); put("plan_max_steps", v.coerceIn(0, 50)) }

    /**额外规划要求：原样拼进编排段，留给用户写自己的约束 */
    var planExtra: String
        get() = mem("plan_extra", "").value
        set(v) { mem("plan_extra", "").value = v; put("plan_extra", v) }

    var replyStyle: String
        get() = mem("reply_style", "").value
        set(v) { mem("reply_style", "").value = v; put("reply_style", v) }

    var promptOptStyle: String
        get() = mem("prompt_opt_style", "").value
        set(v) { mem("prompt_opt_style", "").value = v; put("prompt_opt_style", v) }

    /**关掉「保持原语言」后的输出语种：auto = 自动（由模型按原文判断）；其余值 = 强制统一成该语种*/
    var promptOptLang: String
        get() = mem("prompt_opt_lang", "auto").value
        set(v) { mem("prompt_opt_lang", "auto").value = v; put("prompt_opt_lang", v) }

    /**永不询问优化方向：开启后优化器不许反问，信息不足就按最短理解直接改，改不了就原样返回 */
    var promptOptNoAsk: Boolean
        get() = mem("prompt_opt_noask", true).value
        set(v) { mem("prompt_opt_noask", true).value = v; put("prompt_opt_noask", v) }

    /**开启自定义补充优化：关掉时设置页不显示「优化方向自定义」输入框，也不往提示里拼 */
    var promptOptExtraOn: Boolean
        get() = mem("prompt_opt_extra_on", false).value
        set(v) { mem("prompt_opt_extra_on", false).value = v; put("prompt_opt_extra_on", v) }

    /**自定义优化偏好总开关：开关两个下拉（档位 / 风格）才生效 */
    var promptOptPrefOn: Boolean
        get() = mem("prompt_opt_pref_on", false).value
        set(v) { mem("prompt_opt_pref_on", false).value = v; put("prompt_opt_pref_on", v) }

    /**优化偏好档位：auto / brief / full / detail，对应「自动 / 极简 / 全面 / 详细」*/
    var promptOptLevel: String
        get() = mem("prompt_opt_level", "auto").value
        set(v) { mem("prompt_opt_level", "auto").value = v; put("prompt_opt_level", v) }

    /**优化偏好风格：auto / strict / code / draw / science，对应「自动 / 严谨 / 代码 / 绘图 / 科学」*/
    var promptOptTone: String
        get() = mem("prompt_opt_tone", "auto").value
        set(v) { mem("prompt_opt_tone", "auto").value = v; put("prompt_opt_tone", v) }

    var topP: Float
        get() = mem("top_p", -1f).value
        set(v) { mem("top_p", -1f).value = v; put("top_p", v) }

    var topK: Int
        get() = mem("top_k", 0).value
        set(v) { mem("top_k", 0).value = v; put("top_k", v) }

    var presencePenalty: Float
        get() = mem("presence_penalty", -9f).value
        set(v) { mem("presence_penalty", -9f).value = v; put("presence_penalty", v) }

    var frequencyPenalty: Float
        get() = mem("frequency_penalty", -9f).value
        set(v) { mem("frequency_penalty", -9f).value = v; put("frequency_penalty", v) }

    var repeatPenalty: Float
        get() = mem("repeat_penalty", 0f).value
        set(v) { mem("repeat_penalty", 0f).value = v; put("repeat_penalty", v) }

    var toolBudgetTokens: Int
        get() = mem("tool_budget_tokens", 0).value
        set(v) { mem("tool_budget_tokens", 0).value = v; put("tool_budget_tokens", v) }

    var temperature: Float
        get() = mem("temperature", -1f).value
        set(v) { mem("temperature", -1f).value = v; put("temperature", v) }

    var modelVision: Boolean
        get() = mem("model_vision", true).value
        set(v) { mem("model_vision", true).value = v; put("model_vision", v) }

    var modelToolCall: Boolean
        get() = mem("model_tool_call", true).value
        set(v) { mem("model_tool_call", true).value = v; put("model_tool_call", v) }

    var modelReasoning: Boolean
        get() = mem("model_reasoning", true).value
        set(v) { mem("model_reasoning", true).value = v; put("model_reasoning", v) }

    var modelNativeSearch: Boolean
        get() = mem("model_native_search", false).value
        set(v) { mem("model_native_search", false).value = v; put("model_native_search", v) }

    /** 是否向服务端下发创造性采样参数（temperature / top_p / top_k）：不认这些字段的厂商会直接 400 */
    var modelCreative: Boolean
        get() = mem("model_creative", false).value
        set(v) { mem("model_creative", false).value = v; put("model_creative", v) }

    /** 是否向服务端下发重复控制参数（presence / frequency / repeat penalty）：同上，按厂商能力开关 */
    var modelRepeat: Boolean
        get() = mem("model_repeat", false).value
        set(v) { mem("model_repeat", false).value = v; put("model_repeat", v) }

    /** 接口协议：chat = OpenAI Chat Completions（/chat/completions）；responses = OpenAI Responses API（/responses） */
    var modelProtocol: String
        get() = mem("model_protocol", "chat").value
        set(v) { mem("model_protocol", "chat").value = v; put("model_protocol", v) }

    var tavilyKey: String
        get() = mem("tavily_key", "").value
        set(v) { mem("tavily_key", "").value = v; Vault.put("tavily_key", v.trim()) }

    var searchProvider: String
        get() = mem("search_provider", "").value
        set(v) { mem("search_provider", "").value = v; put("search_provider", v) }

    var personaId: String
        get() = mem("persona_id", "").value
        set(v) { mem("persona_id", "").value = v; put("persona_id", v) }

    var personaSeed: Int
        get() = mem("persona_seed", 0).value
        set(v) { mem("persona_seed", 0).value = v; put("persona_seed", v) }

    var avatarOn: Boolean
        get() = mem("avatar_on", true).value
        set(v) { mem("avatar_on", true).value = v; put("avatar_on", v) }

    var avatarUser: String
        get() = mem("avatar_user", "").value
        set(v) { mem("avatar_user", "").value = v; put("avatar_user", v) }

    var avatarAiMode: String
        get() = mem("avatar_ai_mode", "persona").value
        set(v) { mem("avatar_ai_mode", "persona").value = v; put("avatar_ai_mode", v) }

    var bubbleCapsule: Boolean
        get() = mem("bubble_capsule", false).value
        set(v) { mem("bubble_capsule", false).value = v; put("bubble_capsule", v) }

    /**
     * 聊天卡片样式：0 = 分式（每条消息一个气泡，默认，旧行为）；
     * 1 = 整体式（一轮的用户消息 + 该轮所有回复/工具/附件/统计都收进同一张卡，头部可折叠）。
     */
    var chatCardStyle: Int
        get() = mem("chat_card_style", 0).value
        set(v) { mem("chat_card_style", 0).value = v; put("chat_card_style", v) }

    var avatarAi: String
        get() = mem("avatar_ai", "").value
        set(v) { mem("avatar_ai", "").value = v; put("avatar_ai", v) }

    var waifuMode: Boolean
        get() = mem("waifu_mode", false).value
        set(v) { mem("waifu_mode", false).value = v; put("waifu_mode", v) }

    var waifuBounce: Boolean
        get() = mem("waifu_bounce", true).value
        set(v) { mem("waifu_bounce", true).value = v; put("waifu_bounce", v) }

    var waifuReal: Boolean
        get() = mem("waifu_real", false).value
        set(v) { mem("waifu_real", false).value = v; put("waifu_real", v) }

    var waifuTypeMs: Int
        get() = mem("waifu_type_ms", 45).value
        set(v) {
            val n = v.coerceIn(20, 150)
            mem("waifu_type_ms", 45).value = n
            put("waifu_type_ms", n)
        }

    var waifuTyping: Boolean
        get() = mem("waifu_typing", true).value
        set(v) { mem("waifu_typing", true).value = v; put("waifu_typing", v) }

    var systemPrompt: String
        get() = mem("system_prompt", "").value
        set(v) {
            mem("system_prompt", "").value = v
            put("system_prompt", v)

            Store.write(SYSTEM_PROMPT_FILE, v)
        }

    private fun searchCfgKey(provider: String, field: String): String =
        if (provider == "tavily" && field == "key") "tavily_key" else "search_cfg_${provider}_$field"

    private fun searchCfgSecret(field: String): Boolean = field == "key" || field.endsWith("_key")

    fun searchCfg(provider: String, field: String): String {
        if (provider.isBlank() || field.isBlank()) return ""
        val k = searchCfgKey(provider, field)
        val st = mem(k, "")
        if (st.value == "") st.value = if (searchCfgSecret(field)) Vault.get(k) else sp?.getString(k, "") ?: ""
        return st.value
    }

    fun setSearchCfg(provider: String, field: String, v: String) {
        if (provider.isBlank() || field.isBlank()) return
        val k = searchCfgKey(provider, field)
        mem(k, "").value = v
        if (searchCfgSecret(field)) Vault.put(k, v.trim()) else put(k, v)
    }

    var extraParams: String
        get() = mem("extra_params", "").value
        set(v) { mem("extra_params", "").value = v; put("extra_params", v) }

    var workspace: String
        get() = mem("workspace", "").value
        set(v) { mem("workspace", "").value = v; put("workspace", v) }

    var defaultWorkspace: String
        get() = mem("default_workspace", "").value
        set(v) { mem("default_workspace", "").value = v; put("default_workspace", v) }

    var darkTheme: Int
        get() = mem("dark_theme", 0).value
        set(v) { mem("dark_theme", 0).value = v; put("dark_theme", v) }

    var fontScale: Float
        get() = mem("font_scale", 1f).value
        set(v) { mem("font_scale", 1f).value = v; put("font_scale", v) }

    var floatInput: Boolean
        get() = mem("float_input", false).value
        set(v) { mem("float_input", false).value = v; put("float_input", v) }

    /** 回车键是否直接发送；关掉时回车只换行（外接键盘/物理键盘场景用） */
    var enterSend: Boolean
        get() = mem("enter_send", true).value
        set(v) { mem("enter_send", true).value = v; put("enter_send", v) }

    var voiceInput: Boolean
        get() = mem("voice_input", false).value
        set(v) { mem("voice_input", false).value = v; put("voice_input", v) }

    var fullInputNew: Boolean
        get() = mem("full_input_new", true).value
        set(v) { mem("full_input_new", true).value = v; put("full_input_new", v) }

    var pasteToFile: Boolean
        get() = mem("paste_to_file", true).value
        set(v) { mem("paste_to_file", true).value = v; put("paste_to_file", v) }

    var pasteLimit: Int
        get() = mem("paste_limit", 3000).value
        set(v) { mem("paste_limit", 3000).value = v; put("paste_limit", v) }

    var glassCard: Boolean
        get() = mem("glass_card", false).value
        set(v) { mem("glass_card", false).value = v; put("glass_card", v) }

    var cardAlpha: Int
        get() = mem("card_alpha", 100).value
        set(v) { mem("card_alpha", 100).value = v; put("card_alpha", v) }

    var cardCorner: Int
        get() = mem("card_corner", 16).value
        set(v) { mem("card_corner", 16).value = v.coerceIn(0, 32); put("card_corner", v.coerceIn(0, 32)) }

    var scrollBtnStyle: Int
        get() = mem("scroll_btn_style", 0).value
        set(v) { mem("scroll_btn_style", 0).value = v.coerceIn(0, 2); put("scroll_btn_style", v.coerceIn(0, 2)) }

    var termFont: Int
        get() = mem("term_font", 10).value
        set(v) { mem("term_font", 10).value = v.coerceIn(9, 14); put("term_font", v.coerceIn(9, 14)) }

    var termFontFile: String
        get() = mem("term_font_file", "").value
        set(v) { mem("term_font_file", "").value = v; put("term_font_file", v) }

    var termFg: Int
        get() = mem("term_fg", 0xFFD5DBE5.toInt()).value
        set(v) { mem("term_fg", 0xFFD5DBE5.toInt()).value = v; put("term_fg", v) }

    var termBg: Int
        get() = mem("term_bg", 0xFF0E1116.toInt()).value
        set(v) { mem("term_bg", 0xFF0E1116.toInt()).value = v; put("term_bg", v) }

    var uiFontFile: String
        get() = mem("ui_font_file", "").value
        set(v) { mem("ui_font_file", "").value = v; put("ui_font_file", v) }

    var userFontFile: String
        get() = mem("user_font_file", "").value
        set(v) { mem("user_font_file", "").value = v; put("user_font_file", v) }

    var aiFontFile: String
        get() = mem("ai_font_file", "").value
        set(v) { mem("ai_font_file", "").value = v; put("ai_font_file", v) }

    var globalIndent: Int
        get() = mem("global_indent", 0).value
        set(v) { mem("global_indent", 0).value = v.coerceIn(0, 32); put("global_indent", v.coerceIn(0, 32)) }

    var dialogCorner: Int
        get() = mem("dialog_corner", 20).value
        set(v) { mem("dialog_corner", 20).value = v.coerceIn(0, 32); put("dialog_corner", v.coerceIn(0, 32)) }

    var sliderAccent: Boolean
        get() = mem("slider_accent", false).value
        set(v) { mem("slider_accent", false).value = v; put("slider_accent", v) }

    var sliderTrack: Int
        get() = mem("slider_track", 18).value
        set(v) { mem("slider_track", 18).value = v.coerceIn(5, 60); put("slider_track", v.coerceIn(5, 60)) }

    var sliderTrackH: Int
        get() = mem("slider_track_h", 26).value
        set(v) {
            val n = v.coerceIn(12, 48)
            mem("slider_track_h", 26).value = n
            put("slider_track_h", n)
        }

    var mdFont: Int
        get() = mem("md_font", 13).value
        set(v) { mem("md_font", 13).value = v.coerceIn(11, 18); put("md_font", v.coerceIn(11, 18)) }

    var mdLine: Int
        get() = mem("md_line", 155).value
        set(v) { mem("md_line", 155).value = v.coerceIn(100, 220); put("md_line", v.coerceIn(100, 220)) }

    var mdGap: Int
        get() = mem("md_gap", 6).value
        set(v) { mem("md_gap", 6).value = v.coerceIn(0, 20); put("md_gap", v.coerceIn(0, 20)) }

    var mdIndent: Int
        get() = mem("md_indent", 12).value
        set(v) { mem("md_indent", 12).value = v.coerceIn(0, 32); put("md_indent", v.coerceIn(0, 32)) }

    var mdPad: Int
        get() = mem("md_pad", 0).value
        set(v) { mem("md_pad", 0).value = v.coerceIn(0, 24); put("md_pad", v.coerceIn(0, 24)) }

    var mdCode: Int
        get() = mem("md_code", 12).value
        set(v) { mem("md_code", 12).value = v.coerceIn(9, 16); put("md_code", v.coerceIn(9, 16)) }

    var islandOn: Boolean
        get() = mem("island_on", true).value
        set(v) { mem("island_on", true).value = v; put("island_on", v) }

    var islandDone: Boolean
        get() = mem("island_done", true).value
        set(v) { mem("island_done", true).value = v; put("island_done", v) }

    var islandDoneSec: Int
        get() = mem("island_done_sec", 20).value
        set(v) { mem("island_done_sec", 20).value = v; put("island_done_sec", v) }

    var bkSecrets: Boolean
        get() = mem("bk_secrets", true).value
        set(v) { mem("bk_secrets", true).value = v; put("bk_secrets", v) }

    var tactileBtn: Boolean
        get() = mem("tactile_btn", false).value
        set(v) { mem("tactile_btn", false).value = v; put("tactile_btn", v) }

    var glassBar: Boolean
        get() = mem("glass_bar", false).value
        set(v) { mem("glass_bar", false).value = v; put("glass_bar", v) }

    var glassBlur: Int
        get() = mem("glass_blur", 4).value
        set(v) { mem("glass_blur", 4).value = v; put("glass_blur", v) }

    var glassLens: Int
        get() = mem("glass_lens", 8).value
        set(v) { mem("glass_lens", 8).value = v; put("glass_lens", v) }

    var glassHi: Int
        get() = mem("glass_hi", 50).value
        set(v) { mem("glass_hi", 50).value = v; put("glass_hi", v) }

    var glassVeil: Int
        get() = mem("glass_veil", 12).value
        set(v) { mem("glass_veil", 12).value = v; put("glass_veil", v) }

    var themeTint: Int
        get() = mem("theme_tint", 0).value
        set(v) { mem("theme_tint", 0).value = v; put("theme_tint", v) }

    var bgStyle: Int
        get() = mem("bg_style", 0).value
        set(v) { mem("bg_style", 0).value = v; put("bg_style", v) }

    var bgImg: String
        get() = mem("bg_img", "").value
        set(v) { mem("bg_img", "").value = v; put("bg_img", v) }

    var bgBlur: Int
        get() = mem("bg_blur", 0).value
        set(v) { mem("bg_blur", 0).value = v.coerceIn(0, 100); put("bg_blur", v.coerceIn(0, 100)) }

    /** 壁纸取色：开着时色系由背景图主色决定，上面的色系下拉变灰不可选 */
    var wallTint: Boolean
        get() = mem("wall_tint", false).value
        set(v) { mem("wall_tint", false).value = v; put("wall_tint", v) }

    /** 从壁纸取到的种色（ARGB int，0 表示还没取到）；落盘是为了冷启动直接上色，不用再解一次图 */
    var wallTintSeed: Int
        get() = mem("wall_tint_seed", 0).value
        set(v) { mem("wall_tint_seed", 0).value = v; put("wall_tint_seed", v) }

    var hintText: String
        get() = mem("hint_text", "").value
        set(v) { mem("hint_text", "").value = v; put("hint_text", v) }

    var predictiveBack: Boolean
        get() = mem("predictive_back", true).value
        set(v) { mem("predictive_back", true).value = v; put("predictive_back", v) }

    var priceIn: Float
        get() = mem("price_in", 0f).value
        set(v) { mem("price_in", 0f).value = v; put("price_in", v) }

    var priceOut: Float
        get() = mem("price_out", 0f).value
        set(v) { mem("price_out", 0f).value = v; put("price_out", v) }

    var priceCache: Float
        get() = mem("price_cache", 0f).value
        set(v) { mem("price_cache", 0f).value = v; put("price_cache", v) }

    var autoBindWorkspace: Boolean
        get() = mem("auto_bind_workspace", true).value
        set(v) { mem("auto_bind_workspace", true).value = v; put("auto_bind_workspace", v) }

    var sessionWorkspace: String
        get() = mem("session_workspace", "").value
        set(v) { mem("session_workspace", "").value = v; put("session_workspace", v) }

    var sessionWsManual: Boolean
        get() = mem("session_ws_manual", false).value
        set(v) { mem("session_ws_manual", false).value = v; put("session_ws_manual", v) }

    var wsMigrated: Boolean
        get() = mem("ws_migrated", false).value
        set(v) { mem("ws_migrated", false).value = v; put("ws_migrated", v) }

    var profileId: String
        get() = mem("profile_id", "default").value
        set(v) { mem("profile_id", "default").value = v; put("profile_id", v) }

    var maxTokens: Int
        get() = mem("max_tokens", 0).value
        set(v) { mem("max_tokens", 0).value = v; put("max_tokens", v) }

    var maxMode: Boolean
        get() = mem("max_mode", false).value
        set(v) { mem("max_mode", false).value = v; put("max_mode", v) }

    var devShow: Boolean
        get() = mem("dev_show", false).value
        set(v) { mem("dev_show", false).value = v; put("dev_show", v) }

    var devOn: Boolean
        get() = mem("dev_on", false).value
        set(v) { mem("dev_on", false).value = v; put("dev_on", v) }

    var linuxMirrorNode: String
        get() = mem("linux_mirror_node", "").value
        set(v) { mem("linux_mirror_node", "").value = v; put("linux_mirror_node", v) }

    var profileName: String
        get() = mem("profile_name", "").value
        set(v) { mem("profile_name", "").value = v; put("profile_name", v) }

    var profileCustom: Boolean
        get() = mem("profile_custom", false).value
        set(v) { mem("profile_custom", false).value = v; put("profile_custom", v) }

    var profileSeed: Int
        get() = mem("profile_seed", 0).value
        set(v) { mem("profile_seed", 0).value = v; put("profile_seed", v) }

    var injectTime: Boolean
        get() = mem("inject_time", true).value
        set(v) { mem("inject_time", true).value = v; put("inject_time", v) }

    var injectBattery: Boolean
        get() = mem("inject_battery", false).value
        set(v) { mem("inject_battery", false).value = v; put("inject_battery", v) }

    var injectPlace: Boolean
        get() = mem("inject_place", false).value
        set(v) { mem("inject_place", false).value = v; put("inject_place", v) }

    var injectPrecise: Boolean
        get() = mem("inject_precise", false).value
        set(v) { mem("inject_precise", false).value = v; put("inject_precise", v) }

    var placeGeo: Boolean
        get() = mem("place_geo", true).value
        set(v) { mem("place_geo", true).value = v; put("place_geo", v) }

    var injectNoti: Boolean
        get() = mem("inject_noti", false).value
        set(v) { mem("inject_noti", false).value = v; put("inject_noti", v) }

    var notiLimit: Int
        get() = mem("noti_limit", 0).value
        set(v) { mem("noti_limit", 0).value = v; put("noti_limit", v) }

    /**注入设备信息：system 里的「设备：厂商 机型 / Android 版本」，实时段再补屏幕、内存、ABI */
    var injectDevice: Boolean
        get() = mem("inject_device", true).value
        set(v) { mem("inject_device", true).value = v; put("inject_device", v) }

    /**注入长期记忆：关掉时一条都不下发，模型这轮看不到记忆表 */
    var injectMem: Boolean
        get() = mem("inject_mem", true).value
        set(v) { mem("inject_mem", true).value = v; put("inject_mem", v) }

    /**记忆注入条数：0 = 不限；默认 35 条，够覆盖常用偏好又不至于把提示词撑爆 */
    var injectMemCount: Int
        get() = mem("inject_mem_count", 35).value
        set(v) { mem("inject_mem_count", v.coerceAtLeast(0)).value = v.coerceAtLeast(0); put("inject_mem_count", v.coerceAtLeast(0)) }

    var fcOn: Boolean
        get() = mem("fc_on", false).value
        set(v) { mem("fc_on", false).value = v; put("fc_on", v) }

    var fcW: Int
        get() = mem("fc_w", 300).value
        set(v) { mem("fc_w", 300).value = v; put("fc_w", v) }

    var fcH: Int
        get() = mem("fc_h", 430).value
        set(v) { mem("fc_h", 430).value = v; put("fc_h", v) }

    var fcFont: Int
        get() = mem("fc_font", 13).value
        set(v) { mem("fc_font", 13).value = v; put("fc_font", v) }

    var fcAlpha: Int
        get() = mem("fc_alpha", 100).value
        set(v) { mem("fc_alpha", 100).value = v; put("fc_alpha", v) }

    var fcX: Int
        get() = mem("fc_x", -1).value
        set(v) { mem("fc_x", -1).value = v; put("fc_x", v) }

    var fcY: Int
        get() = mem("fc_y", -1).value
        set(v) { mem("fc_y", -1).value = v; put("fc_y", v) }

    var a2aOn: Boolean
        get() = mem("a2a_on", false).value
        set(v) { mem("a2a_on", false).value = v; put("a2a_on", v) }
    var a2aPort: Int
        get() = mem("a2a_port", 8720).value
        set(v) { mem("a2a_port", 8720).value = v; put("a2a_port", v) }
    var a2aToken: String
        get() = mem("a2a_token", "").value
        set(v) { mem("a2a_token", "").value = v; Vault.put("a2a_token", v) }

    var petOn: Boolean
        get() = mem("pet_on", false).value
        set(v) { mem("pet_on", false).value = v; put("pet_on", v) }

    var petGlobal: Boolean
        get() = mem("pet_global", false).value
        set(v) { mem("pet_global", false).value = v; put("pet_global", v) }

    var petX: Int
        get() = mem("pet_x", -1).value
        set(v) { mem("pet_x", -1).value = v; put("pet_x", v) }

    var petY: Int
        get() = mem("pet_y", -1).value
        set(v) { mem("pet_y", -1).value = v; put("pet_y", v) }

    var petSize: Int
        get() = mem("pet_size", 120).value
        set(v) { val n = v.coerceIn(60, 260); mem("pet_size", 120).value = n; put("pet_size", n) }

    var petSeen: Int
        get() = mem("pet_seen", 0).value
        set(v) { mem("pet_seen", 0).value = v; put("pet_seen", v) }

    var petAlpha: Int
        get() = mem("pet_alpha", 100).value
        set(v) { val n = v.coerceIn(30, 100); mem("pet_alpha", 100).value = n; put("pet_alpha", n) }

    var petFlip: Boolean
        get() = mem("pet_flip", false).value
        set(v) { mem("pet_flip", false).value = v; put("pet_flip", v) }

    var petShadow: Boolean
        get() = mem("pet_shadow", true).value
        set(v) { mem("pet_shadow", true).value = v; put("pet_shadow", v) }

    var petCard: Boolean
        get() = mem("pet_card", false).value
        set(v) { mem("pet_card", false).value = v; put("pet_card", v) }

    var petFont: Int
        get() = mem("pet_font", 14).value
        set(v) { val n = v.coerceIn(9, 20); mem("pet_font", 14).value = n; put("pet_font", n) }

    var petFontBold: Boolean
        get() = mem("pet_font_bold", true).value
        set(v) { mem("pet_font_bold", true).value = v; put("pet_font_bold", v) }

    var petBubbleW: Int
        get() = mem("pet_bubble_w", 210).value
        set(v) { val n = v.coerceIn(100, 320); mem("pet_bubble_w", 210).value = n; put("pet_bubble_w", n) }

    var petBubbleR: Int
        get() = mem("pet_bubble_r", 14).value
        set(v) { val n = v.coerceIn(0, 28); mem("pet_bubble_r", 14).value = n; put("pet_bubble_r", n) }

    var petBubbleDark: Boolean
        get() = mem("pet_bubble_dark", false).value
        set(v) { mem("pet_bubble_dark", false).value = v; put("pet_bubble_dark", v) }

    var petFloat: Boolean
        get() = mem("pet_float", true).value
        set(v) { mem("pet_float", true).value = v; put("pet_float", v) }

    var petFloatAmp: Int
        get() = mem("pet_float_amp", 4).value
        set(v) { val n = v.coerceIn(0, 24); mem("pet_float_amp", 4).value = n; put("pet_float_amp", n) }

    var petFloatMs: Int
        get() = mem("pet_float_ms", 1600).value
        set(v) { val n = v.coerceIn(400, 4000); mem("pet_float_ms", 1600).value = n; put("pet_float_ms", n) }

    var petSwim: Boolean
        get() = mem("pet_swim", false).value
        set(v) { mem("pet_swim", false).value = v; put("pet_swim", v) }

    var petSwimSpeed: Int
        get() = mem("pet_swim_speed", 60).value
        set(v) { val n = v.coerceIn(10, 200); mem("pet_swim_speed", 60).value = n; put("pet_swim_speed", n) }

    var petSwimBounce: Boolean
        get() = mem("pet_swim_bounce", true).value
        set(v) { mem("pet_swim_bounce", true).value = v; put("pet_swim_bounce", v) }

    var petBounce: Boolean
        get() = mem("pet_bounce", true).value
        set(v) { mem("pet_bounce", true).value = v; put("pet_bounce", v) }

    var petBouncePct: Int
        get() = mem("pet_bounce_pct", 14).value
        set(v) { val n = v.coerceIn(0, 30); mem("pet_bounce_pct", 14).value = n; put("pet_bounce_pct", n) }

    var petTalk: Boolean
        get() = mem("pet_talk", true).value
        set(v) { mem("pet_talk", true).value = v; put("pet_talk", v) }

    var petBubbleMs: Int
        get() = mem("pet_bubble_ms", 2600).value
        set(v) { val n = v.coerceIn(800, 8000); mem("pet_bubble_ms", 2600).value = n; put("pet_bubble_ms", n) }

    var petTimedPct: Int
        get() = mem("pet_timed_pct", 35).value
        set(v) { val n = v.coerceIn(0, 100); mem("pet_timed_pct", 35).value = n; put("pet_timed_pct", n) }

    var petDblBal: Boolean
        get() = mem("pet_dbl_bal", true).value
        set(v) { mem("pet_dbl_bal", true).value = v; put("pet_dbl_bal", v) }

    var petSound: Boolean
        get() = mem("pet_sound", true).value
        set(v) { mem("pet_sound", true).value = v; put("pet_sound", v) }

    var petSoundVol: Int
        get() = mem("pet_sound_vol", 70).value
        set(v) { val n = v.coerceIn(20, 100); mem("pet_sound_vol", 70).value = n; put("pet_sound_vol", n) }

    var injectUsage: Boolean
        get() = mem("inject_usage", false).value
        set(v) { mem("inject_usage", false).value = v; put("inject_usage", v) }

    var autoA11y: Boolean
        get() = mem("auto_a11y", false).value
        set(v) { mem("auto_a11y", false).value = v; put("auto_a11y", v) }

    var showUsage: Boolean
        get() = mem("show_usage", true).value
        set(v) { mem("show_usage", true).value = v; put("show_usage", v) }

    var showMeta: Boolean
        get() = mem("show_meta", true).value
        set(v) { mem("show_meta", true).value = v; put("show_meta", v) }

    /**附带请求信息细分：回复底部那行小字里每一项都能单独关，默认全开（show_meta 关掉时整行细分不生效）*/
    var showSpeed: Boolean
        get() = mem("show_speed", true).value
        set(v) { mem("show_speed", true).value = v; put("show_speed", v) }

    var showLatency: Boolean
        get() = mem("show_latency", true).value
        set(v) { mem("show_latency", true).value = v; put("show_latency", v) }

    var showFirst: Boolean
        get() = mem("show_first", true).value
        set(v) { mem("show_first", true).value = v; put("show_first", v) }

    var showTime: Boolean
        get() = mem("show_time", true).value
        set(v) { mem("show_time", true).value = v; put("show_time", v) }

    var showProvider: Boolean
        get() = mem("show_provider", true).value
        set(v) { mem("show_provider", true).value = v; put("show_provider", v) }

    var showModel: Boolean
        get() = mem("show_model", true).value
        set(v) { mem("show_model", true).value = v; put("show_model", v) }

    var noStream: Boolean
        get() = mem("no_stream", false).value
        set(v) { mem("no_stream", false).value = v; put("no_stream", v) }

    /** chat 引擎：true = 整轮对话交给 dsh 的 agent 循环（插件工具全部可用），false = 用自家循环 */
    var dshEngine: Boolean
        get() = mem("dsh_engine", false).value
        set(v) { mem("dsh_engine", false).value = v; put("dsh_engine", v) }

    var maskOut: Boolean
        get() = mem("mask_out", false).value
        set(v) {
            mem("mask_out", false).value = v
            put("mask_out", v)
            if (v && !noStream) noStream = true
        }

    var noShot: Boolean
        get() = mem("no_shot", false).value
        set(v) { mem("no_shot", false).value = v; put("no_shot", v) }

    var speakReplies: Boolean
        get() = mem("speak_replies", false).value
        set(v) { mem("speak_replies", false).value = v; put("speak_replies", v) }

    var asrEngine: String
        get() = mem("asr_engine", "").value
        set(v) { mem("asr_engine", "").value = v; put("asr_engine", v) }

    var asrDgKey: String
        get() = mem("asr_dg_key", "").value
        set(v) { mem("asr_dg_key", "").value = v; Vault.put("asr_dg_key", v.trim()) }

    var asrDgModel: String
        get() = mem("asr_dg_model", "nova-3").value
        set(v) { mem("asr_dg_model", "nova-3").value = v; put("asr_dg_model", v.trim()) }

    var asrDgUrl: String
        get() = mem("asr_dg_url", "https://api.deepgram.com").value
        set(v) { mem("asr_dg_url", "https://api.deepgram.com").value = v.trim().trimEnd('/'); put("asr_dg_url", v.trim().trimEnd('/')) }

    var asrSherpaDir: String
        get() = mem("asr_sherpa_dir", "").value
        set(v) { mem("asr_sherpa_dir", "").value = v.trim(); put("asr_sherpa_dir", v.trim()) }

    var asrSherpaSrc: String
        get() = mem("asr_sherpa_src", "").value
        set(v) { mem("asr_sherpa_src", "").value = v.trim(); put("asr_sherpa_src", v.trim()) }

    var asrMode: String
        get() = mem("asr_mode", "sys").value
        set(v) { mem("asr_mode", "sys").value = v; put("asr_mode", v) }

    var asrBase: String
        get() = mem("asr_base", "").value
        set(v) { mem("asr_base", "").value = v; put("asr_base", v.trim()) }

    var asrKey: String
        get() = mem("asr_key", "").value
        set(v) { mem("asr_key", "").value = v; Vault.put("asr_key", v.trim()) }

    var asrModel: String
        get() = mem("asr_model", "whisper-1").value
        set(v) { mem("asr_model", "whisper-1").value = v; put("asr_model", v.trim()) }

    var asrLang: String
        get() = mem("asr_lang", "zh-CN").value
        set(v) { mem("asr_lang", "zh-CN").value = v; put("asr_lang", v.trim()) }

    var asrVendor: String
        get() = mem("asr_vendor", "openai").value
        set(v) { mem("asr_vendor", "openai").value = v; put("asr_vendor", v) }

    var asrRate: Int
        get() = mem("asr_rate", 16000).value
        set(v) { mem("asr_rate", 16000).value = v; put("asr_rate", v) }

    var asrMaxSec: Int
        get() = mem("asr_max_sec", 60).value
        set(v) { mem("asr_max_sec", 60).value = v; put("asr_max_sec", v) }

    var asrOffline: Boolean
        get() = mem("asr_offline", false).value
        set(v) { mem("asr_offline", false).value = v; put("asr_offline", v) }

    var ttsMode: String
        get() = mem("tts_mode", "sys").value
        set(v) { mem("tts_mode", "sys").value = v; put("tts_mode", v) }

    var ttsBase: String
        get() = mem("tts_base", "").value
        set(v) { mem("tts_base", "").value = v; put("tts_base", v.trim()) }

    var ttsKey: String
        get() = mem("tts_key", "").value
        set(v) { mem("tts_key", "").value = v; Vault.put("tts_key", v.trim()) }

    var ttsModel: String
        get() = mem("tts_model", "tts-1").value
        set(v) { mem("tts_model", "tts-1").value = v; put("tts_model", v.trim()) }

    var ttsVoice: String
        get() = mem("tts_voice", "alloy").value
        set(v) { mem("tts_voice", "alloy").value = v; put("tts_voice", v.trim()) }

    var ctxTotal: Int
        get() = mem("ctx_total", 128000).value
        set(v) { mem("ctx_total", 128000).value = v; put("ctx_total", v) }

    var profCtxTotal: Int = 0

    var profThinkLevels: String = ""

    var profThinkLevelsJson: String = ""

    val ctxWin: Int
        get() = if (profCtxTotal > 0) profCtxTotal else ctxTotal

    var ctxPct: Int
        get() = mem("ctx_pct", 70).value
        set(v) {
            val n = v.coerceIn(10, 99)
            mem("ctx_pct", 70).value = n
            put("ctx_pct", n)
        }

    val ctxTokenBudget: Int
        get() = (ctxWin.toLong() * ctxPct / 100L).toInt()

    fun estTokens(s: String): Int {
        if (s.isEmpty()) return 0
        var cjk = 0
        var other = 0
        for (ch in s) {
            val c = ch.code
            if (c in 0x3000..0x303F || c in 0x3040..0x30FF || c in 0x3400..0x4DBF ||
                c in 0x4E00..0x9FFF || c in 0xAC00..0xD7AF || c in 0xF900..0xFAFF ||
                c in 0xFF00..0xFFEF
            ) cjk++ else other++
        }
        return cjk + (other * 2 + 6) / 7
    }

    var presetSeed: Int
        get() = mem("preset_seed", 0).value
        set(v) { mem("preset_seed", 0).value = v; put("preset_seed", v) }

    fun profileKey(id: String): String {
        if (id.isBlank()) return ""
        val k = "profkey_$id"
        val st = mem(k, "")
        if (st.value == "") st.value = Vault.get(k)
        return st.value
    }

    fun setProfileKey(id: String, v: String): Boolean {
        if (id.isBlank()) return false
        val k = "profkey_$id"
        mem(k, "").value = v
        return Vault.put(k, v)
    }

    fun keyList(id: String): List<String> =
        profileKey(id).split('\n', ',', ';', '\t')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    fun policy(tool: String): String = mem("policy_$tool", "ask").also {
        if (it.value == "ask") it.value = sp?.getString("policy_$tool", "ask") ?: "ask"
    }.value

    fun setPolicy(tool: String, policy: String) {
        mem("policy_$tool", "ask").value = policy
        put("policy_$tool", policy)
    }

    /**
     * 全局工具权限主控：allow / deny / ask。
     * 默认 ask（等价于「按单工具设置走」），只有 allow / deny 才真正覆盖单工具权限；
     * 用户在设置 → 扩展 → 工具权限 里点「一键永久同意 / 每次确认 / 一键永久拒绝」改的就是它。
     */
    var policyAll: String
        get() = mem("policy_all", "ask").also {
            if (it.value == "ask") it.value = sp?.getString("policy_all", "ask") ?: "ask"
        }.value
        set(v) {
            mem("policy_all", "ask").value = v
            put("policy_all", v)
        } //全局权限主控

    /**
     * 实际生效的策略：全局主控是 allow/deny 时直接覆盖单工具设置，
     * 是 ask 时回落到该工具自己的 policy。所有执行侧与 UI 一律读这个，别再直接读 policy()。
     */
    fun policyEff(tool: String): String {
        val g = policyAll
        return if (g == "allow" || g == "deny") g else policy(tool)
    } //生效策略

    val localReady: Boolean
        get() = llmLocal && llmModel.isNotBlank() && File(llmModel).isFile

    val ready: Boolean
        get() = localReady || (baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank())

    var draftKeepImg: Boolean
        get() = mem("draft_keep_img", false).value
        set(v) { mem("draft_keep_img", false).value = v; put("draft_keep_img", v) }

    var draftDays: Int
        get() = mem("draft_days", 7).value
        set(v) {
            val n = v.coerceIn(0, 365)
            mem("draft_days", 7).value = n
            put("draft_days", n)
        }

    fun draft(sid: String): String =
        if (sid.isBlank()) "" else sp?.getString("draft_$sid", "") ?: ""

    fun setDraft(sid: String, text: String) {
        if (sid.isBlank()) return
        if (text.isBlank()) sp?.edit()?.remove("draft_$sid")?.apply()
        else sp?.edit()?.putString("draft_$sid", text)?.apply()
    }
}
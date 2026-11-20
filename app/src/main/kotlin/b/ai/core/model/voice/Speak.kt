/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:09
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package b.ai.core.model.voice
import b.ai.core.cfg.app.Cfg
import b.ai.net.api.llm.text

import android.speech.tts.TextToSpeech
import b.ai.App
import java.util.Locale

/** 系统 TTS：把回复念出来，不联网、不依赖第三方服务 */
object Speak {
    private var tts: TextToSpeech? = null
    private var ready = false
    //正在念的文本，UI 想显示状态时可以读
    var speaking: String = ""
        private set

    private fun ensure() {
        if (tts != null) return
        runCatching {
            tts = TextToSpeech(App.self) { st ->
                ready = st == TextToSpeech.SUCCESS
                if (ready) runCatching { tts?.language = Locale.getDefault() }
            }
        }
    } //初始化引擎

    /** 朗读一段正文：按设置开关决定；force=true 时无视开关（长按菜单里的「朗读」用） */
    fun say(text: String, force: Boolean = false) {
        if (!force && !Cfg.speakReplies) return
        val t = plain(text)
        if (t.isBlank()) return
        //接口模式：先请求音频再播；接口失败就退回系统 TTS，别让人听不到
        if (Cfg.ttsMode == "api") {
            speaking = t
            Thread({
                if (!Audio.speak(t)) sysSay(t)
            }, "bai-tts").start()
            return
        }
        sysSay(t)
    } //朗读

    private fun sysSay(t: String) {
        ensure()
        speaking = t
        runCatching {
            tts?.stop()
            tts?.speak(t.take(4000), TextToSpeech.QUEUE_FLUSH, null, "bai-reply")
        }
    } //系统 TTS 念

    fun stop() {
        speaking = ""
        Audio.stop()
        runCatching { tts?.stop() }
    } //停止

    fun release() {
        stop()
        runCatching { tts?.shutdown() }
        tts = null
        ready = false
    } //释放

    /** 念之前把 Markdown 记号去掉：代码块直接跳过，念符号没有意义 */
    fun plain(s: String): String {
        var t = s
        t = Regex("```[\\s\\S]*?```").replace(t, "，代码块，")
        t = Regex("`[^`\\n]*`").replace(t, "")
        t = Regex("!\\[[^]]*]\\([^)]*\\)").replace(t, "")
        t = Regex("\\[([^]]*)]\\([^)]*\\)").replace(t, "$1")
        t = Regex("^#{1,6}\\s*", RegexOption.MULTILINE).replace(t, "")
        t = Regex("[*_>]").replace(t, "")
        t = Regex("\\n{2,}").replace(t, "\n")
        return t.trim()
    } //去掉 Markdown 记号
}
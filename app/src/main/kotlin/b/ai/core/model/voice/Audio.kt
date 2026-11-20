/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:08
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

import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import b.ai.App
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** 语音模型：录音 -> /audio/transcriptions 转文字；文字 -> /audio/speech 转音频并播放；两个接口都走 OpenAI 兼容协议 */
object Audio {
    //录音中 / 上传中 / 播放中，界面直接读这三个状态
    var recing by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var playing by mutableStateOf(false)
        private set
    var error by mutableStateOf("")
        private set

    private var rec: MediaRecorder? = null
    private var recFile: File? = null
    private var player: MediaPlayer? = null
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    /** 转写接口地址：手填 > 服务商预设 > 当前模型配置，三者都空就没法用 */
    private fun asrBase(): String {
        val preset = AsrPresets.of(Cfg.asrVendor).base
        return (Cfg.asrBase.ifBlank { preset }).ifBlank { Cfg.baseUrl }.trim().trimEnd('/')
    } //识别接口地址

    /** 合成接口地址：手填 > 当前模型配置（TTS 不走识别预设，两家不是一回事） */
    private fun ttsBase(): String = Cfg.ttsBase.ifBlank { Cfg.baseUrl }.trim().trimEnd('/')

    private fun key(custom: String): String = custom.ifBlank { Cfg.apiKey }.trim()

    //录音秒数：界面显示用，到设置的上限自动停并转写
    var recSec by mutableStateOf(0)
        private set

    private var clock: Runnable? = null
    private var limit: Runnable? = null
    private var cb: ((String) -> Unit)? = null

    fun startRec(): Boolean {
        stopRec()
        error = ""
        val f = File(App.self.cacheDir, "asr-" + System.currentTimeMillis() + ".m4a")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(App.self) else @Suppress("DEPRECATION") MediaRecorder()
        val ok = runCatching {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            //采样率按设置来：8k 适合窄带语音，16k 各家通吃，48k 留给音乐场景
            r.setAudioSamplingRate(
                if (engine() == "sherpa") 16000 else Cfg.asrRate.coerceIn(8000, 48000)
            )
            r.setAudioChannels(1)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
        }.isSuccess
        if (!ok) {
            error = "录音启动失败"
            runCatching { r.release() }
            return false
        }
        rec = r
        recFile = f
        recing = true
        startClock()
        return true
    } //开始录音

    /** 停止录音，返回录好的文件（没在录就是 null） */
    fun stopRec(): File? {
        val r = rec ?: return null
        rec = null
        recing = false
        runCatching { r.stop() }
        runCatching { r.release() }
        return recFile
    } //停止录音

    /** 点一下开始录、再点一下停下并上传转写；结果切回主线程交给 onText */
    fun toggleAsr(onText: (String) -> Unit) {
        if (recing) {
            if (cb == null) cb = onText
            finish()
            return
        }
        cb = onText
        if (!startRec()) cb = null
    } //录音转写开关

    /** 收尾：停录音、上传转写，结果只交给这一轮的回调 */
    private fun finish() {
        stopClock()
        val f = stopRec()
        if (f == null || !f.exists() || f.length() == 0L) {
            runCatching { f?.delete() }
            return
        }
        val once = cb
        cb = null
        busy = true
        error = ""
        Thread({
            val r = runCatching { transcribeAny(f) }
            f.delete()
            main.post {
                busy = false
                r.onSuccess { t -> once?.invoke(t) }.onFailure { error = it.message ?: "转写失败" }
            }
        }, "bai-asr").start()
    } //停止并转写

    /** 每秒走一格给界面看，到上限自动收 */
    private fun startClock() {
        stopClock()
        recSec = 0
        val max = Cfg.asrMaxSec.coerceIn(5, 3600)
        var n = 0
        val tick = object : Runnable {
            override fun run() {
                if (!recing) return
                n++
                recSec = n
                if (n < max) main.postDelayed(this, 1000)
            }
        }
        val stop = Runnable {
            if (recing) {
                error = "已到 $max 秒上限，自动停止并转写"
                finish()
            }
        }
        clock = tick
        limit = stop
        main.postDelayed(tick, 1000)
        main.postDelayed(stop, max * 1000L)
    } //录音计时

    private fun stopClock() {
        clock?.let { main.removeCallbacks(it) }
        limit?.let { main.removeCallbacks(it) }
        clock = null
        limit = null
        recSec = 0
    } //停计时

    fun cancelRec() {
        stopClock()
        cb = null
        stopRec()
        recFile?.delete()
        recFile = null
    } //放弃这段录音

    /** 清空错误：界面 3 秒自动消失与切换输入方式时调 */
    fun clearError() {
        error = ""
    } //清错误

    /** 当前 STT 引擎：asrEngine 优先，空则按旧的 asrMode 回落；离线模式下远程引擎统一回落本地 */
    fun engine(): String {
        val e = Cfg.asrEngine.trim().ifEmpty {
            if (Cfg.asrMode == "api") "openai" else "sys"
        }
        //离线模式只允许本地：老配置里残留的 openai/deepgram 不再生效，避免音频被传出去
        if (Cfg.asrOffline && (e == "openai" || e == "deepgram")) return "sys"
        return e
    } //当前引擎

    /** 按引擎分派：远程两种都返回正文，本地 sherpa 需要内置运行时 */
    fun transcribeAny(f: File): String = when (engine()) {
        "deepgram" -> transcribeDeepgram(f)
        "sherpa" -> transcribeSherpa(f)
        else -> transcribe(f)
    } //按引擎转写

    /**
     * Deepgram：POST /v1/listen，鉴权是 `Token <key>`（不是 Bearer），
     * 音频走裸 body（不是 multipart），返回 results.channels[0].alternatives[0].transcript。
     */
    fun transcribeDeepgram(f: File): String {
        val key = Cfg.asrDgKey.trim()
        if (key.isEmpty()) throw IllegalStateException("Deepgram 还没填 API Key：设置 → 模型 → 语音输入")
        val model = Cfg.asrDgModel.ifBlank { "nova-3" }
        val lang = Cfg.asrLang.substringBefore('-').ifBlank { "zh" }
        val url = URL(
            Cfg.asrDgUrl.trimEnd('/') + "/v1/listen?model=" + java.net.URLEncoder.encode(model, "UTF-8") +
                "&language=" + java.net.URLEncoder.encode(lang, "UTF-8") + "&smart_format=true&punctuate=true"
        )
        val c = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30000
            readTimeout = 120000
            setRequestProperty("Authorization", "Token $key")
            setRequestProperty("Content-Type", if (f.name.endsWith(".wav")) "audio/wav" else "audio/mp4")
        }
        c.outputStream.use { it.write(f.readBytes()) }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        c.disconnect()
        if (code !in 200..299) throw IllegalStateException("Deepgram HTTP $code " + text.take(200))
        val t = runCatching {
            JSONObject(text).getJSONObject("results").getJSONArray("channels")
                .getJSONObject(0).getJSONArray("alternatives").getJSONObject(0).optString("transcript")
        }.getOrDefault("")
        if (t.isBlank()) throw IllegalStateException("Deepgram 没识别出内容")
        return t.trim()
    } //Deepgram 转写

    /**
     * Sherpa-NCNN 本地识别：需要 libsherpa-ncnn 与模型文件，当前构建没打进来，
     * 这里给出明确指引而不是静默失败。
     */
    fun transcribeSherpa(f: File): String {
        if (!SherpaAsr.runtimeOk()) {
            throw IllegalStateException("本地识别运行时没加载起来：当前安装包可能不是 arm64 版本")
        }
        val dir = Cfg.asrSherpaDir.trim()
        if (!SherpaAsr.modelOk(dir)) {
            throw IllegalStateException("模型目录不完整（缺 param/bin/tokens）：$dir")
        }
        val t = SherpaAsr.recognize(f, dir)
        if (t.isBlank()) throw IllegalStateException("没听清，再说一次")
        return t
    } //Sherpa 本地转写

    /** 音频转文字：multipart 上传，返回识别出的正文；失败抛异常由调用方出提示 */
    fun transcribe(f: File): String {
        //服务商有单文件上限时先拦一下，比传完被拒省一轮等待
        val cap = AsrPresets.of(Cfg.asrVendor).maxMb
        if (cap > 0 && f.length() > cap * 1024L * 1024L) {
            throw IllegalStateException("这段录音 ${f.length() / 1024 / 1024}MB，超过 ${cap}MB 上限，缩短一点或换服务商")
        }
        val url = URL(asrBase() + "/audio/transcriptions")
        val boundary = "----bai" + System.nanoTime()
        val body = java.io.ByteArrayOutputStream()
        fun part(name: String, value: String) {
            body.write(("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n").toByteArray())
        }
        part("model", Cfg.asrModel.ifBlank { "whisper-1" })
        if (Cfg.asrLang.isNotBlank()) part("language", Cfg.asrLang.substringBefore('-'))
        body.write(
            ("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"a.m4a\"\r\n" +
                "Content-Type: audio/mp4\r\n\r\n").toByteArray()
        )
        body.write(f.readBytes())
        body.write("\r\n--$boundary--\r\n".toByteArray())
        val c = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30000
            readTimeout = 120000
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            if (key(Cfg.asrKey).isNotBlank()) setRequestProperty("Authorization", "Bearer " + key(Cfg.asrKey))
        }
        c.outputStream.use { it.write(body.toByteArray()) }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        c.disconnect()
        if (code !in 200..299) throw IllegalStateException("HTTP $code " + text.take(200))
        //有的实现回 {text:...}，有的回 {data:{text:...}}，两种都认
        val t = runCatching { JSONObject(text).optString("text") }.getOrDefault("")
            .ifBlank { runCatching { JSONObject(text).optJSONObject("data")?.optString("text").orEmpty() }.getOrDefault("") }
        if (t.isBlank()) throw IllegalStateException("没识别出内容")
        return t.trim()
    } //音频转文字

    /** 拉 baseUrl 下的 /models：语音类筛出来给用户选，一个都没筛到就退回全量列表 */
    fun listModels(): List<String> {
        val b = asrBase()
        if (b.isBlank()) throw IllegalStateException("baseUrl 还没填")
        val c = (URL(b + "/models").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 30000
            setRequestProperty("Accept", "application/json")
            if (key(Cfg.asrKey).isNotBlank()) setRequestProperty("Authorization", "Bearer " + key(Cfg.asrKey))
        }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        c.disconnect()
        if (code !in 200..299) throw IllegalStateException("HTTP $code " + text.take(160))
        val obj = runCatching { JSONObject(text) }.getOrNull()
            ?: throw IllegalStateException("返回不是 JSON，这个服务可能没有 /models")
        val arr = obj.optJSONArray("data") ?: obj.optJSONArray("models")
            ?: throw IllegalStateException("返回里没有模型列表")
        val all = ArrayList<String>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id").ifBlank {
                o.optString("slug").ifBlank { o.optString("name") }
            }
            if (id.isNotBlank()) all.add(id)
        }
        //混着聊天模型没法选，先按名字筛一遍语音的
        val voice = all.filter {
            val n = it.lowercase()
            n.contains("whisper") || n.contains("asr") || n.contains("transcri") ||
                n.contains("sensevoice") || n.contains("paraformer") || n.contains("audio")
        }
        return (voice.ifEmpty { all }).distinct()
    } //拉可用模型列表

    /** 文字转音频：拿回 mp3 字节存进缓存再播，返回是否成功 */
    fun speak(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        error = ""
        val bytes = runCatching {
            val url = URL(ttsBase() + "/audio/speech")
            val json = JSONObject()
                .put("model", Cfg.ttsModel.ifBlank { "tts-1" })
                .put("voice", Cfg.ttsVoice.ifBlank { "alloy" })
                .put("input", t.take(4000))
                .put("response_format", "mp3")
            val c = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 30000
                readTimeout = 120000
                setRequestProperty("Content-Type", "application/json")
                if (key(Cfg.ttsKey).isNotBlank()) setRequestProperty("Authorization", "Bearer " + key(Cfg.ttsKey))
            }
            c.outputStream.use { it.write(json.toString().toByteArray()) }
            val code = c.responseCode
            if (code !in 200..299) {
                val msg = c.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                c.disconnect()
                throw IllegalStateException("HTTP $code " + msg.take(200))
            }
            val b = c.inputStream.use { it.readBytes() }
            c.disconnect()
            b
        }.getOrElse {
            error = it.message ?: "合成失败"
            return false
        }
        val f = File(App.self.cacheDir, "tts.mp3")
        runCatching { FileOutputStream(f).use { it.write(bytes) } }
        stop()
        val mp = MediaPlayer()
        player = mp
        return runCatching {
            mp.setDataSource(f.absolutePath)
            mp.setOnCompletionListener {
                playing = false
                runCatching { it.release() }
                if (player === it) player = null
            }
            mp.prepare()
            mp.start()
            playing = true
            true
        }.getOrElse {
            error = it.message ?: "播放失败"
            playing = false
            runCatching { mp.release() }
            player = null
            false
        }
    } //文字转音频

    fun stop() {
        playing = false
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
    } //停止播放

    fun release() {
        stop()
        cancelRec()
    } //释放
} //语音模型
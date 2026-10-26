package b.ai.core

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import b.ai.R

private const val TAG = "bai-voice"

/** 语音输入：默认走系统识别器；设置里切成模型模式就走录音 + 转写接口 */
object Voice {
    var listening by mutableStateOf(false)
        private set
    var sysError by mutableStateOf("")
        private set

    //界面只关心「有没有在听」和「出了什么错」，两种模式在这里统一
    val error: String
        get() = if (Cfg.asrMode == "api") Audio.error else sysError

    private var sr: SpeechRecognizer? = null

    //本机识别服务一旦证实不可用（第三方绑不上 signature 级服务）就不再重试系统，
    //后续有接口配置时直接走录音转写，省去每次都先失败一遍
    private var sysBroken = false

    fun toggle(ctx: Context, onText: (String) -> Unit) {
        if (Cfg.asrMode == "api") {
            Audio.toggleAsr(onText)
            listening = Audio.recing
            return
        }
        if (sysBroken && hasApi()) {
            sysError = ""
            Audio.toggleAsr(onText)
            listening = Audio.recing
            return
        }
        if (listening) {
            stop()
            return
        }
        sysError = ""
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            Log.w(TAG, "isRecognitionAvailable=false，系统里没有可用的识别服务")
            sysError = ctx.getString(R.string.vo_no_service)
            return
        }
        //上一轮的识别器可能停在 error/结果状态，先彻底销毁再建新的；复用一个脏实例是 ERROR_CLIENT 的常见来源
        release()
        val r = runCatching { SpeechRecognizer.createSpeechRecognizer(ctx) }.getOrNull()
        if (r == null) {
            Log.w(TAG, "createSpeechRecognizer 返回 null")
            sysError = ctx.getString(R.string.vo_init_fail)
            return
        }
        sr = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(e: Int) {
                Log.w(TAG, "onError code=$e lang=${Cfg.asrLang} -> ${errText(ctx, e)}")
                listening = false
                //ERROR_CLIENT 基本都是「系统里没有可绑定的识别服务」：配了接口就直接回退，没配就指路去设置
                if (e == SpeechRecognizer.ERROR_CLIENT) {
                    sysBroken = true
                    release()
                    if (hasApi()) {
                        sysError = ctx.getString(R.string.vo_fallback)
                        Audio.toggleAsr(onText)
                    } else {
                        sysError = ctx.getString(R.string.vo_need_api)
                    }
                    return
                }
                sysError = errText(ctx, e)
                release()
            }

            override fun onResults(results: Bundle?) {
                listening = false
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                Log.i(TAG, "onResults ${list?.size ?: 0} 条")
                val t = list?.firstOrNull().orEmpty()
                if (t.isNotBlank()) onText(t)
                release()
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Cfg.asrLang.ifBlank { "zh-CN" })
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        //startListening 必须在主线程；不在主线程时系统会直接回 ERROR_CLIENT
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post { runCatching { r.startListening(i) } }
            listening = true
            return
        }
        val ok = runCatching { r.startListening(i) }.isSuccess
        if (!ok) {
            Log.w(TAG, "startListening 抛异常")
            sysError = ctx.getString(R.string.vo_start_fail)
            release()
            return
        }
        listening = true
    } //开始/结束听写

    fun stop() {
        runCatching { sr?.stopListening() }
        listening = false
    } //结束听写等结果

    fun release() {
        runCatching { sr?.destroy() }
        sr = null
        listening = false
    } //释放识别器

    /**配了独立接口地址或独立 key 才算能走录音转写：只回退主模型接口会打到不支持转写的服务上 */
    private fun hasApi(): Boolean = Cfg.asrBase.isNotBlank() || Cfg.asrKey.isNotBlank()

    private fun errText(c: Context, e: Int): String = when (e) {
        SpeechRecognizer.ERROR_AUDIO -> c.getString(R.string.vo_audio)
        SpeechRecognizer.ERROR_CLIENT -> c.getString(R.string.vo_client)
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> c.getString(R.string.vo_perm)
        SpeechRecognizer.ERROR_NETWORK -> c.getString(R.string.vo_net)
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> c.getString(R.string.vo_net_timeout)
        SpeechRecognizer.ERROR_NO_MATCH -> c.getString(R.string.vo_no_match)
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> c.getString(R.string.vo_busy)
        SpeechRecognizer.ERROR_SERVER -> c.getString(R.string.vo_server)
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> c.getString(R.string.vo_speech_timeout)
        else -> c.getString(R.string.vo_fail_fmt, e)
    }
} //系统语音输入
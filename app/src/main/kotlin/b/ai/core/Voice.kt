package b.ai.core

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import b.ai.R

/** 系统语音识别：点一下开始、说完把文本交回去；不依赖任何云服务配置 */
object Voice {
    var listening by mutableStateOf(false)
        private set
    var error by mutableStateOf("")
        private set

    private var sr: SpeechRecognizer? = null

    fun toggle(ctx: Context, onText: (String) -> Unit) {
        if (listening) {
            stop()
            return
        }
        error = ""
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            error = ctx.getString(R.string.vo_no_service)
            return
        }
        val r = runCatching { SpeechRecognizer.createSpeechRecognizer(ctx) }.getOrNull()
        if (r == null) {
            error = ctx.getString(R.string.vo_init_fail)
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
                listening = false
                error = errText(ctx, e)
            }

            override fun onResults(results: Bundle?) {
                listening = false
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                if (t.isNotBlank()) onText(t)
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        val ok = runCatching { r.startListening(i) }.isSuccess
        if (!ok) {
            error = ctx.getString(R.string.vo_start_fail)
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
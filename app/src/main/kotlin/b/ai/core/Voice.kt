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
            error = "系统没有可用的语音识别服务"
            return
        }
        val r = runCatching { SpeechRecognizer.createSpeechRecognizer(ctx) }.getOrNull()
        if (r == null) {
            error = "语音识别初始化失败"
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
                error = errText(e)
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
            error = "启动语音识别失败"
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

    private fun errText(e: Int): String = when (e) {
        SpeechRecognizer.ERROR_AUDIO -> "录音出错"
        SpeechRecognizer.ERROR_CLIENT -> "识别客户端错误"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "没有麦克风权限"
        SpeechRecognizer.ERROR_NETWORK -> "网络错误"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时"
        SpeechRecognizer.ERROR_NO_MATCH -> "没听清，再说一次"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别器忙"
        SpeechRecognizer.ERROR_SERVER -> "语音服务报错"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没检测到说话"
        else -> "识别失败($e)"
    }
} //系统语音输入
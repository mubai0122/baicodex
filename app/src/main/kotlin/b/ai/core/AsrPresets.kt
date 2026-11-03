package b.ai.core

/**
 * 语音识别服务商预设：把 baseUrl 与常用模型带上，用户选一个就能用。
 * maxMb 只写官方文档能确认的值，0 表示不知道也不拦。
 */
data class AsrPreset(
    val id: String,
    val label: String,
    val base: String,
    val models: List<String>,
    val maxMb: Int,
    val tip: String
)

object AsrPresets {
    val all = listOf(
        AsrPreset(
            "openai", "OpenAI", "https://api.openai.com/v1",
            listOf("gpt-4o-transcribe", "gpt-4o-mini-transcribe", "whisper-1"), 25, "单文件 25MB"
        ),
        AsrPreset(
            "groq", "Groq", "https://api.groq.com/openai/v1",
            listOf("whisper-large-v3-turbo", "whisper-large-v3", "distil-whisper-large-v3-en"), 25,
            "单文件 25MB，速度最快"
        ),
        AsrPreset(
            "siliconflow", "硅基流动", "https://api.siliconflow.cn/v1",
            listOf("FunAudioLLM/SenseVoiceSmall"), 0, "中文便宜，按音频时长计费"
        ),
        AsrPreset(
            "together", "Together", "https://api.together.xyz/v1",
            listOf("openai/whisper-large-v3"), 0, ""
        ),
        AsrPreset(
            "fireworks", "Fireworks", "https://api.fireworks.ai/inference/v1",
            listOf("whisper-v3", "whisper-v3-turbo"), 0, ""
        ),
        AsrPreset(
            "aliyun", "阿里百炼（OpenAI 兼容）", "https://dashscope.aliyuncs.com/compatible-mode/v1",
            listOf("qwen3-asr-flash"), 10, "兼容接口只收公网音频 URL，本地录音用不了，仅作备选"
        ),
        AsrPreset(
            "local", "本地 whisper.cpp", "http://127.0.0.1:8080/v1",
            listOf("whisper-1"), 0, "自建 whisper.cpp server，不联网"
        ),
        AsrPreset("custom", "自定义", "", emptyList(), 0, "baseUrl 与模型都自己填")
    )

    fun of(id: String): AsrPreset = all.firstOrNull { it.id == id } ?: all.last()

    /** 采样率选项：录音与上传都按它来，16k 是各家通吃的最小值 */
    val rates = listOf(8000, 16000, 22050, 44100, 48000)

    /** 单次最长录音秒数：到点自动停并转写，避免手滑录成半小时 */
    val maxSecs = listOf(30, 60, 120, 300, 600)
} //语音识别预设
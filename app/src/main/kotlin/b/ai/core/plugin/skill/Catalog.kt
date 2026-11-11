package b.ai.core.plugin.skill
import b.ai.core.model.chat.AuthStyle
import b.ai.core.model.chat.Provider

object Providers {
    /** 本地推理的伪厂商 id：选中后 base_url / Key 都不用填，模型选手机上已导入的 GGUF */
    const val LOCAL = "local"

    val builtin: List<Provider> = listOf(
        Provider(LOCAL, "本地模型", "", AuthStyle.NONE),
        Provider("openai", "OpenAI", "https://api.openai.com/v1"),
        Provider("deepseek", "DeepSeek", "https://api.deepseek.com/v1"),
        Provider("moonshot", "Kimi / Moonshot", "https://api.moonshot.cn/v1"),
        Provider("dashscope", "通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1"),
        Provider("zhipu", "智谱", "https://open.bigmodel.cn/api/paas/v4"),
        Provider("siliconflow", "硅基流动", "https://api.siliconflow.cn/v1"),
        Provider("openrouter", "OpenRouter", "https://openrouter.ai/api/v1"),
        Provider("groq", "Groq", "https://api.groq.com/openai/v1"),
        Provider("together", "Together", "https://api.together.xyz/v1"),
        Provider("mimo", "小米 MiMo", ""),
        Provider("custom", "自定义", "", AuthStyle.BEARER, custom = true)
    )

    //找不到就退「自定义」：退成表里第一项（OpenAI）会让未知厂商的配置显示成 OpenAI，看着像厂商被改了
    fun byId(id: String): Provider = builtin.firstOrNull { it.id == id }
        ?: builtin.firstOrNull { it.custom }
        ?: builtin.first()
}

object Workflows {
    val all: List<Pair<String, String>> = listOf(
        "general" to "通用助手",
        "file" to "文件管理",
        "ops" to "设备运维",
        "reverse" to "逆向分析"
    )

    fun label(id: String): String = all.firstOrNull { it.first == id }?.second ?: "通用助手"

    fun prompt(id: String): String = when (id) {
        "file" -> "你负责文件管理：优先用 read_file / write_file / delete_file 精确操作，" +
            "需要批量或检索时用 exec_shell（find、grep、du 等）。删改前先说明影响。"
        "ops" -> "你负责设备运维：用 exec_shell 执行 pm / am / settings / dumpsys 等命令，" +
            "每次先给结论再给命令，避免输出无限日志。"
        "reverse" -> "你负责逆向分析：用 exec_shell 调 unzip / aapt / dexdump / apksigner 等工具，" +
            "产物统一落到工作目录，重要结论先落文件再看。"
        else -> "你是设备上的 AI 助手，可以读写文件、执行 shell 命令来完成用户任务。"
    }
} //厂商与工作流静态表
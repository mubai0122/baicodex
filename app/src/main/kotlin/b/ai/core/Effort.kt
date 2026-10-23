package b.ai.core

/**
 * 思考强度档位表：不同模型认的 reasoning_effort 不一样，滑块按这张表决定档位数。
 * 依据（查证官方文档）：DeepSeek Thinking Mode 只认 low/high/max（medium、xhigh 都被映射成 high）；
 * OpenAI 是 none/minimal/low/medium/high/xhigh/max 的子集，随型号放开；
 * 其它厂商与中转站最保守只给 low/medium/high，多传一档可能直接 400。
 */
object Effort {
    /** 等级顺序：用来把历史档位映射到当前模型认的档位 */
    private val RANK = listOf("none", "minimal", "low", "medium", "high", "xhigh", "max")
    /** 最保守的三档：绝大多数中转站都认 */
    private val SAFE = listOf("low", "medium", "high")

    fun levels(providerId: String, model: String): List<String> {
        val pid = providerId.lowercase()
        val m = model.lowercase()
        if (pid.contains("deepseek") || m.contains("deepseek")) return listOf("low", "high", "max")
        val openai = pid.contains("openai") || m.startsWith("gpt-") || m.startsWith("o1") ||
            m.startsWith("o3") || m.startsWith("o4") || m.contains("codex")
        if (!openai) return SAFE
        //型号越新档位越多：5.6/6 才到 max；5.2~5.5 与 codex-max 到 xhigh；5.1 起有 none；老 gpt-5 有 minimal
        return when {
            m.contains("gpt-6") || m.contains("5.6") -> listOf("low", "medium", "high", "xhigh", "max")
            m.contains("5.2") || m.contains("5.4") || m.contains("5.5") || m.contains("codex-max") ->
                listOf("none", "low", "medium", "high", "xhigh")
            m.contains("5.1") -> listOf("none", "low", "medium", "high")
            m.contains("gpt-5") -> listOf("minimal", "low", "medium", "high")
            else -> SAFE
        }
    } //按厂商与型号给档位表

    fun indexOf(levels: List<String>, effort: String): Int {
        if (levels.isEmpty()) return 0
        val want = effort.lowercase()
        val hit = levels.indexOf(want)
        if (hit >= 0) return hit
        //换模型后旧档位可能不合法：取第一个不低于它的档位，没有就落到最高档（medium → high 与服务端映射一致）
        val wantRank = RANK.indexOf(want).let { if (it < 0) RANK.indexOf("medium") else it }
        val up = levels.firstOrNull { lv -> RANK.indexOf(lv).let { if (it < 0) 0 else it } >= wantRank }
        return levels.indexOf(up ?: levels.last())
    } //把历史档位落到当前表上
} //思考强度档位表

package b.ai.core

import b.ai.R

/** 智能体运行模式：DSH 同款四档（标准 / 代码 / 极简 / 创造）+ 计划模式 */
enum class AgentMode(val id: String, val labelRes: Int, val descRes: Int) {
    PLAN("plan", R.string.mode_plan, R.string.mode_plan_desc),
    STANDARD("standard", R.string.mode_standard, R.string.mode_standard_desc),
    CODE("code", R.string.mode_code, R.string.mode_code_desc),
    MINIMAL("minimal", R.string.mode_minimal, R.string.mode_minimal_desc),
    CREATOR("creator", R.string.mode_creator, R.string.mode_creator_desc);

    companion object {
        /** id 对不上就当标准模式：老配置里没有这个键 */
        fun of(id: String?): AgentMode = entries.firstOrNull { it.id == id } ?: STANDARD
    }
}

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

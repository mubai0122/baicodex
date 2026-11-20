/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:16
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

// 思考配置规则引擎：按模型名匹配决定「思考字段怎么写进请求体」。
// 各家手机端思考参数长得都不一样（thinking.type / enable_thinking / reasoning_effort），
// 与其在代码里 if 一串厂商，不如让规则表说清楚：匹配到哪条规则就按哪条下发。
package b.ai.net.api.llm

import b.ai.core.cfg.app.Cfg
import org.json.JSONArray
import org.json.JSONObject

object ThinkRules {
    /** 滑块的一个档位：显示名给界面，写入路径与写入值决定请求体里塞什么（可覆盖规则默认路径） */
    class Level(
        var label: String = "",
        var path: String = "",
        var value: String = ""
    ) {
        fun toJson(): JSONObject = JSONObject().put("label", label).put("path", path).put("value", value)

        fun copy(
            label: String = this.label,
            path: String = this.path,
            value: String = this.value
        ): Level = Level(label = label, path = path, value = value)
    } //一个档位

    /** 一条规则：谁命中它，就按它写的路径与取值下发思考字段 */
    class Rule(
        /** 本地唯一 id（新建时按时间戳生成） */
        val id: String = System.currentTimeMillis().toString(),
        /** 关掉这条规则就整条跳过，方便临时对比 */
        var on: Boolean = true,
        /** 模型匹配方式：contains（包含）/ exact（完全相同）/ regex（正则） */
        var match: String = "contains",
        /** 匹配用的完整模型名，多个逗号分隔；留空表示「所有模型」 */
        var pattern: String = "",
        /** 思考字段在请求体里的路径，点号分层，如 thinking.type / enable_thinking / reasoning_effort */
        var path: String = "thinking.type",
        /** 思考开启时写进去的值（JSON 片段，字符串要带引号） */
        var onValue: String = "\"enabled\"",
        /** 思考关闭时写进去的值；留空表示关闭时不下发这个字段 */
        var offValue: String = "\"disabled\"",
        /** 旧版档位串（"low","medium","high"）：只用于兼容老配置，新配置走 levels 列表 */
        var legacyLevels: String = "",
        /** 控件形态：slider（多档滑块）/ bool（开关）/ enum（枚举）；只影响 UI 与档位是否可用 */
        var control: String = "slider",
        /** 始终开启思考：不管聊天里的思考开关，一律按开启值下发（部分厂商服务端恒开，需要显式告诉它别关） */
        var alwaysOn: Boolean = false,
        /** 滑块档位：每个档位自带显示名、写入路径、写入值（参考图那套） */
        var levels: List<Level> = emptyList()
    ) {
        fun toJson(): JSONObject {
            val arr = JSONArray()
            levels.forEach { arr.put(it.toJson()) }
            return JSONObject()
                .put("id", id).put("on", on).put("match", match).put("pattern", pattern)
                .put("path", path).put("onValue", onValue).put("offValue", offValue)
                .put("levels", legacyLevels).put("lv", arr)
                .put("control", control).put("alwaysOn", alwaysOn)
        }

        /** 复制一份：弹窗里改副本，保存时才写回，取消不留痕 */
        fun copy(): Rule = Rule(
            id = id, on = on, match = match, pattern = pattern, path = path,
            onValue = onValue, offValue = offValue, legacyLevels = legacyLevels, control = control,
            alwaysOn = alwaysOn, levels = levels.map { it.copy() }
        ) //复制规则

        /** 实际可用的档位：新配置读 levels，老配置把逗号串摊平（路径沿用规则路径） */
        fun levelList(): List<Level> {
            if (levels.isNotEmpty()) return levels
            if (legacyLevels.isBlank()) return emptyList()
            val arr = runCatching { JSONArray("[" + legacyLevels + "]") }.getOrNull() ?: return emptyList()
            val out = ArrayList<Level>(arr.length())
            for (i in 0 until arr.length()) {
                out += Level(label = arr.opt(i)?.toString()?.trim('"').orEmpty(), path = path, value = toLit(arr.opt(i)))
            }
            return out
        } //可用档位

        /** 档位标题：列表副标题里显示「3 档」，没有档位就不显示 */
        fun levelText(): String = levelList().size.toString() + " 档"
    } //一条规则

    /** 把 JSON 值还原成写入用字面量：字符串补引号，数字/布尔直写 */
    private fun toLit(v: Any?): String = when (v) {
        null -> ""
        is String -> "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        else -> v.toString()
    } //还原字面量

    /** 匹配方式的中文名：UI 下拉直接用 */
    val MATCH_NAMES = listOf("contains" to "包含", "exact" to "完全相同", "regex" to "正则")

    /** 造一条档位：显示名 + 写入值，路径沿用规则路径（弹窗里可单独改） */
    private fun lv(name: String, v: String, p: String = ""): Level =
        Level(label = name, path = p, value = v)

    /**
     * 内置规则：匹配的是完整模型名，不是厂商前缀。
     * 多写几个具体型号，命中范围清楚；不确定的型号宁可留给用户自己加，也不写「供应商」这种大范围。
     */
    private fun builtins(): List<Rule> = listOf(
        Rule(
            id = "deepseek", pattern = "deepseek-chat, deepseek-reasoner", match = "contains",
            path = "thinking.type", onValue = "\"enabled\"", offValue = "\"disabled\"",
            levels = listOf(
                lv("关闭", "\"disabled\""), lv("开启", "\"enabled\"")
            )
        ),
        Rule(
            id = "qwen", pattern = "qwen-max, qwen-plus, qwen3-235b-a22b, qwen3-32b", match = "contains",
            path = "enable_thinking", onValue = "true", offValue = "false",
            levels = listOf(lv("关闭", "false"), lv("开启", "true"))
        ),
        Rule(
            id = "glm", pattern = "glm-4.6, glm-4.5, glm-4-plus", match = "contains",
            path = "thinking.type", onValue = "\"enabled\"", offValue = "\"disabled\"",
            levels = listOf(lv("关闭", "\"disabled\""), lv("开启", "\"enabled\""))
        ),
        Rule(
            id = "kimi", pattern = "kimi-k2, moonshot-v1-128k", match = "contains",
            path = "thinking.type", onValue = "\"enabled\"", offValue = "\"disabled\"",
            levels = listOf(lv("关闭", "\"disabled\""), lv("开启", "\"enabled\""))
        ),
        Rule(
            id = "o-series", pattern = "o3, o4-mini, gpt-5", match = "contains",
            path = "reasoning_effort", onValue = "\"medium\"", offValue = "",
            levels = listOf(
                lv("low", "\"low\""), lv("medium", "\"medium\""), lv("high", "\"high\"")
            )
        ),
        Rule(
            id = "claude", pattern = "claude-sonnet-4-5, claude-opus-4-1, claude-3-7-sonnet", match = "contains",
            path = "thinking.type", onValue = "\"enabled\"", offValue = "\"disabled\"",
            levels = listOf(
                lv("low", "\"enabled\"", "thinking.budget_tokens"),
                lv("medium", "\"enabled\"", "thinking.budget_tokens"),
                lv("high", "\"enabled\"", "thinking.budget_tokens")
            )
        ),
        Rule(
            id = "gemini", pattern = "gemini-2.5-pro, gemini-2.5-flash", match = "contains",
            path = "reasoning_effort", onValue = "\"medium\"", offValue = "",
            levels = listOf(lv("low", "\"low\""), lv("medium", "\"medium\""), lv("high", "\"high\""))
        ),
        Rule(
            id = "grok", pattern = "grok-4, grok-3", match = "contains",
            path = "reasoning_effort", onValue = "\"medium\"", offValue = "",
            levels = listOf(lv("low", "\"low\""), lv("high", "\"high\""))
        ),
        Rule(
            id = "doubao", pattern = "doubao-seed-1-6, doubao-1-5-pro-32k", match = "contains",
            path = "thinking.type", onValue = "\"enabled\"", offValue = "\"disabled\"",
            levels = listOf(lv("关闭", "\"disabled\""), lv("开启", "\"enabled\""))
        ),
        Rule(
            id = "hunyuan", pattern = "hunyuan-t1, hunyuan-turbos", match = "contains",
            path = "thinking.type", onValue = "\"enabled\"", offValue = "\"disabled\"",
            levels = listOf(lv("关闭", "\"disabled\""), lv("开启", "\"enabled\""))
        ),
        Rule(
            id = "ernie", pattern = "ernie-4.5-turbo, ernie-x1", match = "contains",
            path = "enable_thinking", onValue = "true", offValue = "false",
            levels = listOf(lv("关闭", "false"), lv("开启", "true"))
        ),
        Rule(
            id = "local", pattern = "llama-3.3-70b, qwen2.5-7b", match = "contains",
            path = "reasoning_effort", onValue = "\"medium\"", offValue = "",
            levels = listOf(lv("low", "\"low\""), lv("medium", "\"medium\""), lv("high", "\"high\""))
        )
    ) //内置规则

    /** 读规则表；空配置时回落内置（不写盘，等用户改了才落） */
    fun list(): List<Rule> {
        val raw = Cfg.thinkRules
        if (raw.isBlank()) return builtins()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return builtins()
        val out = ArrayList<Rule>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            //档位：新格式 lv 数组优先，没有就把老的逗号串原样带上（levelList 会兼容展开）
            val lvArr = o.optJSONArray("lv")
            val levels = ArrayList<Level>()
            if (lvArr != null) {
                for (k in 0 until lvArr.length()) {
                    val lo = lvArr.optJSONObject(k) ?: continue
                    levels += Level(
                        label = lo.optString("label", ""),
                        path = lo.optString("path", ""),
                        value = lo.optString("value", "")
                    )
                }
            }
            out += Rule(
                id = o.optString("id", i.toString()),
                on = o.optBoolean("on", true),
                match = o.optString("match", "contains"),
                pattern = o.optString("pattern", ""),
                path = o.optString("path", "thinking.type"),
                onValue = o.optString("onValue", "\"enabled\""),
                offValue = o.optString("offValue", "\"disabled\""),
                legacyLevels = o.optString("levels", ""),
                control = o.optString("control", "slider"),
                alwaysOn = o.optBoolean("alwaysOn", false),
                levels = levels
            )
        }
        return out.ifEmpty { builtins() }
    } //读规则表

    /** 整表写回（含新增 / 删除 / 编辑），保证不改的就是原样 */
    fun save(rules: List<Rule>) {
        val arr = JSONArray()
        rules.forEach { arr.put(it.toJson()) }
        Cfg.thinkRules = arr.toString()
    } //写规则表

    /** 恢复内置：删掉自定义的那份，读取时自动回落内置 */
    fun reset() {
        Cfg.thinkRules = ""
    } //恢复内置

    /** 找出命中这条模型的第一条启用规则；没有就返回 null（由调用方走默认逻辑） */
    fun hit(model: String): Rule? {
        if (model.isBlank()) return null
        return list().firstOrNull { r ->
            //pattern 留空的规则表示「所有模型」：排在最后当兜底
            if (!r.on) return@firstOrNull false
            if (r.pattern.isBlank()) return@firstOrNull true
            r.pattern.split(',', '，').map { it.trim() }.filter { it.isNotEmpty() }.any { p ->
                when (r.match) {
                    "exact" -> model.equals(p, ignoreCase = true)
                    "regex" -> runCatching { Regex(p, RegexOption.IGNORE_CASE).containsMatchIn(model) }
                        .getOrDefault(false)
                    else -> model.contains(p, ignoreCase = true)
                }
            }
        }
    } //匹配规则

    /** 控件名：UI 与副标题共用 */
    fun controlName(k: String): String = when (k) {
        "bool" -> "开关"
        "enum" -> "枚举"
        else -> "多档滑块"
    } //控件名

    /**
     * 把规则写进请求体：
     * think = 本轮是否要思考；level = 强度档位 1..5（0 表示按 thinkEffort 折算）。
     * 返回 true 表示已经按规则处理过，调用方就不要再走内置的厂商判断。
     */
    fun apply(body: JSONObject, model: String, think: Boolean, level: Int): Boolean {
        val r = hit(model) ?: return false
        //始终开启思考：配置里写死要开，就不看聊天里的开关
        val wantThink = think || r.alwaysOn
        //先按开 / 关定值：关的时候可以不下发（offValue 空），这样「关思考」就是让服务端回到默认
        val lvList = r.levelList()
        val picked = if (wantThink && level > 0) lvList.getOrNull(level - 1) else null
        val valText = if (wantThink) picked?.value?.ifBlank { r.onValue } ?: r.onValue else r.offValue
        if (valText.isBlank()) return true
        val v = runCatching { JSONObject("{\"v\":" + valText + "}").get("v") }
            .getOrElse { valText }
        //档位可以带自己的写入路径（例如 Claude 的 budget_tokens），没写就沿用规则路径
        val path = if (wantThink && picked?.path?.isNotBlank() == true) picked.path else r.path
        putPath(body, path, v)
        return true
    } //按规则写请求体

    /** 点号路径写入：只有最后一段自动补对象，避免把厂商结构写坏 */
    private fun putPath(body: JSONObject, path: String, value: Any?) {
        val seg = path.split('.').filter { it.isNotBlank() }
        if (seg.isEmpty()) return
        var cur = body
        for (i in 0 until seg.size - 1) {
            cur = cur.optJSONObject(seg[i]) ?: JSONObject().also { cur.put(seg[i], it) }
        }
        cur.put(seg.last(), value)
    } //点号路径写入
} //思考配置规则引擎

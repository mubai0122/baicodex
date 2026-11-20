/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:10
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package b.ai.core.task.alarm

import android.content.Context
import b.ai.core.cfg.io.Prefs
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.UUID

/** 一条定时任务：到点把 prompt 交给 Agent 执行 */
data class Task(
    val id: String,
    val prompt: String,
    val at: Long,
    val daily: Boolean,
    val auto: Boolean,
    val on: Boolean
)

/** 定时任务库：整表 JSON 存在 bai_cfg 里，任何改动立刻落盘 */
object Schedules {
    private val cache = mutableStateOf<List<Task>>(emptyList())
    private var sp: Prefs? = null

    val list: List<Task> get() = cache.value
    /** 参与调度的任务：开关打开且时间没被清掉 */
    val active: List<Task> get() = cache.value.filter { it.on && it.at > 0L }

    fun init(c: Context) {
        if (sp != null) return
        val p = Prefs.get(c)
        sp = p
        cache.value = runCatching { parse(p.getString("tasks", "")) }.getOrDefault(emptyList())
    } //读盘

    private fun parse(raw: String?): List<Task> {
        if (raw.isNullOrBlank()) return emptyList()
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val o = arr.optJSONObject(i) ?: JSONObject()
            Task(
                id = o.optString("id"),
                prompt = o.optString("prompt"),
                at = o.optLong("at", 0L),
                daily = o.optBoolean("daily", false),
                auto = o.optBoolean("auto", true),
                on = o.optBoolean("on", true)
            )
        }.filter { it.id.isNotBlank() && it.prompt.isNotBlank() }
    } //从 JSON 还原

    private fun save() {
        val arr = JSONArray()
        cache.value.forEach { t ->
            arr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("prompt", t.prompt)
                    .put("at", t.at)
                    .put("daily", t.daily)
                    .put("auto", t.auto)
                    .put("on", t.on)
            )
        }
        sp?.edit()?.putString("tasks", arr.toString())?.apply()
    } //整表落盘

    fun byId(id: String): Task? = cache.value.firstOrNull { it.id == id }

    fun add(prompt: String, at: Long, daily: Boolean, auto: Boolean): Task {
        val t = Task(
            id = UUID.randomUUID().toString(),
            prompt = prompt.trim(),
            at = at,
            daily = daily,
            auto = auto,
            on = true
        )
        cache.value = cache.value + t
        save()
        return t
    } //新增

    fun remove(id: String) {
        cache.value = cache.value.filterNot { it.id == id }
        save()
    } //删除

    fun toggle(id: String, on: Boolean) {
        cache.value = cache.value.map { if (it.id == id) it.copy(on = on) else it }
        save()
    } //开关

    fun touch(id: String, at: Long) {
        cache.value = cache.value.map { if (it.id == id) it.copy(at = at) else it }
        save()
    } //写回下次触发时间

    /** 每天重复的任务顺延到下一个触发点；单次的保持原值（过期就由调度层丢掉） */
    fun rollForward(at: Long, daily: Boolean, now: Long): Long {
        if (!daily) return at
        var t = at
        val cal = Calendar.getInstance()
        var guard = 0
        while (t <= now && guard < 400) {
            cal.timeInMillis = t
            cal.add(Calendar.DAY_OF_YEAR, 1)
            t = cal.timeInMillis
            guard++
        }
        return t
    } //顺延下一次
} //定时任务库
/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27 04:30:00
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * 定时任务：到点把提示词发进会话，由模型照常执行（工具、思考都照常）。
 * tasks.json 存 <appDir>/；检查器每 30 秒扫一次，跨天不追补（错过了就等下次）。
 * 「仅今天」的任务执行完自动禁用；「每天」的长期跑。
 */

package b.ai.desktop.core.schedule

import b.ai.desktop.core.config.ConfigStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Serializable
data class ScheduledTask(
    val id: String = java.util.UUID.randomUUID().toString().take(8),
    val label: String = "",
    /** HH:mm，到点触发 */
    val time: String = "09:00",
    /** 到点要发给模型的提示词 */
    val prompt: String = "",
    /** daily = 每天；once = 仅今天 */
    val repeat: String = "daily",
    /** 仅今天：触发一次后自动禁用 */
    val enabled: Boolean = true,
    /** 上次触发的日期，防同一天重复 */
    val lastRunDate: String = "",
)

object Scheduler {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    private fun file(): File = File(ConfigStore.appDir(), "tasks.json")

    fun load(): List<ScheduledTask> = runCatching {
        val f = file()
        if (!f.exists()) emptyList() else json.decodeFromString<List<ScheduledTask>>(f.readText())
    }.getOrDefault(emptyList())

    fun save(tasks: List<ScheduledTask>) {
        runCatching { file().writeText(json.encodeToString(tasks)) }
    }

    private val pool = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "baicodex-scheduler").apply { isDaemon = true }
    }

    /** 检查器：每 30 秒；due 的任务回调给 AppState（在后台线程回调，由它自己切 UI 线程） */
    fun start(onDue: (ScheduledTask) -> Unit) {
        pool.scheduleAtFixedRate({
            runCatching {
                val tasks = load()
                val today = LocalDate.now().toString()
                val now = LocalTime.now().withSecond(0).withNano(0)
                var dirty = false
                tasks.forEach { task ->
                    if (!task.enabled) return@forEach
                    if (task.lastRunDate == today) return@forEach
                    val due = runCatching { LocalTime.parse(task.time) }.getOrNull() ?: return@forEach
                    if (!now.isBefore(due)) {
                        // 到点了（含应用这个时段才启动的情况）：触发一次
                        onDue(task)
                        dirty = true
                    }
                }
                if (dirty) {
                    save(tasks.map {
                        when {
                            !it.enabled -> it
                            it.lastRunDate == today -> it
                            else -> it.copy(lastRunDate = today, enabled = it.repeat == "daily")
                        }
                    })
                }
            }
        }, 15, 30, TimeUnit.SECONDS)
    }

    /** 立即触发一次（设置页按钮；落 lastRunDate 防止到点再跑一遍） */
    fun markRanToday(id: String) {
        val today = LocalDate.now().toString()
        save(load().map { if (it.id == id) it.copy(lastRunDate = today) else it })
    }

    fun parseDue(task: ScheduledTask, now: LocalDateTime = LocalDateTime.now()): Boolean =
        runCatching { !now.toLocalTime().withSecond(0).isBefore(LocalTime.parse(task.time)) }.getOrDefault(false)
}

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

import b.ai.core.cfg.io.Store
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 提示词标签库：一条 = 名字 + 正文。
 * 两种用法：插入当前输入框（只对这一次发言生效）、全局启用（每轮拼进系统提示）。
 * 存数据根目录的 prompt_tags.json，跟角色卡一样是文件不是新表。
 */
object PromptTags {
    data class Tag(val id: String, val name: String, val body: String, val on: Boolean = false)

    private const val FILE = "prompt_tags.json"
    private var cache: List<Tag>? = null

    fun list(): List<Tag> {
        val c = cache
        if (c != null) return c
        val l = load()
        cache = l
        return l
    } //全部标签

    /** 全局启用中且正文非空的那些：正文空的启用也没意义 */
    fun on(): List<Tag> = list().filter { it.on && it.body.isNotBlank() }

    /** 拼进系统提示；没有启用项返回空串，调用方用 isNotBlank 判断 */
    fun prompt(): String {
        val l = on()
        if (l.isEmpty()) return ""
        return buildString {
            append("补充要求（用户设定的提示词片段，按条执行）：\n")
            l.forEach { append("- ").append(it.name.ifBlank { "片段" }).append("：").append(it.body.trim()).append("\n") }
        }.trim()
    } //拼系统提示

    fun add(name: String, body: String): Tag {
        val t = Tag(UUID.randomUUID().toString(), name.trim().ifBlank { "未命名片段" }, body.trim())
        cache = list() + t
        save()
        return t
    } //新建

    fun update(t: Tag) {
        cache = list().map { if (it.id == t.id) t.copy(name = t.name.trim().ifBlank { "未命名片段" }, body = t.body.trim()) else it }
        save()
    } //改内容

    fun remove(id: String) {
        cache = list().filterNot { it.id == id }
        save()
    } //删除

    fun setOn(id: String, on: Boolean) {
        cache = list().map { if (it.id == id) it.copy(on = on) else it }
        save()
    } //启用开关

    private fun load(): List<Tag> {
        val raw = Store.read(FILE) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id").ifBlank { UUID.randomUUID().toString() }
                val name = o.optString("name")
                val body = o.optString("body")
                //名字与正文都空的条目当坏档丢掉，免得列表里出现摸不到的空行
                if (name.isBlank() && body.isBlank()) return@mapNotNull null
                Tag(id, name, body, o.optBoolean("on"))
            }
        }.getOrDefault(emptyList())
    } //读盘

    private fun save() {
        val arr = JSONArray()
        list().forEach { t ->
            arr.put(JSONObject().put("id", t.id).put("name", t.name).put("body", t.body).put("on", t.on))
        }
        Store.why(FILE, arr.toString(2))
    } //落盘
} //提示词标签库
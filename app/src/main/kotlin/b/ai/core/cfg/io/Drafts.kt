/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:05
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

// 会话草稿：每个会话一份 .prkcovs（JSON），存输入框文字与待发送的附件。
// 图片默认不落盘（隐私 + 体积），只有用户在设置里打开才一起存；超过有效期自动丢弃。
package b.ai.core.cfg.io

import b.ai.core.cfg.app.Cfg
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object Drafts {
    /** 草稿目录：数据根下 drafts/，每会话一个 <sid>.prkcovs */
    private fun dir(): File = File(Paths.base(), "drafts")

    private fun file(sid: String): File = File(dir(), "$sid.prkcovs")

    /** 一份草稿：文字 + 附件 + 写入时间 */
    data class Draft(
        val text: String = "",
        val images: List<String> = emptyList(),
        val files: List<String> = emptyList(),
        val at: Long = 0L
    ) {
        /** 全空就是没有草稿，调用方不用再判三遍 */
        fun empty(): Boolean = text.isBlank() && images.isEmpty() && files.isEmpty()
    } //草稿

    /**
     * 读草稿。文件不存在 / 过期 / 解析失败一律返回空草稿。
     * 图片持久化关着时，就算历史草稿里存了图也不还原（用户明确关掉的东西不能偷偷回来）。
     */
    fun load(sid: String): Draft {
        if (sid.isBlank()) return Draft()
        val f = file(sid)
        if (!f.isFile) return Draft()
        val d = runCatching {
            val o = JSONObject(f.readText())
            Draft(
                text = o.optString("text"),
                images = strList(o.optJSONArray("images")),
                files = strList(o.optJSONArray("files")),
                at = o.optLong("at")
            )
        }.getOrElse { Draft() }
        //有效期：0 表示不过期；超过就删档
        val days = Cfg.draftDays
        if (days > 0 && d.at > 0 && System.currentTimeMillis() - d.at > days * 86_400_000L) {
            runCatching { f.delete() }
            return Draft()
        }
        val imgs = if (Cfg.draftKeepImg) d.images.filter { File(it).isFile } else emptyList()
        val fs = d.files.filter { File(it).isFile }
        return Draft(d.text, imgs, fs, d.at)
    } //读草稿

    /**
     * 写草稿：全空就删档（省得目录里攒一堆空文件）。
     * 图片按 Cfg.draftKeepImg 决定要不要一起存；文件是路径引用，体积小，一直存。
     */
    fun save(sid: String, text: String, images: List<String> = emptyList(), files: List<String> = emptyList()) {
        if (sid.isBlank()) return
        val f = file(sid)
        val keepImgs = if (Cfg.draftKeepImg) images else emptyList()
        if (text.isBlank() && keepImgs.isEmpty() && files.isEmpty()) {
            runCatching { f.delete() }
            return
        }
        val o = JSONObject()
            .put("text", text)
            .put("images", JSONArray(keepImgs))
            .put("files", JSONArray(files))
            .put("at", System.currentTimeMillis())
        runCatching {
            dir().mkdirs()
            f.writeText(o.toString())
        }
    } //写草稿

    /** 清掉某个会话的草稿（发送成功后调用） */
    fun clear(sid: String) {
        if (sid.isBlank()) return
        runCatching { file(sid).delete() }
    } //删草稿

    /** 过期清理：顺手删掉所有超期文件，切会话时调一次就够 */
    fun sweep() {
        val days = Cfg.draftDays
        if (days <= 0) return
        val limit = days * 86_400_000L
        val now = System.currentTimeMillis()
        runCatching {
            dir().listFiles()?.forEach { f ->
                if (now - f.lastModified() > limit) runCatching { f.delete() }
            }
        }
    } //清理过期

    private fun strList(a: JSONArray?): List<String> {
        if (a == null) return emptyList()
        return (0 until a.length()).mapNotNull { a.optString(it).ifBlank { null } }
    } //JSON 数组 → 字符串列表
} //会话草稿
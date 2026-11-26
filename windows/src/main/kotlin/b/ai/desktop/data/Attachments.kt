/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:15
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baicodex Windows - 附件数据模型
 */

package b.ai.desktop.data

/** 附件种类（对齐手机端 AttachPanel 的条目） */
enum class AttachmentKind(
    val label: String,
    val desc: String,
    /** 文件选择器允许的扩展名；空表示不限 */
    val extensions: List<String>,
) {
    IMAGE("图片", "选择图片文件", listOf("png", "jpg", "jpeg", "webp", "gif", "bmp")),
    FILE("文件", "任意格式文件", emptyList()),
    AUDIO("音频", "音频文件", listOf("mp3", "wav", "flac", "m4a", "ogg", "aac")),
    VIDEO("视频", "视频文件", listOf("mp4", "mov", "mkv", "webm", "avi")),
    MUSIC("音乐", "音乐文件", listOf("mp3", "flac", "wav", "m4a")),
}

/** 已挂载到输入区的附件 */
data class Attachment(
    val kind: AttachmentKind,
    val name: String,
    val path: String? = null,
) {
    /** 输入区/气泡上显示的简短标识 */
    val badge: String get() = "${kind.label}·$name"
}

/** 由文件路径推断附件类型（拖入、选择共用） */
fun attachmentFromPath(path: String): Attachment {
    val name = path.substringAfterLast('\\').substringAfterLast('/')
    val ext = name.substringAfterLast('.', "").lowercase()
    val kind = when (ext) {
        "png", "jpg", "jpeg", "webp", "gif", "bmp" -> AttachmentKind.IMAGE
        "mp4", "mov", "mkv", "webm", "avi" -> AttachmentKind.VIDEO
        "mp3", "flac", "wav", "m4a", "ogg", "aac" -> AttachmentKind.AUDIO
        else -> AttachmentKind.FILE
    }
    return Attachment(kind = kind, name = name, path = path)
}

/** 提示词标签预设（MVP：常用片段直接插入输入框） */
val presetPromptTags = listOf(
    "总结要点" to "请用要点形式总结下面内容的重点：\n",
    "翻译成英文" to "请把下面内容翻译成地道的英文：\n",
    "代码审查" to "请审查下面的代码，指出潜在问题和改进建议：\n",
)

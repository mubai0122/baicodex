/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:06
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package b.ai.core.data.io
import b.ai.net.api.llm.text

import b.ai.App
import b.ai.device.fs.Workspace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/** 粘贴超长文本转文件：落工作区 .paste/，两分钟没人用就删掉 */
object Pastes {
    /** 存活时长：两分钟后自动清理 */
    const val TTL_MS = 120_000L
    const val DIR = "paste"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    //已经被用户挂成附件的文件：TTL 和 purge 都不许再动它，否则还没点发送就被删了
    private val kept = java.util.Collections.synchronizedSet(HashSet<String>())

    /** 存放目录：优先跟工作区走，没有工作区就落应用私有目录 */
    fun dir(): File {
        val ws = Workspace.path()
        val base = if (ws.isNotBlank()) File(ws) else Layout.dir(App.self, Layout.CACHE)
        return File(base, DIR)
    } //粘贴文件目录

    /** 写一份粘贴文本，返回绝对路径；失败返回 null（调用方就当没转存） */
    fun save(text: String): String? = runCatching {
        val d = dir()
        if (!d.isDirectory && !d.mkdirs()) return null
        val name = "paste_" + System.currentTimeMillis() + "_" + Integer.toHexString(text.hashCode()) + ".txt"
        val f = File(d, name)
        f.writeText(text)
        //两分钟到点自己删；进程被杀就靠 purge 兜底
        scope.launch {
            delay(TTL_MS)
            if (!kept.contains(f.absolutePath)) runCatching { f.delete() }
        }
        f.absolutePath
    }.getOrNull() //转存

    /** 挂成附件：取消这两分钟的死期，文件转为长期保留（模型发送后还要读它） */
    fun keep(path: String) {
        kept.add(path)
    } //取消自动清理

    /** 用户从附件里删掉：连文件一起清 */
    fun forget(path: String) {
        kept.remove(path)
        scope.launch { runCatching { File(path).delete() } }
    } //删附件文件

    /** 清掉过期的粘贴文件：App 启动与每次转存都顺手扫一遍 */
    fun purge() {
        scope.launch {
            val cut = System.currentTimeMillis() - TTL_MS
            dir().listFiles()?.forEach { f ->
                if (f.isFile && f.lastModified() < cut && !kept.contains(f.absolutePath)) runCatching { f.delete() }
            }
        }
    } //清过期
}
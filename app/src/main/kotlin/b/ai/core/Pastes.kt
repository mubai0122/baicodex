package b.ai.core

import b.ai.App
import b.ai.exec.Workspace
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
    const val DIR = ".paste"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    //已经被用户挂成附件的文件：TTL 和 purge 都不许再动它，否则还没点发送就被删了
    private val kept = java.util.Collections.synchronizedSet(HashSet<String>())

    /** 存放目录：优先跟工作区走，没有工作区就落应用私有目录 */
    fun dir(): File {
        val ws = Workspace.path()
        val base = if (ws.isNotBlank()) File(ws) else App.self.filesDir
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
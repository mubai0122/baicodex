/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-24
 * Repository: https://github.com/mubai0122/baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * DeepSeek 官方导出包（export_format_version 3）的下载与导入。
 * 包结构：manifest.json / user.json / chat_session_index.json /
 *        chat_sessions/<uuid>.json / chat_sessions/<uuid>_files/<文件名>
 * 只取正文、思考与附件；搜索、打开网页这类工具片段直接丢弃（离开 DeepSeek 的上下文没有意义）。
 */

package b.ai.core.data.io

import b.ai.core.cfg.app.Cfg
import b.ai.core.data.db.Repo
import b.ai.core.model.chat.ChatMsg
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipFile

object DeepSeekImport {
    /** 下载时声明的 Accept：任何类型都收下 */
    private const val ACCEPT: String = "*" + "/" + "*"
    /** 导入结果：会话数 / 消息数 / 附件数，err 非空表示整体失败 */
    data class Result(
        val sessions: Int = 0,
        val msgs: Int = 0,
        val files: Int = 0,
        val err: String? = null
    ) //导入结果

    /**
     * 下载导出包到 out。
     * 返回 null 表示成功，否则是中文错误文案。onProgress(已读字节, 总字节)，总字节未知时为 -1。
     */
    fun download(url: String, out: File, onProgress: (Long, Long) -> Unit): String? {
        var u = url.trim()
        if (u.isEmpty()) return "先粘贴导出链接"
        var hops = 0
        while (hops++ < 6) {
            val c = runCatching { URL(u).openConnection() as HttpURLConnection }.getOrNull()
                ?: return "链接格式不对"
            try {
                c.connectTimeout = 20000
                c.readTimeout = 45000
                c.instanceFollowRedirects = false //自己跟跳转，OBS 直链会 302 到内网签名地址
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) Baicodex")
                //注意：源码里不能出现星号加斜杠的连写（Kotlin 词法会当成块注释结束，直接报语法错），用拼接绕开
                c.setRequestProperty("Accept", ACCEPT)
                val code = c.responseCode
                if (code in 300..399) {
                    val loc = c.getHeaderField("Location")
                    if (loc.isNullOrBlank()) return "重定向缺少地址（$code）"
                    u = if (loc.startsWith("http")) loc else URL(URL(u), loc).toString()
                    c.disconnect()
                    continue
                }
                if (code != 200) return "服务器返回 HTTP $code"
                val total = c.contentLengthLong
                val parent = out.parentFile
                if (parent != null && !parent.exists()) parent.mkdirs()
                c.inputStream.use { ins ->
                    FileOutputStream(out).use { os ->
                        val buf = ByteArray(64 * 1024)
                        var got = 0L
                        while (true) {
                            val n = ins.read(buf)
                            if (n < 0) break
                            os.write(buf, 0, n)
                            got += n
                            onProgress(got, total)
                        }
                    }
                }
                return null
            } catch (e: Exception) {
                return e.message ?: "下载失败"
            } finally {
                runCatching { c.disconnect() }
            }
        }
        return "重定向次数过多"
    } //下载导出包

    /**
     * 解析并导入。
     * wsPath 为当前工作区（空串表示没绑，此时不导附件），附件落在 <工作区>/deepseek_import/<会话id>/ 下。
     */
    fun importZip(zip: File, wsPath: String, onStep: (String) -> Unit): Result {
        if (!zip.isFile) return Result(err = "文件不存在：" + zip.name)
        var sessions = 0
        var msgs = 0
        var files = 0
        return runCatching {
            ZipFile(zip).use { z ->
                val all = z.entries().toList().map { it.name }
                val list = all.filter {
                    it.startsWith("chat_sessions/") && it.endsWith(".json") && !it.contains("_files/")
                }
                if (list.isEmpty()) return@use Result(err = "这不是 DeepSeek 导出的历史对话包")
                list.forEachIndexed { idx, path ->
                    val text = runCatching {
                        z.getInputStream(z.getEntry(path)).use { it.readBytes().toString(Charsets.UTF_8) }
                    }.getOrNull() ?: return@forEachIndexed
                    val root = runCatching { JSONObject(text) }.getOrNull() ?: return@forEachIndexed
                    val title = root.optString("title").ifBlank { "DeepSeek 对话" }
                    val sid = root.optString("id")
                    onStep("正在导入 ${idx + 1}/${list.size}：$title")
                    //后缀受设置控制：关掉就按原标题导入
                    val name = if (Cfg.importSuffix) "$title（DeepSeek）" else title
                    val row = Repo.newSession(name, "默认")
                    if (wsPath.isNotBlank()) runCatching { Repo.setWorkspace(row.id, wsPath) }
                    sessions++

                    val arr = root.optJSONArray("messages") ?: JSONArray()
                    for (i in 0 until arr.length()) {
                        val msg = arr.optJSONObject(i) ?: continue
                        val user = msg.optString("role").equals("USER", true)
                        val frs = msg.optJSONArray("fragments") ?: JSONArray()
                        val body = StringBuilder()
                        val think = StringBuilder()
                        val imgs = ArrayList<String>()
                        val others = ArrayList<String>()
                        for (k in 0 until frs.length()) {
                            val fr = frs.optJSONObject(k) ?: continue
                            when (fr.optString("type")) {
                                "REQUEST" -> if (user) append(body, fr.optString("content"))
                                "RESPONSE" -> if (!user) append(body, fr.optString("content"))
                                "THINK" -> append(think, fr.optString("content"))
                                "FILE" -> {
                                    //附件只在绑了工作区时落地，否则跳过（避免散落到应用私有目录）
                                    if (wsPath.isBlank()) continue
                                    val fa = fr.optJSONArray("files") ?: JSONArray()
                                    for (t in 0 until fa.length()) {
                                        val fo = fa.optJSONObject(t) ?: continue
                                        val dst = copyEntry(z, fo.optString("export_path"), wsPath, sid, fo.optString("file_name"))
                                        if (dst != null) {
                                            files++
                                            if (fo.optBoolean("is_image")) imgs.add(dst) else others.add(dst)
                                        }
                                    }
                                }
                                else -> Unit //TOOL_SEARCH / TOOL_OPEN / CONTENT_RISK 等一律丢弃
                            }
                        }
                        val textBody = body.toString().trim()
                        val textThink = think.toString().trim()
                        if (user) {
                            if (textBody.isBlank() && imgs.isEmpty() && others.isEmpty()) continue
                            Repo.append(
                                row.id,
                                ChatMsg(role = "user", content = textBody, images = imgs, files = others)
                            )
                            msgs++
                        } else {
                            if (textBody.isBlank() && textThink.isBlank()) continue
                            Repo.append(
                                row.id,
                                ChatMsg(role = "assistant", content = textBody, reasoning = textThink)
                            )
                            msgs++
                        }
                    }
                }
                Result(sessions = sessions, msgs = msgs, files = files)
            }
        }.getOrElse { Result(err = it.message ?: "解析失败") }
    } //解析并入库

    /** 多段文本拼接：中间留空行，避免两段挤成一行 */
    private fun append(sb: StringBuilder, s: String) {
        if (s.isBlank()) return
        if (sb.isNotEmpty()) sb.append("\n\n")
        sb.append(s.trim())
    } //拼接片段

    /** 从 zip 里抽出单个附件到工作区，返回绝对路径；失败返回 null */
    private fun copyEntry(z: ZipFile, path: String, wsPath: String, sid: String, name: String): String? {
        if (path.isBlank()) return null
        val e = z.getEntry(path) ?: return null
        val safe = name.ifBlank { path.substringAfterLast('/') }.replace('/', '_')
        val dir = File(File(wsPath, "deepseek_import"), sid.ifBlank { "unknown" })
        val dst = File(dir, safe)
        return runCatching {
            if (!dir.exists()) dir.mkdirs()
            z.getInputStream(e).use { ins -> FileOutputStream(dst).use { os -> ins.copyTo(os) } }
            dst.absolutePath
        }.getOrNull()
    } //导出单个附件
} //DeepSeek 导出包导入
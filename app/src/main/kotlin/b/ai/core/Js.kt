package b.ai.core

import android.util.Log
import b.ai.App
import b.ai.agent.Tools
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * QuickJS 门面：运行时在 cpp/js/qjs.cpp，这里只负责长会话、宿主工具桥、脚本目录与注册工具表。
 * 会话必须复用：registerTool 注册的函数挂在 JS 上下文里，换会话就全丢。
 */
object Js {
    private const val TAG = "Js"

    @Volatile
    private var loaded = false

    init {
        loaded = runCatching { System.loadLibrary("baicodex") }.isSuccess
    } //载入 native 库

    val ready: Boolean get() = loaded

    /** 脚本注册的工具：schema 给模型看，name 用来分发 */
    data class Def(val name: String, val description: String, val parameters: String)

    @Volatile
    private var defs: List<Def> = emptyList()
    private val io = Any()
    private var handle = 0L
    private var bridge: Bridge? = null

    val tools: List<Def> get() = defs

    fun has(name: String): Boolean = defs.any { it.name == name }

    /** 跑一段脚本：脚本里的 bai.call(工具名, 参数) 会同步调回 App 的工具 */
    fun run(code: String, timeoutMs: Int = 15_000): Pair<Boolean, String> = guard { h ->
        val r = nativeEval(h, code, "bai.js", timeoutMs)
        unpack(r)
    }

    /** 调脚本注册的工具：参数对象原样传给脚本里的 run(args) */
    fun call(name: String, args: JSONObject, timeoutMs: Int = 60_000): Pair<Boolean, String> = guard { h ->
        val r = nativeCallTool(h, name, args.toString(), timeoutMs)
        unpack(r)
    }

    /** 脚本目录：内部私有目录 + 外部 baicode/js（用户直接丢文件进去就能注册工具） */
    private fun dirs(): List<File> =
        listOf(File(App.self.filesDir, "js"), File(Paths.ensure(), "js"))

    /** 启动时把脚本都跑一遍，脚本里 registerTool 的工具就此可用 */
    fun loadScripts() {
        if (!loaded) return
        var ok = 0
        var bad = 0
        for (d in dirs()) {
            val files = d.listFiles { f -> f.isFile && f.name.endsWith(".js") }?.sortedBy { it.name } ?: continue
            for (f in files) {
                val code = runCatching { f.readText() }.getOrElse {
                    bad++
                    continue
                }
                val r = run(code, 15_000)
                if (r.first) {
                    ok++
                } else {
                    bad++
                    Log.w(TAG, "${f.name} 加载失败：${r.second}")
                }
            }
        }
        Log.i(TAG, "JS 脚本加载：成功 $ok / 失败 $bad，注册工具 ${defs.size} 个")
    } //加载脚本目录

    private fun guard(block: (Long) -> Pair<Boolean, String>): Pair<Boolean, String> {
        if (!loaded) return false to "JS 引擎未加载（libbaicodex.so 没起来）"
        synchronized(io) {
            val h = ensure() ?: return false to "JS 运行时建不起来（内存不够？）"
            val out = block(h)
            if (out.first) refresh()
            return out
        }
    } //加锁跑

    private fun ensure(): Long? {
        if (handle != 0L) return handle
        val h = nativeOpen(32, 1024)
        if (h == 0L) return null
        val b = Bridge()
        nativeSetBridge(h, b)
        bridge = b
        handle = h
        return h
    } //建长会话

    private fun refresh() {
        val raw = runCatching { nativeTools(handle) }.getOrElse { return }
        defs = runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Def(
                    o.optString("name"),
                    o.optString("description"),
                    o.optString("parameters", "{\"type\":\"object\",\"properties\":{}}")
                )
            }
        }.getOrElse { emptyList() }
    } //刷新注册工具表

    private fun unpack(r: String): Pair<Boolean, String> =
        if (r.startsWith("OK\n")) true to r.substring(3) else false to r.removePrefix("ERR\n") //拆 C 层返回

    /**
     * 宿主工具桥：JS 里的 bai.call 走这里。JSON 参数解析失败就按空对象传，
     * 禁止脚本再调 js 自己（会无限套娃）。
     */
    class Bridge {
        fun call(name: String, argsJson: String): String {
            if (name == Tools.JS) return "ERR\n脚本里不能再调 js 工具本身"
            val args = runCatching { JSONObject(argsJson.ifBlank { "{}" }) }.getOrElse { JSONObject() }
            val out = runCatching { Tools.execute(name, args) }.getOrElse {
                return "ERR\n工具 $name 执行抛异常：${it.message}"
            }
            return (if (out.ok) "OK\n" else "ERR\n") + out.text
        }
    } //宿主工具桥

    private external fun nativeOpen(memMb: Int, stackKb: Int): Long
    private external fun nativeSetBridge(h: Long, bridge: Bridge)
    private external fun nativeEval(h: Long, code: String, name: String, timeoutMs: Int): String
    private external fun nativeTools(h: Long): String
    private external fun nativeCallTool(h: Long, name: String, args: String, timeoutMs: Int): String
}
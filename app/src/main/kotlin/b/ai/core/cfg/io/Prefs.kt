package b.ai.core.cfg.io

import android.content.Context
import android.os.Build
import b.ai.core.data.io.Layout
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 配置库：替掉 SharedPreferences。
 *
 * 为什么换：SP 的文件位置系统写死成 /data/user/0/<包名>/shared_prefs 下的 bai_cfg.xml，想归到 data/ 根本动不了；
 * 而且每次 apply() 都要整份 XML 序列化 + 落盘，键多起来之后改一个开关也是全量写。
 *
 * 现在：data/prefs/<库名>.json，一库一文件；改动只进内存，250ms 内的连续改动合并成一次落盘
 * （临时文件写完原子替换，写到一半被杀不会留下半截 json），切后台/退出再强制刷一次。
 *
 * 老库不丢：某个库第一次打开时若新文件不存在，用同名 SharedPreferences 读一遍全量导进来，
 * 老 xml 原样留着不动。备份导入走 [importFromSp]，可以在新文件已存在时强制覆盖。
 */
class Prefs internal constructor(private val ctx: Context, val name: String) {
    private val file = Layout.file(ctx, Layout.PREFS, "$name.json")
    private val tmp = File(file.parentFile, "$name.tmp")
    private val map = HashMap<String, Any>()
    private val lock = Any()
    /** 写盘队列：单线程串行，进程被杀最多丢最近 250ms 的配置改动 */
    private val queued = AtomicBoolean(false)
    private val io = Executors.newSingleThreadExecutor { r ->
        Thread(r, "prefs-io").apply { isDaemon = true }
    }
    private var loaded = false

    init {
        load()
    }

    private fun load() {
        synchronized(lock) {
            if (loaded) return
            loaded = true
            if (!file.isFile) {
                importFromSp()
                return
            }
            runCatching {
                val o = JSONObject(file.readText())
                o.keys().forEach { k -> dec(o.getString(k))?.let { v -> map[k] = v } }
            }.onFailure { android.util.Log.w("Prefs", "读不出 $name.json：" + it.message) }
        }
    } //首次读盘

    fun getString(key: String, def: String?): String? = synchronized(lock) {
        when (val v = map[key]) {
            is String -> v
            null -> def
            else -> v.toString()
        }
    }

    fun getInt(key: String, def: Int): Int = synchronized(lock) { (map[key] as? Number)?.toInt() ?: def }

    fun getLong(key: String, def: Long): Long = synchronized(lock) { (map[key] as? Number)?.toLong() ?: def }

    fun getFloat(key: String, def: Float): Float = synchronized(lock) { (map[key] as? Number)?.toFloat() ?: def }

    fun getBoolean(key: String, def: Boolean): Boolean = synchronized(lock) { map[key] as? Boolean ?: def }

    @Suppress("UNCHECKED_CAST")
    fun getStringSet(key: String, def: Set<String>?): Set<String>? = synchronized(lock) {
        map[key] as? Set<String> ?: def
    }

    /** 全量快照：迁移与备份要遍历，返回副本不给外面改内存表 */
    fun all(): Map<String, Any?> = synchronized(lock) { HashMap<String, Any?>(map) }

    fun contains(key: String): Boolean = synchronized(lock) { map.containsKey(key) }

    fun edit(): Ed = Ed()

    /** 一次批量改动：apply 只合并落盘，commit 立刻写完返回 */
    inner class Ed {
        private val puts = LinkedHashMap<String, Any?>()
        private val rms = ArrayList<String>()
        private var clr = false

        fun putString(k: String, v: String?): Ed = apply { puts[k] = v }

        fun putInt(k: String, v: Int): Ed = apply { puts[k] = v }

        fun putLong(k: String, v: Long): Ed = apply { puts[k] = v }

        fun putFloat(k: String, v: Float): Ed = apply { puts[k] = v }

        fun putBoolean(k: String, v: Boolean): Ed = apply { puts[k] = v }

        fun putStringSet(k: String, v: Set<String>?): Ed = apply { puts[k] = v }

        fun remove(k: String): Ed = apply { rms.add(k) }

        fun clear(): Ed = apply { clr = true }

        fun apply() {
            merge()
            schedule()
        }

        fun commit(): Boolean {
            merge()
            write()
            return true
        }

        private fun merge() = synchronized(lock) {
            if (clr) map.clear()
            rms.forEach { map.remove(it) }
            puts.forEach { (k, v) -> if (v == null) map.remove(k) else map[k] = v }
        }
    }

    /** 把内存表立刻写盘：切后台、退出、备份前调 */
    fun flush() {
        write()
    }

    private fun schedule() {
        if (!queued.compareAndSet(false, true)) return
        io.execute {
            runCatching { Thread.sleep(250) }
            queued.set(false)
            write()
        }
    } //合并落盘

    private fun write() = synchronized(lock) {
        runCatching {
            val o = JSONObject()
            map.forEach { (k, v) -> o.put(k, enc(v)) }
            file.parentFile?.mkdirs()
            tmp.writeText(o.toString())
            if (!replace(tmp, file)) android.util.Log.w("Prefs", "写不动 $name.json")
        }.onFailure { android.util.Log.e("Prefs", "落盘失败 $name：" + it.message) }
    } //原子落盘

    /** 临时文件换正文件：26 以后走原子 move，24/25 退化成删了再改名 */
    private fun replace(from: File, to: File): Boolean {
        if (Build.VERSION.SDK_INT >= 26) {
            val ok = runCatching {
                Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            }.isSuccess
            if (ok) return true
        }
        runCatching { to.delete() }
        return from.renameTo(to)
    } //原子替换

    /** 把同名 SharedPreferences 的全量导进来：老库搬家与老备份导入共用，新文件存在时也强制覆盖 */
    fun importFromSp() {
        runCatching {
            val sp = ctx.getSharedPreferences(name, Context.MODE_PRIVATE)
            val old = sp.all
            if (old.isEmpty()) return@runCatching
            synchronized(lock) {
                map.clear()
                old.forEach { (k, v) -> if (v != null) map[k] = v }
            }
            write()
            android.util.Log.w("Prefs", "已从 SharedPreferences 导入 $name：${old.size} 个键")
        }.onFailure { android.util.Log.w("Prefs", "老库导入失败 $name：" + it.message) }
    } //从 SP 导入

    /** 值编码：带类型前缀，json 里只有字符串，读回来才知道是 1、"1" 还是 true */
    private fun enc(v: Any?): String = when (v) {
        is Boolean -> "b:$v"
        is Int -> "i:$v"
        is Long -> "l:$v"
        is Float -> "f:$v"
        is Set<*> -> "a:" + JSONArray(v.toList()).toString()
        else -> "s:" + (v as? String ?: v.toString())
    } //编码

    private fun dec(s: String): Any? {
        if (s.length < 2) return null
        val body = s.substring(2)
        return when (s[0]) {
            'b' -> body.toBoolean()
            'i' -> body.toIntOrNull()
            'l' -> body.toLongOrNull()
            'f' -> body.toFloatOrNull()
            'a' -> runCatching {
                val a = JSONArray(body)
                val set = HashSet<String>()
                for (i in 0 until a.length()) set.add(a.getString(i))
                set as Set<String>
            }.getOrNull()
            else -> body
        }
    } //解码

    companion object {
        /** 主配置库名：和原来的 SharedPreferences 同名，老库才能对上 */
        const val MAIN = "bai_cfg"
        private val libs = HashMap<String, Prefs>()

        /** 取一个库：一库一文件，name 就是文件名 */
        @JvmStatic
        fun get(ctx: Context, name: String = MAIN): Prefs = synchronized(libs) {
            libs.getOrPut(name) {
                val app = ctx.applicationContext ?: ctx
                Prefs(app, name)
            }
        } //取库

        /** 导入备份后要丢掉内存里的旧值，下次访问按新文件重读 */
        @JvmStatic
        fun reset() = synchronized(libs) { libs.clear() }

        /** 全部库立刻落盘：切后台 / 进程退出前调 */
        @JvmStatic
        fun flushAll() {
            val all = synchronized(libs) { libs.values.toList() }
            all.forEach { runCatching { it.flush() } }
        } //全量落盘
    }
} //配置库
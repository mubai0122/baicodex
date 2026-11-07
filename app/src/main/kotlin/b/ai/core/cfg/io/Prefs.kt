package b.ai.core.cfg.io

import android.content.Context
import android.os.Build
import b.ai.BuildConfig
import b.ai.core.data.io.Layout
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 配置库：替掉 SharedPreferences，文件放在私有目录 data/prefs 下。
 *
 * 为什么换：SP 的位置系统写死成 /data/user/0/<包名>/shared_prefs 下的 xml，想归到 data/ 动不了；
 * 而且每次 apply() 都整份 XML 序列化 + 落盘，几百个键改一个开关也是全量写。
 *
 * 现在一个库拆成 8 个分片（data/prefs/<库名>/0..7.json），键按 hashCode 固定落片：
 * 改一个键只重写那一片，另外七片不动。每片都是 AES-256-GCM 密文，
 * 密钥由 local.properties 的 baicodex.backup.key 派生（跟加密库那把四段种子分开：
 * 那把绑本机硬件，配置库必须保证冷启动一定读得出来）。
 *
 * 改动只进内存，250ms 内的连续改动合并成一次落盘（写 tmp 再原子替换），切后台强制刷一次。
 * 老数据不丢：第一次打开时若是老的单文件 data/prefs/<库名>.json 就整份迁进分片，
 * 再没有就从同名 SharedPreferences 全量导一遍，老 xml 原样留着。
 */
class Prefs internal constructor(private val ctx: Context, val name: String) {
    companion object {
        /** 主配置库名：和原来的 SharedPreferences 同名，老库才能对上 */
        const val MAIN = "bai_cfg"

        /** 分片数：键按 hashCode 固定落片。改这个数等于换存储格式，必须同时写迁移 */
        const val SHARDS = 8

        private const val MAGIC = "bcxprefs"
        private const val VER = 1
        private const val NONCE = 12
        private const val TAG_BITS = 128
        /** 迁移完成标记：分片是逐片写的，写一半被杀时靠它重来一遍 */
        private const val DONE = ".done"
        private val libs = HashMap<String, Prefs>()

        /** 取一个库：一库一个目录，name 就是目录名 */
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

        /** 全部库立刻落盘：切后台 / 进程退出 / 导出备份前调 */
        @JvmStatic
        fun flushAll() {
            val all = synchronized(libs) { libs.values.toList() }
            all.forEach { runCatching { it.flush() } }
        } //全量落盘
    }

    private val dir = File(Layout.dir(ctx, Layout.PREFS), name)
    private val map = HashMap<String, Any>()
    /** 已经读进内存的分片号：没读过的分片在第一次访问它的键时才解密 */
    private val loaded = HashSet<Int>()
    /** 有改动的分片号：落盘只重写这些 */
    private val dirty = HashSet<Int>()
    private val lock = Any()
    private val queued = AtomicBoolean(false)
    private val io = Executors.newSingleThreadExecutor { r ->
        Thread(r, "prefs-io").apply { isDaemon = true }
    }
    /** 落盘密钥：从备份密钥派生，一份软件密钥，跟本机硬件无关 */
    private val key: ByteArray by lazy {
        MessageDigest.getInstance("SHA-256").digest(("baicodex-prefs|" + BuildConfig.BAI_BK_KEY).toByteArray())
    }

    init {
        migrate()
    }

    /** 第一次进来：没有完成标记时按「老单文件 → 老 SharedPreferences」的顺序搬一次，搬完落标记 */
    private fun migrate() {
        synchronized(lock) {
            if (loaded.isNotEmpty()) return
            //认标记文件而不是「有分片」：分片逐片写，写一半被杀时下次还能重来，否则剩下几片的键静默丢光
            if (File(dir, DONE).isFile) return
            //上一版留下的单文件整库 json
            val legacy = File(dir.parentFile, "$name.json")
            if (legacy.isFile) {
                runCatching {
                    val o = JSONObject(legacy.readText())
                    o.keys().forEach { k -> dec(o.getString(k))?.let { v -> map[k] = v } }
                    loaded.addAll(0 until SHARDS)
                    dirty.addAll(0 until SHARDS)
                    writeDirty()
                    legacy.delete()
                    markDone()
                }.onFailure { android.util.Log.w("Prefs", "老 json 迁不动 $name：" + it.message) }
                android.util.Log.w("Prefs", "$name 已从老单文件迁到分片：${map.size} 个键")
                return
            }
            //再往前：同名 SharedPreferences
            importFromSp()
            markDone()
        }
    } //首次迁移

    private fun markDone() = runCatching { File(dir, DONE).writeText(VER.toString()) }

    private fun shardOf(k: String): Int = Math.floorMod(k.hashCode(), SHARDS)

    private fun shardFile(s: Int): File = File(dir, "$s.json")

    private fun ensure(s: Int) {
        if (s in loaded) return
        loaded.add(s)
        val f = shardFile(s)
        if (!f.isFile) return
        val o = runCatching { unseal(f) }.getOrNull()
        if (o == null) {
            android.util.Log.w("Prefs", "分片读不出 ${f.name}（密钥换过或文件损坏）")
            return
        }
        o.keys().forEach { k -> dec(o.getString(k))?.let { v -> map[k] = v } }
    } //按需读分片

    fun getString(key: String, def: String?): String? = synchronized(lock) {
        ensure(shardOf(key))
        when (val v = map[key]) {
            is String -> v
            null -> def
            else -> v.toString()
        }
    }

    fun getInt(key: String, def: Int): Int = synchronized(lock) {
        ensure(shardOf(key))
        (map[key] as? Number)?.toInt() ?: def
    }

    fun getLong(key: String, def: Long): Long = synchronized(lock) {
        ensure(shardOf(key))
        (map[key] as? Number)?.toLong() ?: def
    }

    fun getFloat(key: String, def: Float): Float = synchronized(lock) {
        ensure(shardOf(key))
        (map[key] as? Number)?.toFloat() ?: def
    }

    fun getBoolean(key: String, def: Boolean): Boolean = synchronized(lock) {
        ensure(shardOf(key))
        map[key] as? Boolean ?: def
    }

    fun getStringSet(key: String, def: Set<String>?): Set<String>? = synchronized(lock) {
        ensure(shardOf(key))
        @Suppress("UNCHECKED_CAST")
        map[key] as? Set<String> ?: def
    }

    /** 全量快照：迁移与备份要遍历，返回副本不给外面改内存表 */
    fun all(): Map<String, Any?> = synchronized(lock) {
        (0 until SHARDS).forEach { ensure(it) }
        HashMap(map)
    }

    fun contains(key: String): Boolean = synchronized(lock) {
        ensure(shardOf(key))
        map.containsKey(key)
    }

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
            writeDirty()
            return true
        }

        private fun merge() = synchronized(lock) {
            if (clr) {
                map.clear()
                loaded.addAll(0 until SHARDS)
                dirty.addAll(0 until SHARDS)
            }
            rms.forEach {
                val s = shardOf(it)
                ensure(s)
                map.remove(it)
                dirty.add(s)
            }
            puts.forEach { (k, v) ->
                val s = shardOf(k)
                ensure(s)
                if (v == null) map.remove(k) else map[k] = v
                dirty.add(s)
            }
        }
    }

    /** 把内存表里改过的分片立刻写盘：切后台、退出、备份前调 */
    fun flush() {
        writeDirty()
    }

    private fun schedule() {
        if (!queued.compareAndSet(false, true)) return
        io.execute {
            runCatching { Thread.sleep(250) }
            queued.set(false)
            writeDirty()
        }
    } //合并落盘

    /** 只重写 dirty 里的分片：一片几 KB，改一个开关不再全量落盘 */
    private fun writeDirty() = synchronized(lock) {
        if (dirty.isEmpty()) return
        val shards = HashSet(dirty)
        dirty.clear()
        shards.forEach { s ->
            runCatching {
                val o = JSONObject()
                map.forEach { (k, v) -> if (shardOf(k) == s) o.put(k, enc(v)) }
                seal(s, o)
            }.onFailure { android.util.Log.e("Prefs", "落盘失败 $name/$s：" + it.message) }
        }
    } //按分片落盘

    private fun seal(s: Int, o: JSONObject) {
        dir.mkdirs()
        val target = shardFile(s)
        val tmp = File(dir, "$s.tmp")
        val nonce = ByteArray(NONCE).also { SecureRandom().nextBytes(it) }
        val head = MAGIC.toByteArray(Charsets.US_ASCII) + byteArrayOf(VER.toByte())
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        c.updateAAD(head)
        tmp.outputStream().use { out ->
            out.write(head)
            out.write(nonce)
            out.write(c.doFinal(o.toString().toByteArray()))
        }
        if (!replace(tmp, target)) throw IllegalStateException("替换写不动")
    } //加密写分片

    private fun unseal(f: File): JSONObject? {
        val raw = f.readBytes()
        val skip = MAGIC.length + 1 + NONCE
        if (raw.size <= skip) return null
        if (String(raw, 0, MAGIC.length, Charsets.US_ASCII) != MAGIC) return null
        if (raw[MAGIC.length].toInt() != VER) return null
        val head = raw.copyOfRange(0, MAGIC.length + 1)
        val nonce = raw.copyOfRange(MAGIC.length + 1, skip)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        c.updateAAD(head)
        return JSONObject(String(c.doFinal(raw, skip, raw.size - skip), Charsets.UTF_8))
    } //解密读分片

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

    /** 把同名 SharedPreferences 的全量导进来：老库搬家与老备份导入共用，导完立即按分片落盘 */
    fun importFromSp() {
        runCatching {
            val sp = ctx.getSharedPreferences(name, Context.MODE_PRIVATE)
            val old = sp.all
            if (old.isEmpty()) return@runCatching
            synchronized(lock) {
                map.clear()
                old.forEach { (k, v) -> if (v != null) map[k] = v }
                loaded.addAll(0 until SHARDS)
                dirty.addAll(0 until SHARDS)
            }
            writeDirty()
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
} //配置库
package b.ai.native.security
import b.ai.core.data.io.Layout
import b.ai.net.api.llm.text

import b.ai.App
import org.json.JSONObject
import java.io.File

/**
 * 凭据库：按类别分文件存在私有目录 files/configs/，每个文件都是 AES-256-GCM 密文。
 * 密钥派生与加解密都在 C 层（runtime/secret.cpp），解密结果只活在内存，不回写任何明文文件。
 */
object Vault {
    /** 密文所在目录：私有目录 data/configs，跟别的数据一起分块，不再平铺在 files/ 根下 */
    const val DIR = b.ai.core.data.io.Layout.CFG
    /** 老安装的密文目录：迁移没跑到（进程被杀）时仍要能读，读到顺手搬过去 */
    private const val OLD = "configs"
    /** 模型 / 语音 / 搜索 / A2A 这些调用凭据 */
    const val API = "apikeys.baicodex"
    /** 账号登录态与反馈用的 token */
    const val USER = "usertokens.baicodex"
    /** 技能包里 $secret.xxx 引用的密钥 */
    const val SECRET = "secrets.baicodex"
    /** 位置地名缓存：坐标转出来的地址串，同样只以密文落盘 */
    const val PLACE = "places.baicodex"
    /** SSH 与 GitHub 凭据：主机 / 端口 / 用户 / 密码 / token，单独一个文件 */
    const val SSH = "ssh.baicodex"
    /** 数据输出脱敏的关键词表：服务端下发，本地只做缓存，不算凭据但一并不外露 */
    const val MASK = "maskrules.baicodex"
    /** 摘要正文：对话被压缩掉的那段历史，正文只以密文落盘，DB 里只留覆盖范围标记 */
    const val SUM = "sums.baicodex"
    /** 全部桶：备份与指纹对账都按这张表遍历 */
    val buckets = listOf(API, USER, SECRET, PLACE, SSH, MASK, SUM)

    /** 文件名 -> 该文件的全量明文，只在进程内存里存在 */
    private val cache = HashMap<String, HashMap<String, String>>()
    private val loaded = HashSet<String>()

    /** 文件在、却解不开（种子换过）：这轮不给写，免得把还能救回来的密文冲掉 */
    private val locked = HashSet<String>()

    private val ok: Boolean by lazy { runCatching { System.loadLibrary("baicodex") }.isSuccess }

    private fun path(f: String): String {
        val d = File(File(App.self.filesDir, DIR), f)
        //老路径还有这份密文、新路径没有：先搬过来再用，避免升级后一次性读空当成全新安装
        if (!d.isFile) {
            val old = File(File(App.self.filesDir, OLD), f)
            if (old.isFile) runCatching {
                d.parentFile?.mkdirs()
                if (!old.renameTo(d)) old.copyTo(d, overwrite = false)
            }
        }
        return d.absolutePath
    } //密文绝对路径

    fun get(key: String, file: String = API): String {
        ensure(file)
        return cache[file]?.get(key).orEmpty()
    } //读凭据

    /**
     * 上一次凭据落盘失败的原因（空串 = 成功）。
     * 以前这里全程静默：桶被锁（种子换过）或 native 落盘失败时 put 直接丢弃，
     * 界面上"保存成功"，重启才发现 Key 没了。设置页现在会把这句显示出来。
     */
    var lastError: String = ""
        private set

    /** 这个桶当前是否被锁：密文解不开（换过种子 / 文件损坏）时不给写，保住还能救回的那份 */
    fun locked(file: String = API): Boolean {
        ensure(file)
        return file in locked
    } //是否被锁

    /**
     * 写凭据。
     * @return true = 真的写进去了；false = 落盘失败（原因见 lastError）
     *
     * 桶被锁（旧密文解不开）时不再一拒了之：调用方明确要写，说明那份旧密文用户已经用不上，
     * 先把旧文件归档成 `*.stale-<时间戳>` 留档，再按当前密钥重建，写成功并在 lastError 里
     * 说明"旧库已归档、原 Key 需重填"。
     */
    fun put(key: String, value: String, file: String = API): Boolean {
        ensure(file)
        val note = if (file in locked) archive(file) else ""
        val m = cache.getOrPut(file) { HashMap() }
        if (value.isEmpty()) m.remove(key) else m[key] = value
        val wrote = flush(file)
        if (wrote && note.isNotEmpty()) lastError = note
        return wrote
    } //写凭据

    /** 把解不开的旧密文挪成 `.stale-时间戳` 留档，然后按全新桶初始化 */
    private fun archive(file: String): String {
        val f = File(path(file))
        val bak = File(f.parentFile, "${f.name}.stale-${System.currentTimeMillis() / 1000}")
        runCatching {
            if (f.isFile) {
                if (!f.renameTo(bak)) {
                    f.copyTo(bak, overwrite = true)
                    f.delete()
                }
            }
        }
        cache.remove(file)
        locked.remove(file)
        loaded.remove(file)
        ensure(file) //旧文件已挪走，按"文件不存在"重新初始化，这次一定可写
        return "旧凭据库与当前密钥对不上，已归档为 ${bak.name}，请重新填一次 API Key（旧密文保留在 files/data/configs/ 里可随时找回）"
    } //归档并重建

    fun snapshot(file: String = API): Map<String, String> {
        ensure(file)
        return HashMap(cache[file].orEmpty())
    } //全量快照

    /** 一次性搬入历史明文，返回真正写进去的条数 */
    fun adopt(legacy: Map<String, String>, file: String = API): Int {
        ensure(file)
        if (file in locked) return 0
        val m = cache.getOrPut(file) { HashMap() }
        var n = 0
        legacy.forEach { (k, v) ->
            if (v.isNotBlank() && m[k] != v) {
                m[k] = v
                n++
            }
        }
        if (n > 0) flush(file)
        return n
    } //搬入历史明文

    /** 这个桶能不能写：解不开的密文不给覆盖，用户换回正确种子还能救 */
    fun writable(file: String = API): Boolean {
        ensure(file)
        return file !in locked
    } //是否可写

    /** 当前编译进去的密钥指纹：换过 local.properties 里的种子它就会变，文件解不开时靠它对账 */
    fun fingerprint(): String = if (ok) runCatching { nativeFingerprint() }.getOrDefault("") else ""

    /**
     * 导入备份换过密文后调用：把内存里的桶全部作废，下次访问按新文件重新解密。
     * 必须在文件已经换好之后调用，否则会读到旧密文。
     */
    @Synchronized
    fun reload() {
        cache.clear()
        loaded.clear()
        locked.clear()
    } //重置内存桶

    @Synchronized
    private fun ensure(file: String) {
        if (!loaded.add(file)) return
        if (!ok) {
            locked.add(file)
            return
        }
        val raw = runCatching { nativeLoad(path(file)) }.getOrDefault("")
        if (raw.isBlank()) {
            //文件在却解不开 = 种子变了；文件不在 = 全新安装，照常可写
            if (File(path(file)).isFile) locked.add(file)
            return
        }
        val o = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val m = cache.getOrPut(file) { HashMap() }
        o.keys().forEach { k ->
            val v = o.optString(k)
            if (v.isNotEmpty()) m[k] = v
        }
    } //首次访问时解密进内存

    @Synchronized
    private fun flush(file: String): Boolean {
        if (!ok) {
            lastError = "加密库没加载起来（libbaicodex），凭据没落盘"
            return false
        }
        if (file in locked) return false
        runCatching { File(App.self.filesDir, DIR).mkdirs() }
        val o = JSONObject()
        cache[file].orEmpty().forEach { (k, v) -> if (v.isNotEmpty()) o.put(k, v) }
        val okSave = runCatching { nativeSave(path(file), o.toString()) }.getOrDefault(false)
        lastError = if (okSave) "" else "凭据没写进 ${path(file)}（目录不可写或加密失败）"
        return okSave
    } //整体加密落盘

    /** 逐桶解进内存：迁移阶段先摸底，解不开的桶进 locked，后面不会被覆盖 */
    @Synchronized
    fun loadAll(): Int {
        var n = 0
        buckets.forEach { f ->
            ensure(f)
            if (f in loaded) n++
        }
        return n
    } //全部解密进内存

    /** 逐桶按当前密钥写回：调用前 native 那侧必须已经 unlock，否则写出来的是解不开的密文 */
    @Synchronized
    fun flushAll(): Int {
        var n = 0
        buckets.forEach { f ->
            if (f !in loaded || f in locked) return@forEach
            if (cache[f].isNullOrEmpty() && !File(path(f)).isFile) return@forEach
            flush(f)
            n++
        }
        return n
    } //全部重新加密落盘

    /** 把全部桶按当前密钥重写一遍：开启数据超强加密后旧 v1 密文在这里变成 v2 */
    @Synchronized
    fun rewriteAll(): Int {
        loadAll()
        return flushAll()
    } //全量重写

    /** 强制清空：密文文件全删，内存缓存与锁定状态一起清掉，不给旧值留回写机会 */
    @Synchronized
    fun wipeAll() {
        buckets.forEach { f ->
            runCatching { File(path(f)).delete() }
            cache.remove(f)
            loaded.remove(f)
            locked.remove(f)
        }
    } //清空全部凭据

    private external fun nativeSave(path: String, text: String): Boolean
    private external fun nativeLoad(path: String): String
    private external fun nativeFingerprint(): String
} //凭据库
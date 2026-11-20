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
 */

package b.ai.native.security
import b.ai.core.data.io.Layout
import b.ai.net.api.llm.text

import b.ai.App
import org.json.JSONObject
import java.io.File

object Vault {
    const val DIR = b.ai.core.data.io.Layout.CFG
    private const val OLD = "configs"
    const val API = "apikeys.baicodex"
    const val USER = "usertokens.baicodex"
    const val SECRET = "secrets.baicodex"
    const val PLACE = "places.baicodex"
    const val SSH = "ssh.baicodex"
    const val MASK = "maskrules.baicodex"
    const val SUM = "sums.baicodex"
    val buckets = listOf(API, USER, SECRET, PLACE, SSH, MASK, SUM)
    private val cache = HashMap<String, HashMap<String, String>>()
    private val loaded = HashSet<String>()
    private val locked = HashSet<String>()
    private val pending = HashSet<String>()
    private val ok: Boolean by lazy { runCatching { System.loadLibrary("baicodex") }.isSuccess }
    private fun path(f: String): String {
        val d = File(File(App.self.filesDir, DIR), f)
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

    var lastError: String = ""
        private set

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
        //「没解锁」先当成偶发故障重试一次：Keystore 抽风过去就好了，别直接把用户挡在门外
        if (file in pending && b.ai.native.security.Guard.retry()) {
            pending.remove(file)
            loaded.remove(file)
        }
        ensure(file)
        //还没解锁就先写 = 把解不开的 v2 密文直接覆盖掉，这里硬拦
        if (file in pending) {
            lastError = "数据保障这次没解锁成功，凭据没法读也没法写：请到 设置-隐私与安全 看数据保障状态，解锁后再填"
            return false
        }
        var note = ""
        if (file in locked) {
            note = archive(file)
            //设备密钥已丢失或换了机器：再留着数据保障开关，只会让每个桶都卡在「解不开也写不进」，
            //这里顺手关掉，让后续读写都回到可用状态（旧密文已归档，需要时能找回）
            if (b.ai.native.security.Guard.on && b.ai.native.security.Guard.unrecoverable()) {
                b.ai.native.security.Guard.reset()
                note += "；数据保障已自动关闭（本机已取不到设备密钥），可在设置里重新开启"
            }
        }
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
        pending.remove(file)
        ensure(file) //旧文件已挪走，按"文件不存在"重新初始化，这次一定可写
        return "旧凭据库与当前密钥对不上，已归档为 ${bak.name}，请重新填一次 API Key（旧密文保留在 files/data/configs/ 里可随时找回）"
    } //归档并重建

    fun snapshot(file: String = API): Map<String, String> {
        ensure(file)
        return HashMap(cache[file].orEmpty())
    } //全量快照

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

    fun writable(file: String = API): Boolean {
        ensure(file)
        return file !in locked && file !in pending
    } //是否可写

    fun fingerprint(): String = if (ok) runCatching { nativeFingerprint() }.getOrDefault("") else ""

    @Synchronized
    fun reload() {
        cache.clear()
        loaded.clear()
        locked.clear()
        pending.clear()
    } //重置内存桶

    @Synchronized
    private fun ensure(file: String) {
        if (!loaded.add(file)) return
        if (!ok) {
            locked.add(file)
            return
        }
        val p = path(file)
        var raw = runCatching { nativeLoad(p) }.getOrDefault("")
        //文件在、解出来却是空：先分清「这次没解锁」和「确实解不开」，两者后果完全不同
        if (raw.isBlank() && File(p).isFile) {
            val notUnlocked = b.ai.native.security.Guard.on && !b.ai.native.security.Guard.ready
            if (notUnlocked) {
                //Keystore 偶发失败：当场重试一次解锁，成功就继续按正常流程解密
                if (b.ai.native.security.Guard.retry()) raw = runCatching { nativeLoad(p) }.getOrDefault("")
                if (raw.isBlank()) {
                    if (b.ai.native.security.Guard.unrecoverable()) {
                        //设备密钥永久丢失 / 换了机器：标 locked，写入时走归档重建并关掉数据保障
                        locked.add(file)
                    } else {
                        pending.add(file)
                        loaded.remove(file) //下次访问再试一次解密
                    }
                    return
                }
            } else {
                //解锁正常却解不开 = 密文与当前密钥对不上（老版本密钥变更、文件被换过）
                locked.add(file)
                return
            }
        }
        if (raw.isBlank()) return
        pending.remove(file)
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
        if (file in pending) {
            lastError = "数据保障还没解锁，凭据没落盘"
            return false
        }
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

    @Synchronized
    fun rewriteAll(): Int {
        loadAll()
        return flushAll()
    } //全量重写

    @Synchronized
    fun wipeAll() {
        buckets.forEach { f ->
            runCatching { File(path(f)).delete() }
            cache.remove(f)
            loaded.remove(f)
            locked.remove(f)
            pending.remove(f)
        }
    } //清空全部凭据

    private external fun nativeSave(path: String, text: String): Boolean
    private external fun nativeLoad(path: String): String
    private external fun nativeFingerprint(): String
} //凭据库
/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:14
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package b.ai.native.miot
import android.util.Log

/**
 * 米家局域网传输薄壳：UDP 广播探测与单播对话在 C++（core/feature/miot/udp），
 * 报文头与 JSON 取数在 Rust（core::feature::miot），这里只做字符串搬运与异常兜底。
 */
object MiLanNative {
    private const val TAG = "bai.mi.native"
    private val ok: Boolean by lazy { runCatching { System.loadLibrary("baicodex") }.isSuccess }

    private external fun nativeDiscover(timeoutMs: Int): String
    private external fun nativeCall(ip: String, did: Long, tokenHex: String, stamp: Int, json: String, timeoutMs: Int): String?
    private external fun nativeAlive(ip: String, did: Long, timeoutMs: Int): Long

    /** 广播探测：一行一台「did|ip|stamp」；native 没加载或没探到都返回空表 */
    fun discover(timeoutMs: Int): List<String> {
        if (!ok) {
            Log.w(TAG, "native 未加载，局域网探测不可用")
            return emptyList()
        }
        val raw = runCatching { nativeDiscover(timeoutMs) }.getOrDefault("")
        return if (raw.isEmpty()) emptyList() else raw.lines().filter { it.isNotEmpty() }
    } //广播探测

    /** 单播请求-应答：返回「设备时间戳 to 明文 JSON」，失败返回 null */
    fun call(ip: String, did: Long, tokenHex: String, stamp: Int, json: String, timeoutMs: Int): Pair<Long, String>? {
        if (!ok) return null
        val r = runCatching { nativeCall(ip, did, tokenHex, stamp, json, timeoutMs) }.getOrNull() ?: return null
        val at = r.indexOf('\n')
        if (at <= 0) return null
        val ts = r.substring(0, at).toLongOrNull() ?: return null
        return ts to r.substring(at + 1)
    } //单播请求-应答

    /** 探活：返回设备时间戳，0 表示没应答 */
    fun alive(ip: String, did: Long, timeoutMs: Int): Long {
        if (!ok) return 0L
        return runCatching { nativeAlive(ip, did, timeoutMs) }.getOrDefault(0L)
    } //探活
} //米家局域网传输
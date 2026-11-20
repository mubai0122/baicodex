/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package b.ai.native.archive

import java.io.File

/**
 * tar.bz2 选择性解包（native 实现在 cpp/core/feature/archive/tarbz2.cpp）。
 *
 * 官方语音模型基本都是 tar.bz2，而 Android 不带 bzip2 命令，所以把 bzip2 库静态编进 libbaicodex.so。
 * 解包只按 basename 挑需要的文件平铺写入目标目录，tar 里的目录结构不落地，路径穿越从设计上就不存在。
 */
object Bz2tar {

    /** native 库没加载起来的错误码（和 C 侧的错误码错开，不会撞） */
    const val ERR_NATIVE = -9

    private val ok: Boolean by lazy { runCatching { System.loadLibrary("baicodex") }.isSuccess }

    /** native 可用性：v7a / arm64 都编了，理论上都该是 true */
    fun available(): Boolean = ok

    /**
     * 从 tar.bz2 里挑出 names（按 basename 匹配）解到 dstDir。
     * @return >=0 实际写出的文件数；负数见 [describe]
     */
    fun extract(src: File, dstDir: File, names: Collection<String>): Int {
        if (!ok) return ERR_NATIVE
        if (!src.isFile) return -1
        if (names.isEmpty()) return -1
        dstDir.mkdirs()
        val list = names.toTypedArray()
        return runCatching {
            nativeExtract(src.absolutePath, dstDir.absolutePath.let { if (it.endsWith("/")) it else "$it/" }, list)
        }.getOrDefault(ERR_NATIVE)
    } //解包入口

    /** 错误码转人话，界面直接显示 */
    fun describe(code: Int): String = when (code) {
        0 -> "包是空的"
        -1 -> "打不开模型包"
        -2 -> "bz2 解压失败（包损坏或下载不全）"
        -3 -> "tar 结构不对（不是 tar.bz2）"
        -4 -> "写文件失败（存储空间不足？）"
        ERR_NATIVE -> "native 库不可用"
        else -> if (code < 0) "解压失败（$code）" else "解出 $code 个文件"
    } //错误码说明

    private external fun nativeExtract(src: String, dstDir: String, names: Array<String>): Int
} //tar.bz2 解包
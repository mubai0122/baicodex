package b.ai.core

import android.os.Environment
import b.ai.App
import java.io.File
import java.security.SecureRandom

/**
 * 全局数据根：所有可读可改的东西都落在外部存储的 baicode 目录。
 * 技能、角色、自定义提示是普通文件，用户和模型都能直接编辑；默认工作区是根下的哈希目录。
 */
object Paths {
    /** 根目录名，实际路径 = 外部存储根/baicode，任何机型都一致 */
    const val DIR = "baicode"

    private val rnd = SecureRandom()

    fun base(): File = File(Environment.getExternalStorageDirectory(), DIR)

    /** 确保根目录存在再返回，拿不到外部存储时退回应用私有目录 */
    fun ensure(): File {
        val f = base()
        if (f.isDirectory) return f
        runCatching { f.mkdirs() }
        return if (f.isDirectory) f else File(App.self.filesDir, DIR).also { runCatching { it.mkdirs() } }
    } //根目录

    fun file(name: String): File = File(ensure(), name)

    /** 新版本工作区根：baicode/workspace；旧版直接把哈希目录挂在 baicode 下面 */
    const val WSUB = "workspace"

    fun workspaceRoot(): File = File(ensure(), WSUB).also { runCatching { it.mkdirs() } }

    /** 4-4-4-4-4-4 的哈希目录名：24 位小写 hex 分六段 */
    fun hashName(): String {
        val b = ByteArray(12)
        rnd.nextBytes(b)
        val h = b.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        return (0 until 6).joinToString("-") { h.substring(it * 4, it * 4 + 4) }
    } //哈希目录名

    /** 新建一个哈希目录（碰撞就重摇），落新的 workspace 根下，返回绝对路径 */
    fun newDir(): File {
        val root = workspaceRoot()
        var f = File(root, hashName())
        var i = 0
        while (f.exists() && i++ < 8) f = File(root, hashName())
        runCatching { f.mkdirs() }
        return f
    } //新哈希目录

    /** 按名字取根下子目录，不存在就建 */
    fun dir(name: String): File = File(ensure(), name).also { runCatching { it.mkdirs() } }
} //全局数据根与哈希目录
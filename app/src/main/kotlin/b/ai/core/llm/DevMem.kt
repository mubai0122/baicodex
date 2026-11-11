package b.ai.core.llm

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/** 设备内存：总 RAM 与可用 RAM，用来在加载大模型前算占比给风险提示 */
object DevMem {
    data class Info(val total: Long, val avail: Long, val low: Boolean)

    fun info(ctx: Context): Info = runCatching {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        Info(mi.totalMem, mi.availMem, mi.lowMemory)
    }.getOrDefault(Info(0L, 0L, false)) //读不到就给 0，调用方按未知处理

    /** 模型体积占总内存的比例：GGUF 走 mmap，实际占用约等于文件体积加 KV 缓存 */
    fun ratio(ctx: Context, modelBytes: Long): Double {
        val t = info(ctx).total
        return if (t <= 0L || modelBytes <= 0L) 0.0 else modelBytes.toDouble() / t.toDouble()
    } //体积占比

    /** 风险等级：0 安全，1 提醒，2 严重（对应 60% 与 90% 两条线） */
    fun risk(ratio: Double): Int = when {
        ratio >= 0.9 -> 2
        ratio >= 0.6 -> 1
        else -> 0
    } //风险分级

    /** 设备概况：SoC 与 GPU 型号，用来在设置页显示跑什么后端 */
    fun soc(): String = runCatching {
        val model = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else ""
        val man = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MANUFACTURER else ""
        listOf(man, model).filter { it.isNotBlank() }.joinToString(" ").ifBlank { Build.HARDWARE }
    }.getOrDefault(Build.HARDWARE) //SoC 描述
} //设备内存与 SoC 信息
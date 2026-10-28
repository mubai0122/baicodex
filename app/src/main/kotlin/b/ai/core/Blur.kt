package b.ai.core

/** 背景模糊的计算层：优先走 native（-O3，纯整数滑动窗口），so 加载不到才退回纯 Kotlin，两边逐像素等价 */
object Blur {

    private val loaded: Boolean by lazy { runCatching { System.loadLibrary("baicodex") }.isSuccess }

    private external fun nativeBox(pix: IntArray, w: Int, h: Int, r: Int): Boolean

    /** 原地做两趟盒式模糊近似高斯；native 不可用或返回失败时用 Kotlin 版兜底 */
    fun box2(pix: IntArray, w: Int, h: Int, r: Int) {
        if (w < 2 || h < 2 || r <= 0) return
        if (loaded && runCatching { nativeBox(pix, w, h, r) }.getOrDefault(false)) return
        box(pix, w, h, r)
        box(pix, w, h, r)
    } //两趟模糊

    /** 原地做一次横向 + 纵向盒式模糊（native 兜底用，算法与 blur_jni.cpp 一致） */
    private fun box(pix: IntArray, w: Int, h: Int, r: Int) {
        val div = r * 2 + 1
        val tmp = IntArray(pix.size)
        //横向：滑窗求和，边界像素用夹取
        for (y in 0 until h) {
            val row = y * w
            var sa = 0; var sr = 0; var sg = 0; var sb = 0
            for (i in -r..r) {
                val c = pix[row + i.coerceIn(0, w - 1)]
                sa += c ushr 24 and 0xFF
                sr += c shr 16 and 0xFF
                sg += c shr 8 and 0xFF
                sb += c and 0xFF
            }
            for (x in 0 until w) {
                tmp[row + x] = (sa / div shl 24) or (sr / div shl 16) or (sg / div shl 8) or (sb / div)
                val add = pix[row + (x + r + 1).coerceAtMost(w - 1)]
                val sub = pix[row + (x - r).coerceAtLeast(0)]
                sa += (add ushr 24 and 0xFF) - (sub ushr 24 and 0xFF)
                sr += (add shr 16 and 0xFF) - (sub shr 16 and 0xFF)
                sg += (add shr 8 and 0xFF) - (sub shr 8 and 0xFF)
                sb += (add and 0xFF) - (sub and 0xFF)
            }
        }
        //纵向：同一套滑窗换列扫
        for (x in 0 until w) {
            var sa = 0; var sr = 0; var sg = 0; var sb = 0
            for (i in -r..r) {
                val c = tmp[i.coerceIn(0, h - 1) * w + x]
                sa += c ushr 24 and 0xFF
                sr += c shr 16 and 0xFF
                sg += c shr 8 and 0xFF
                sb += c and 0xFF
            }
            for (y in 0 until h) {
                pix[y * w + x] = (sa / div shl 24) or (sr / div shl 16) or (sg / div shl 8) or (sb / div)
                val add = tmp[(y + r + 1).coerceAtMost(h - 1) * w + x]
                val sub = tmp[(y - r).coerceAtLeast(0) * w + x]
                sa += (add ushr 24 and 0xFF) - (sub ushr 24 and 0xFF)
                sr += (add shr 16 and 0xFF) - (sub shr 16 and 0xFF)
                sg += (add shr 8 and 0xFF) - (sub shr 8 and 0xFF)
                sb += (add and 0xFF) - (sub and 0xFF)
            }
        }
    } //一次盒式模糊
} //背景模糊计算层

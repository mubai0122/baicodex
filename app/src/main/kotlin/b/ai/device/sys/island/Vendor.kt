/**
 * 厂商探测：按 ROM 归到具体的岛实现上。
 * 只做「谁的岛」的判断，退出码级差异全部留给各厂商文件。
 */
package b.ai.device.sys.island

import android.os.Build
import b.ai.device.sys.island.honor.HonorIsland
import b.ai.device.sys.island.huawei.HuaweiIsland
import b.ai.device.sys.island.mi.MiIsland
import b.ai.device.sys.island.oppo.OppoIsland
import b.ai.device.sys.island.plain.PlainIsland
import b.ai.device.sys.island.samsung.SamsungIsland
import b.ai.device.sys.island.vivo.VivoIsland

/** 按 ROM 粗分的厂商标识，label 用于设置页展示 */
enum class Rom(val label: String) {
    MI("小米 / 红米"),
    HUAWEI("华为"),
    HONOR("荣耀"),
    OPPO("OPPO / 一加"),
    VIVO("vivo / iQOO"),
    SAMSUNG("三星"),
    PLAIN("通用")
}

object Vendor {
    /** 缓存一次：Build 属性进程内不变，每轮任务都判一遍纯属浪费 */
    val rom: Rom by lazy { detect() }

    private fun detect(): Rom {
        val m = (Build.MANUFACTURER + " " + Build.BRAND + " " + Build.DISPLAY).lowercase()
        return when {
            m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") -> Rom.MI
            //honor 必须排在 huawei 之前判：荣耀机型的部分字段里仍带 huawei 字样
            m.contains("honor") -> Rom.HONOR
            m.contains("huawei") -> Rom.HUAWEI
            m.contains("oppo") || m.contains("oneplus") || m.contains("realme") -> Rom.OPPO
            m.contains("vivo") || m.contains("iqoo") -> Rom.VIVO
            m.contains("samsung") -> Rom.SAMSUNG
            else -> Rom.PLAIN
        }
    } //按厂商串归口

    /** 造出当前机型对应的岛实现；整套只造一次，门面里缓存 */
    fun create(): IslandImpl = when (rom) {
        Rom.MI -> MiIsland()
        Rom.HUAWEI -> HuaweiIsland()
        Rom.HONOR -> HonorIsland()
        Rom.OPPO -> OppoIsland()
        Rom.VIVO -> VivoIsland()
        Rom.SAMSUNG -> SamsungIsland()
        Rom.PLAIN -> PlainIsland()
    }

    /**
     * 全局共享的岛实现。
     * 通知本体由前台服务（IslandSvc）构建，门面与门面之外必须拿到同一个实例，
     * 否则通道配置 / 厂商字段会在两处各算一遍。
     */
    val shared: IslandImpl by lazy { create() }
} //厂商探测
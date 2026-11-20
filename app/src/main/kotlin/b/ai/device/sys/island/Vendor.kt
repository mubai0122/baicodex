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
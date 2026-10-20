package b.ai.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.BatteryManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 系统提示里的环境注入：时间 / 电量 / 位置，缺权限时如实写缺失，不编数据 */
object Env {
    /** 拼环境段；三个开关全关时返回空串，请求里就一个字都不多 */
    fun block(c: Context): String {
        if (!Cfg.injectTime && !Cfg.injectBattery && !Cfg.injectPlace) return ""
        val parts = ArrayList<String>(3)
        if (Cfg.injectTime) {
            parts.add("时间 " + SimpleDateFormat("yyyy-MM-dd HH:mm EEEE", Locale.CHINA).format(Date()))
        }
        if (Cfg.injectBattery) parts.add(battery(c))
        if (Cfg.injectPlace) parts.add(place(c))
        return "环境：" + parts.joinToString("；") + "（本机实时数据，够用就直接用，别反问用户）\n"
    } //环境段

    fun hasPlacePerm(c: Context): Boolean =
        c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun battery(c: Context): String {
        val bm = c.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            ?: return "电量 读不到"
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        return "电量 $pct%" + if (bm.isCharging) "（充电中）" else "（未充电）"
    } //电量与充电状态

    private fun place(c: Context): String {
        if (!hasPlacePerm(c)) return "位置 未授权（缺定位权限）"
        val lm = c.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return "位置 读不到"
        return runCatching {
            //三个来源里挑时间最新的一个，GPS 常常是空的，网络定位更稳
            val loc = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            ).mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
                .maxByOrNull { it.time }
            if (loc == null) "位置 暂无（还没定位过）"
            else "位置 " + String.format(Locale.US, "%.5f,%.5f", loc.latitude, loc.longitude) +
                "（精度约 ${loc.accuracy.toInt()} 米）"
        }.getOrDefault("位置 未授权（缺定位权限）")
    } //最后已知位置
} //环境注入

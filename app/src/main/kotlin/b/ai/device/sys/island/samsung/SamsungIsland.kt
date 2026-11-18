/**
 * 三星 One UI 适配。
 *
 * 现状：One UI 7 的 Now Bar / Live Updates 基于 Android 16 的 ProgressStyle 通知模板，
 * 本项目 targetSdk 34 拿不到该模板，因此走标准常驻通知降级。
 * 升级 targetSdk 后可改用 Notification.ProgressStyle。
 */
package b.ai.device.sys.island.samsung

import b.ai.device.sys.island.plain.PlainIsland

class SamsungIsland : PlainIsland() {
    override val channelId = "task_island_sam"
}
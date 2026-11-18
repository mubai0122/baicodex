/**
 * vivo OriginOS 原子通知 / 灵动岛适配。
 *
 * 现状：OriginOS 的原子通知只对白名单应用开放，三方通知在岛上的呈现由系统自行决定，
 * 没有可写入的公开 extras 键。这里走标准常驻通知降级。
 * 若拿到 OriginOS 接入文档，只需在 decorate() 里补字段。
 */
package b.ai.device.sys.island.vivo

import b.ai.device.sys.island.plain.PlainIsland

class VivoIsland : PlainIsland() {
    override val channelId = "task_island_vivo"
}
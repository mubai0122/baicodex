/**
 * 荣耀 MagicOS 适配（灵动胶囊 / 胶囊通知）。
 *
 * 现状：荣耀的胶囊通知没有面向三方应用公开的稳定 extras 协议，
 * 官方文档只给了 MagicOS 8 起「任意门 / 通知胶囊」的 UX 规范，字段名随版本变动。
 * 因此这里沿用标准常驻通知：MagicOS 会把它当作普通通知处理，不走胶囊。
 * 若后续拿到官方接入包，只需在 decorate() 里补 extras，其它代码不用动。
 */
package b.ai.device.sys.island.honor

import b.ai.device.sys.island.plain.PlainIsland

class HonorIsland : PlainIsland() {
    override val channelId = "task_island_honor"
}
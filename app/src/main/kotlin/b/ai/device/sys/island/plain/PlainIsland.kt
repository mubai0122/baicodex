/**
 * 降级实现：不认识岛的 ROM（或厂商未开放第三方接入）走这里。
 * 表现就是一条普通常驻通知——有通知栏，没岛。
 */
package b.ai.device.sys.island.plain

import b.ai.device.sys.island.BaseIsland

open class PlainIsland : BaseIsland()
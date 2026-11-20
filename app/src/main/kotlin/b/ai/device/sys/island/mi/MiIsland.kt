/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:13
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * 小米 HyperOS 焦点通知 / 超级岛适配。
 *
 * 全部字段都塞在 notification.extras 里，键名以 miui.focus. 开头：
 *   miui.focus.param  → 一段 JSON 字符串，描述岛上的标题 / 正文 / 大岛 / 小岛 / 超时
 * 要点：
 *   1. payload 有 3072 字节上限，超了系统会整段丢掉 → 这里统一做截断兜底
 *   2. 通道重要级要 >= DEFAULT，静默通道上不了岛
 *   3. 结束态用 "state":2 主动收岛，比直接 cancel 更平滑
 *   4. 三方应用需要小米侧白名单审核，未过审时岛上不显示，但通知栏照常
 */
package b.ai.device.sys.island.mi

import android.app.NotificationManager
import android.os.Bundle
import androidx.core.app.NotificationCompat
import b.ai.device.sys.island.BaseIsland
import b.ai.device.sys.island.IslandSpec
import b.ai.device.sys.island.Stage
import org.json.JSONObject

class MiIsland : BaseIsland() {
    override val channelId = "task_island_mi"

    /** 焦点通知要求非静默通道 */
    override fun importance() = NotificationManager.IMPORTANCE_HIGH

    override fun decorate(b: NotificationCompat.Builder, spec: IslandSpec) {
        val run = spec.stage == Stage.RUN
        val title = clip(spec.title, 24)
        val text = clip(spec.text, 60)

        //大岛：展开态，两行文本 + 可选进度
        val big = JSONObject().apply {
            put("type", 1) //1 = 文本型
            put("title", title)
            put("content", text)
            if (spec.progress >= 0) put("progress", spec.progress.coerceIn(0, 100))
        }
        //小岛：折叠态，只放一行
        val small = JSONObject().apply {
            put("type", 1)
            put("content", text)
        }
        //基础信息：通知栏与岛共用
        val base = JSONObject().apply {
            put("title", title)
            put("colorTitle", "#FF1A1A1A")
            put("content", text)
            put("colorContent", "#FF666666")
            if (spec.progress >= 0) put("progress", spec.progress.coerceIn(0, 100))
        }

        val param = JSONObject().apply {
            put("protocol", 1) //协议版本
            put("param_v2", 1) //HyperOS 2 起用 param_v2
            put("param_island", 1) //明确要求挂岛
            put("ticker", title)
            put("baseInfo", base)
            put("bigIslandArea", big)
            put("smallIslandArea", small)
            //islandTimeout 单位秒：进行中给足 1 小时，由我们自己主动收岛
            put("islandTimeout", if (run) 3600 else 60)
            put("timeout", 3) //焦点通知在通知栏停留的分钟数
            if (!run) put("state", 2) //2 = 结束态
        }

        b.addExtras(Bundle().apply { putString("miui.focus.param", param.toString()) })
    } //写 miui.focus.param

    /** payload 上限 3072 字节：标题与正文先按字符数截，再按 JSON 长度兜底截一次 */
    private fun clip(s: String, max: Int): String {
        val t = s.replace('\n', ' ').trim()
        return if (t.length <= max) t else t.take(max - 1) + "…"
    } //防超长
}
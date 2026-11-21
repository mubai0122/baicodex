/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:09
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package b.ai.core.pet.say
import b.ai.core.pet.balance.PetBalance
import b.ai.core.pet.peak.PeakHours
import b.ai.core.cfg.app.Cfg

import android.content.Context
import b.ai.R

/** 桌宠说词的统一出口：35% 概率说当前峰谷提示，其余从台词池里挑 */
object PetSay {

    fun next(c: Context): String {
        val off = PeakHours.offPeak()
        val timed = c.getString(if (off) R.string.pet_say_offpeak else R.string.pet_say_peak)
        if ((Math.random() * 100).toInt() < Cfg.petTimedPct) return timed
        val one = PetLines.pick()
        return one.ifBlank { timed }
    } //挑一句：时段提示或台词池

    /** 双击查余额：先报一声，查到说数字，查不到也回一句 */
    fun balance(c: Context, cb: (String) -> Unit) {
        if (Cfg.apiKey.isBlank()) {
            cb(c.getString(R.string.pet_no_key))
            return
        }
        cb(c.getString(R.string.pet_bal_wait))
        PetBalance.fetchAsync(Cfg.apiKey) { t ->
            cb(if (t.isBlank()) c.getString(R.string.pet_bal_fail) else c.getString(R.string.pet_bal_say, t))
        }
    } //查余额并说
} //桌宠说词
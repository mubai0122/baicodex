package b.ai.core

import android.content.Context
import b.ai.R

/** 桌宠说词的统一出口：35% 概率说当前峰谷提示，其余从台词池里挑 */
object PetSay {

    fun next(c: Context): String {
        val off = PetBalance.offPeak()
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

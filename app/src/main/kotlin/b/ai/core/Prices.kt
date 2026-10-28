package b.ai.core

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import org.json.JSONObject

/** 按模型定价库：key = usage 表里的 model 名，整表 JSON 存在 bai_cfg 的 prices 键 */
object Prices {
    private val cache = mutableStateOf<Map<String, Price>>(emptyMap())
    private var sp: SharedPreferences? = null

    val all: Map<String, Price> get() = cache.value

    fun init(c: Context) {
        if (sp != null) return
        val p = c.applicationContext.getSharedPreferences("bai_cfg", Context.MODE_PRIVATE)
        sp = p
        cache.value = runCatching { parse(p.getString("prices", "")) }.getOrDefault(emptyMap())
    } //读盘

    private fun parse(raw: String?): Map<String, Price> {
        if (raw.isNullOrBlank()) return emptyMap()
        val o = JSONObject(raw)
        val out = LinkedHashMap<String, Price>()
        o.keys().forEach { k ->
            val v = o.optJSONObject(k) ?: return@forEach
            out[k] = Price(
                v.optDouble("in", 0.0).toFloat(),
                v.optDouble("out", 0.0).toFloat(),
                v.optDouble("cache", 0.0).toFloat()
            )
        }
        return out
    } //从 JSON 还原

    private fun dump(m: Map<String, Price>): String {
        val o = JSONObject()
        m.forEach { (k, v) ->
            o.put(
                k,
                JSONObject()
                    .put("in", v.inP.toDouble())
                    .put("out", v.outP.toDouble())
                    .put("cache", v.cacheP.toDouble())
            )
        }
        return o.toString()
    } //写成 JSON

    /** 某模型的专属价；没配过返回 null */
    fun get(model: String): Price? = cache.value[model]

    /** 生效价：只认这个模型自己配的价，没配过就是全 0（不计价），不再有全局默认价 */
    fun of(model: String): Price = get(model) ?: Price()

    /** 有任意模型配过专属价 */
    val anyCustom: Boolean get() = cache.value.values.any { it.on }

    /** 保存某模型价格：三项全 0 等于取消专属价，直接删掉这条 */
    fun put(model: String, p: Price) {
        val m = LinkedHashMap(cache.value)
        if (p.on) m[model] = p else m.remove(model)
        cache.value = m
        sp?.edit()?.putString("prices", dump(m))?.apply()
    } //写一个模型

    fun remove(model: String) = put(model, Price())
} //按模型定价
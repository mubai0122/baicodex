package b.ai.core

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** 一条通知触发规则：某个应用来了带关键词的通知，就把 prompt 交给 Agent 跑一轮 */
data class NoteTrigger(
    val id: String,
    val app: String,
    val keyword: String,
    val prompt: String,
    val on: Boolean,
    val lastAt: Long
)

/** 通知触发规则库：整表 JSON 存 bai_cfg，同一条规则 60 秒内只触发一次，防连发通知刷爆对话 */
object Triggers {
    private const val GAP = 60_000L

    private val cache = mutableStateOf<List<NoteTrigger>>(emptyList())
    private var sp: SharedPreferences? = null

    val list: List<NoteTrigger> get() = cache.value

    fun init(c: Context) {
        if (sp != null) return
        val p = c.applicationContext.getSharedPreferences("bai_cfg", Context.MODE_PRIVATE)
        sp = p
        cache.value = runCatching { parse(p.getString("note_triggers", "")) }.getOrDefault(emptyList())
    } //读盘

    private fun parse(raw: String?): List<NoteTrigger> {
        if (raw.isNullOrBlank()) return emptyList()
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val o = arr.optJSONObject(i) ?: JSONObject()
            NoteTrigger(
                id = o.optString("id"),
                app = o.optString("app"),
                keyword = o.optString("keyword"),
                prompt = o.optString("prompt"),
                on = o.optBoolean("on", true),
                lastAt = o.optLong("lastAt", 0L)
            )
        }.filter { it.id.isNotBlank() && it.prompt.isNotBlank() }
    } //从 JSON 还原

    private fun save() {
        val arr = JSONArray()
        cache.value.forEach { t ->
            arr.put(
                JSONObject()
                    .put("id", t.id).put("app", t.app).put("keyword", t.keyword)
                    .put("prompt", t.prompt).put("on", t.on).put("lastAt", t.lastAt)
            )
        }
        sp?.edit()?.putString("note_triggers", arr.toString())?.apply()
    } //整表落盘

    fun add(app: String, keyword: String, prompt: String): NoteTrigger {
        val t = NoteTrigger(
            id = UUID.randomUUID().toString(),
            app = app.trim(),
            keyword = keyword.trim(),
            prompt = prompt.trim(),
            on = true,
            lastAt = 0L
        )
        cache.value = cache.value + t
        save()
        return t
    } //新增

    fun remove(id: String) {
        cache.value = cache.value.filterNot { it.id == id }
        save()
    } //删除

    fun toggle(id: String, on: Boolean) {
        cache.value = cache.value.map { if (it.id == id) it.copy(on = on) else it }
        save()
    } //开关

    /** 命中规则：应用可以按包名或显示名匹配，关键词留空表示该应用任何通知都触发 */
    fun match(pkg: String, app: String, title: String, now: Long = System.currentTimeMillis()): NoteTrigger? {
        val hit = cache.value.firstOrNull { t ->
            t.on && t.prompt.isNotBlank() &&
                (t.app.isBlank() || t.app.equals(pkg, true) || app.contains(t.app, true)) &&
                (t.keyword.isBlank() || title.contains(t.keyword, true))
        } ?: return null
        if (now - hit.lastAt < GAP) return null
        cache.value = cache.value.map { if (it.id == hit.id) it.copy(lastAt = now) else it }
        save()
        return hit
    } //命中判定
} //通知触发
package b.ai.core

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** 一个 A2A 对端：名字给人看，url 是它的基地址（不带 /.well-known/agent-card.json） */
data class A2aNode(
    val id: String,
    val name: String,
    val url: String,
    val token: String = "",
    val on: Boolean = true
)

/** A2A 对端库：整表 JSON 存在 bai_cfg 的 a2a_nodes 键 */
object A2aNodes {
    private val cache = mutableStateOf<List<A2aNode>>(emptyList())
    private var sp: SharedPreferences? = null

    val list: List<A2aNode> get() = cache.value

    fun init(c: Context) {
        if (sp != null) return
        val p = c.applicationContext.getSharedPreferences("bai_cfg", Context.MODE_PRIVATE)
        sp = p
        cache.value = runCatching { parse(p.getString("a2a_nodes", "")) }.getOrDefault(emptyList())
    } //读盘

    private fun parse(raw: String?): List<A2aNode> {
        if (raw.isNullOrBlank()) return emptyList()
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val o = arr.optJSONObject(i) ?: JSONObject()
            A2aNode(
                id = o.optString("id"),
                name = o.optString("name"),
                url = o.optString("url"),
                token = o.optString("token"),
                on = o.optBoolean("on", true)
            )
        }.filter { it.id.isNotBlank() && it.url.isNotBlank() }
    } //从 JSON 还原

    private fun save() {
        val arr = JSONArray()
        cache.value.forEach { n ->
            arr.put(
                JSONObject().put("id", n.id).put("name", n.name).put("url", n.url)
                    .put("token", n.token).put("on", n.on)
            )
        }
        sp?.edit()?.putString("a2a_nodes", arr.toString())?.apply()
    } //整表落盘

    fun byId(id: String): A2aNode? = cache.value.firstOrNull { it.id == id }

    /** 按 id / 名字 / URL 模糊找对端：模型给的 node 参数三种写法都可能 */
    fun find(key: String): A2aNode? {
        val k = norm(key)
        if (k.isBlank()) return null
        return cache.value.firstOrNull { it.id == key }
            ?: cache.value.firstOrNull { it.name.equals(key.trim(), true) }
            ?: cache.value.firstOrNull { norm(it.url).equals(k, true) }
            ?: cache.value.firstOrNull { norm(it.url).contains(k, true) || k.contains(norm(it.url), true) }
    } //按三种写法查

    /** URL 规范化：去空白、去尾部斜杠，避免同一个对端被存成两条 */
    fun norm(url: String): String = url.trim().trimEnd('/')

    fun add(name: String, url: String, token: String): A2aNode {
        val n = A2aNode(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { norm(url) },
            url = norm(url),
            token = token.trim(),
            on = true
        )
        cache.value = cache.value + n
        save()
        return n
    } //新增

    fun update(n: A2aNode) {
        cache.value = cache.value.map { if (it.id == n.id) n.copy(url = norm(n.url)) else it }
        save()
    } //覆盖

    fun remove(id: String) {
        cache.value = cache.value.filterNot { it.id == id }
        save()
    } //删除
} //A2A 对端库
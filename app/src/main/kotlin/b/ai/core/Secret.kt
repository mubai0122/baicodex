package b.ai.core

import org.json.JSONObject

/**
 * 技能包里 $secret.xxx 引用的密钥表。
 * 明文存数据根 secrets.json（用户自己填），包文件里永远不写密钥。
 */
object Secret {
    const val FILE = "secrets.json"

    /** 读全表：文件不在或坏了就当空 */
    fun all(): Map<String, String> = runCatching {
        val raw = Store.read(FILE) ?: return emptyMap()
        val o = JSONObject(raw)
        val out = LinkedHashMap<String, String>()
        o.keys().forEach { k ->
            val v = o.optString(k)
            if (v.isNotEmpty()) out[k] = v
        }
        out
    }.getOrDefault(emptyMap()) //全部密钥

    fun get(name: String): String = all()[name].orEmpty()

    /** 写入 / 覆盖一个密钥，空值等于删除 */
    fun put(name: String, value: String) {
        val m = LinkedHashMap(all())
        if (value.isEmpty()) m.remove(name) else m[name] = value
        val o = JSONObject()
        m.forEach { (k, v) -> o.put(k, v) }
        Store.write(FILE, o.toString(2))
    } //设置密钥

    /** 名称合法性与 skill.json 里 $secret.xxx 的写法一致 */
    fun validName(name: String): Boolean = name.isNotEmpty() && name[0].let { !it.isDigit() } &&
        name.all { it.isLetterOrDigit() || it == '_' } //合法密钥名
} //密钥表
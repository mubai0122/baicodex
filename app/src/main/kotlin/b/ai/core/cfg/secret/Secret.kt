package b.ai.core.cfg.secret
import b.ai.core.cfg.io.Store
import b.ai.native.security.Vault

import org.json.JSONObject

/**
 * 技能包里 $secret.xxx 引用的密钥表。
 * 单独一个加密文件（files/configs/secrets.baicodex），解密只在内存，包文件里永远不写密钥。
 */
object Secret {
    /** 老版本的明文文件：升级后第一次启动搬空就删掉 */
    const val FILE = "secrets.json"

    /** 读全表 */
    fun all(): Map<String, String> = Vault.snapshot(Vault.SECRET)

    fun get(name: String): String = Vault.get(name, Vault.SECRET)

    /** 写入 / 覆盖一个密钥，空值等于删除 */
    fun put(name: String, value: String) {
        Vault.put(name, value, Vault.SECRET)
    } //设置密钥

    /** 把老的明文 secrets.json 搬进加密库，搬完删掉明文文件 */
    fun migrate() {
        val raw = Store.read(FILE) ?: return
        val o = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val m = LinkedHashMap<String, String>()
        o.keys().forEach { k ->
            val v = o.optString(k)
            if (v.isNotEmpty()) m[k] = v
        }
        if (m.isNotEmpty()) Vault.adopt(m, Vault.SECRET)
        runCatching { Store.file(FILE).delete() }
    } //迁移明文密钥

    /** 名称合法性与 skill.json 里 $secret.xxx 的写法一致 */
    fun validName(name: String): Boolean = name.isNotEmpty() && name[0].let { !it.isDigit() } &&
        name.all { it.isLetterOrDigit() || it == '_' } //合法密钥名
} //密钥表
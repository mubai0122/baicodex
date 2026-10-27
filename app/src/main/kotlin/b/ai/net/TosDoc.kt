package b.ai.net

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** 服务端下发的用户协议：版本号 + 当前语言的正文；拉不到就返回 null，客户端退回内置 assets */
object TosDoc {
    private const val BASE = "https://agent.ai.baiccb.cn/api/tos"
    private const val TAG = "bai.tos"

    /** version 决定要不要重新同意，lang 是正文实际语言，body 是纯文本正文 */
    data class Doc(val version: Int, val lang: String, val updated: String, val body: String)

    /** 按语言拉一次协议：lang 只传当前界面语言，服务端按需返回对应正文，不整包下发 */
    fun fetch(lang: String): Doc? {
        val conn = (URL("$BASE/get.php?lang=$lang").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 4000
            readTimeout = 6000
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (conn.responseCode != 200) return null
            val root = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            if (root.optInt("code") != 200) return null
            val d = root.optJSONObject("data") ?: return null
            val v = d.optInt("version", 0)
            val body = d.optString("body")
            if (v <= 0 || body.isBlank()) return null
            return Doc(version = v, lang = d.optString("lang", lang), updated = d.optString("updated").trim(), body = body)
        } catch (e: Exception) {
            Log.w(TAG, "协议拉取失败: ${e.message}")
            return null
        } finally {
            conn.disconnect()
        }
    } //拉一次协议
}
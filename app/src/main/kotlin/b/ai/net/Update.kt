package b.ai.net

import android.os.Build
import android.util.Log
import b.ai.App
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** 检查更新：版本判定只看 code（纯数字），version 仅用于展示；域名以实测可用的 agent.ai.baiccb.cn 为准 */
object Update {
    private const val BASE = "https://agent.ai.baiccb.cn/api/update"
    private const val TAG = "bai.update"

    /** 服务端返回的一条版本信息：get.php 与 History.php 的字段基本一致，缺的按默认值兜底 */
    data class Info(
        val latestVersion: String = "",
        val latestCode: Int = 0,
        val updateDate: String = "",
        val updateEnabled: Boolean = true,
        val forceUpdate: Boolean = false,
        val minForceCode: Int = 0,
        val version: String = "",
        val code: Int = 0,
        val title: String = "",
        val subtitle: String = "",
        val content: String = "",
        val clientCode: Int = -1,
        val hasUpdate: Boolean = false,
        val forceUpdateRequired: Boolean = false,
        val downloadJumpUrl: String = "",
        val downloadDirectUrl: String = ""
    )

    /** 更新历史里的一条记录 */
    data class His(
        val code: Int = 0,
        val version: String = "",
        val date: String = "",
        val title: String = "",
        val subtitle: String = "",
        val content: String = ""
    )

    /** 客户端 versionCode：API 28 起 versionCode 字段废弃，改读 longVersionCode */
    fun clientCode(): Int = runCatching {
        val pi = App.self.packageManager.getPackageInfo(App.self.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode.toInt() else pi.versionCode
    }.getOrDefault(0) //客户端版本号

    /** 客户端 versionName，只用于展示 */
    fun clientVersion(): String = runCatching {
        App.self.packageManager.getPackageInfo(App.self.packageName, 0).versionName ?: ""
    }.getOrDefault("") //客户端版本名

    /** 检查更新：必须带 code，服务端才会算 hasUpdate / forceUpdateRequired */
    fun check(code: Int = clientCode()): Info {
        val json = JSONObject(get("$BASE/get.php?code=$code"))
        if (json.optInt("code") != 200) error(json.optString("msg", "检查更新失败"))
        val d = json.optJSONObject("data") ?: error("返回缺少 data")
        val info = Info(
            latestVersion = d.optString("latestVersion"),
            latestCode = d.optInt("latestCode"),
            updateDate = d.optString("updateDate"),
            updateEnabled = d.optBoolean("updateEnabled", true),
            forceUpdate = d.optBoolean("forceUpdate", false),
            minForceCode = d.optInt("minForceCode"),
            version = d.optString("version"),
            code = d.optInt("code"),
            title = d.optString("title"),
            subtitle = d.optString("subtitle"),
            content = d.optString("content"),
            clientCode = d.optInt("clientCode", code),
            hasUpdate = d.optBoolean("hasUpdate", false),
            forceUpdateRequired = d.optBoolean("forceUpdateRequired", false),
            downloadJumpUrl = d.str("downloadJumpUrl"),
            downloadDirectUrl = d.str("downloadDirectUrl")
        )
        Log.i(
            TAG,
            "检查更新 client=$code latest=${info.latestCode} hasUpdate=${info.hasUpdate} " +
                "force=${info.forceUpdateRequired} enabled=${info.updateEnabled} " +
                "direct=${info.downloadDirectUrl.isNotBlank()} jump=${info.downloadJumpUrl.isNotBlank()}"
        )
        return info
    } //检查更新

    /** 取字符串：org.json 把 JSON null 读成 ""，这里统一折成空串，便于判断「上游未返回」 */
    private fun JSONObject.str(key: String): String {
        val s = optString(key, "")
        return if (s == "null") "" else s
    } //安全取字符串

    /** 下载更新包到指定文件；contentLength 拿不到时（chunked）progress 一律回 -1 = 不定长 */
    fun download(url: String, dest: File, onProgress: (Int) -> Unit): File {
        var current = url
        //HttpURLConnection 只自动跟随同协议跳转，http→https 这类要自己跟
        repeat(4) {
            val c = URL(current).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "BaiAgent-Update")
            try {
                val http = c.responseCode
                if (http in 300..399) {
                    val loc = c.getHeaderField("Location") ?: error("HTTP $http 无跳转地址")
                    current = URL(URL(current), loc).toString()
                    return@repeat
                }
                if (http !in 200..299) error("HTTP $http")
                val total = c.contentLengthLong
                dest.parentFile?.mkdirs()
                c.inputStream.use { ins ->
                    dest.outputStream().use { outs ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var lastPct = -2
                        while (true) {
                            val n = ins.read(buf)
                            if (n <= 0) break
                            outs.write(buf, 0, n)
                            done += n
                            val pct = if (total > 0) (done * 100 / total).toInt() else -1
                            if (pct != lastPct) {
                                lastPct = pct
                                onProgress(pct)
                            }
                        }
                    }
                }
                if (dest.length() <= 0) error("下载到的文件是空的")
                Log.i(TAG, "下载完成 ${dest.absolutePath} ${dest.length()} 字节")
                return dest
            } finally {
                c.disconnect()
            }
        }
        error("跳转次数过多")
    } //下载更新包

    /** 更新历史：服务端读 Update.json，第一条为最新 */
    fun history(): List<His> {
        val json = JSONObject(get("$BASE/History.php"))
        if (json.optInt("code") != 200) error(json.optString("msg", "读取更新历史失败"))
        val d = json.optJSONObject("data") ?: error("返回缺少 data")
        val arr = d.optJSONArray("list") ?: return emptyList()
        val out = ArrayList<His>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                His(
                    code = o.optInt("code"),
                    version = o.optString("version"),
                    date = o.optString("date"),
                    title = o.optString("title"),
                    subtitle = o.optString("subtitle"),
                    content = o.optString("content")
                )
            )
        }
        Log.i(TAG, "更新历史 ${out.size} 条")
        return out
    } //更新历史

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.requestMethod = "GET"
        return try {
            val http = c.responseCode
            val text = (if (http in 200..299) c.inputStream else c.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if (http !in 200..299) error("HTTP $http: ${text.take(200)}")
            text
        } finally {
            c.disconnect()
        }
    } //GET 请求
} //检查更新
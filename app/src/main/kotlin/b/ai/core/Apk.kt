package b.ai.core

import java.io.ByteArrayInputStream
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** APK 逆向：纯 Java 标准库读签名证书、二进制清单（AXML）与 dex 字符串，不依赖 apktool / aapt */
object Apk {
    data class Res(val ok: Boolean, val text: String)

    /** 单个 dex 超过这个体积就别整块读进内存了 */
    private const val MAX_DEX = 40_000_000

    /** dex 字符串上限：一个 dex 常有十几万条，全吐给模型没意义 */
    private const val MAX_STR = 40_000

    fun run(action: String, path: String, keyword: String, limit: Int): Res {
        val p = path.trim()
        if (p.isEmpty()) return Res(false, "缺少 path")
        val f = File(p)
        if (!f.exists()) return Res(false, "文件不存在：$p")
        if (!f.isFile) return Res(false, "不是文件：$p")
        val z = runCatching { ZipFile(f) }.getOrElse { return Res(false, "打不开，可能不是 apk/zip：${it.message}") }
        return z.use { zip ->
            runCatching {
                when (action.trim().lowercase(Locale.ROOT).ifEmpty { "info" }) {
                    "sign", "signature", "签名" -> Res(true, sign(zip, f))
                    "permissions", "perm", "权限" -> Res(true, perms(zip))
                    "components", "comp", "组件" -> Res(true, components(zip))
                    "dex", "strings", "字符串" -> Res(true, dex(zip, keyword, limit))
                    "files", "list", "文件" -> Res(true, files(zip, limit))
                    else -> Res(true, info(zip, f))
                }
            }.getOrElse { Res(false, "解析失败：" + (it.message ?: it.javaClass.simpleName)) }
        }
    } //对外入口：action 分发

    //---------------------------------------------------------- 概览

    private fun info(z: ZipFile, f: File): String {
        val els = manifest(z)
        val man = els.firstOrNull { it.tag == "manifest" }
        val uses = els.filter { it.tag == "uses-permission" }.mapNotNull { it.a["name"] }
        val dexes = dexEntries(z)
        val sb = StringBuilder()
        sb.append("APK：").append(f.name).append("（").append(human(f.length())).append("）\n")
        sb.append("包名：").append(one(man, "package").ifEmpty { "未知" }).append('\n')
        val vn = one(man, "versionName")
        val vc = one(man, "versionCode")
        sb.append("版本：").append(vn.ifEmpty { "?" })
        if (vc.isNotEmpty()) sb.append(" (versionCode ").append(vc).append(')')
        sb.append('\n')
        val minS = attr(els.firstOrNull { it.tag == "uses-sdk" }, "minSdkVersion")
        val tarS = attr(els.firstOrNull { it.tag == "uses-sdk" }, "targetSdkVersion")
        sb.append("SDK：min ").append(minS.ifEmpty { "?" }).append(" / target ").append(tarS.ifEmpty { "?" }).append('\n')
        sb.append("签名：").append(signBrief(z)).append('\n')
        sb.append("签名块 v2/v3：").append(if (hasBlock(f)) "存在" else "未检出").append('\n')
        sb.append("权限：").append(uses.size).append(" 个")
        val bad = uses.filter { riskOf(it).isNotEmpty() }
        if (bad.isNotEmpty()) sb.append("，其中敏感 ").append(bad.size).append(" 个")
        sb.append('\n')
        if (bad.isNotEmpty()) bad.forEach { sb.append("  · ").append(short(it)).append(" —— ").append(riskOf(it)).append('\n') }
        val cnt = HashMap<String, Int>()
        els.forEach { if (it.tag in COMPONENTS) cnt[it.tag] = (cnt[it.tag] ?: 0) + 1 }
        sb.append("组件：")
        if (cnt.isEmpty()) sb.append("无") else sb.append(
            COMPONENTS.joinToString(" / ") { t -> t + " " + (cnt[t] ?: 0) }
        )
        sb.append('\n')
        sb.append("dex：").append(dexes.size).append(" 个")
        val totalStr = dexes.sumOf { runCatching { dexCount(z, it) }.getOrDefault(0) }
        if (totalStr > 0) sb.append("，字符串 ").append(totalStr).append(" 条")
        sb.append('\n')
        val acts = els.filter { it.tag == "activity" }.mapNotNull { it.a["name"] }.take(30)
        if (acts.isNotEmpty()) {
            sb.append("Activity（前 ").append(acts.size).append(" 个）：\n")
            acts.forEach { sb.append("  · ").append(it).append('\n') }
        }
        return sb.toString().trim()
    } //概览

    private val COMPONENTS = listOf("activity", "activity-alias", "service", "receiver", "provider")

    private val RISK = mapOf(
        "SEND_SMS" to "可发短信，可能扣费",
        "READ_SMS" to "读全部短信",
        "RECEIVE_SMS" to "拦截短信",
        "CALL_PHONE" to "直接拨号",
        "READ_PHONE_STATE" to "读设备标识",
        "READ_CONTACTS" to "读通讯录",
        "WRITE_CONTACTS" to "改通讯录",
        "READ_CALL_LOG" to "读通话记录",
        "CAMERA" to "相机",
        "RECORD_AUDIO" to "录音",
        "ACCESS_FINE_LOCATION" to "精确定位",
        "ACCESS_BACKGROUND_LOCATION" to "后台定位",
        "MANAGE_EXTERNAL_STORAGE" to "全盘文件读写",
        "REQUEST_INSTALL_PACKAGES" to "可装其它应用",
        "SYSTEM_ALERT_WINDOW" to "悬浮窗覆盖",
        "QUERY_ALL_PACKAGES" to "枚举已装应用",
        "BIND_ACCESSIBILITY_SERVICE" to "无障碍（可代替用户点击）",
        "READ_NOTIFICATION" to "读通知",
        "BIND_NOTIFICATION_LISTENER_SERVICE" to "读通知",
        "WRITE_SECURE_SETTINGS" to "改系统设置",
        "DEVICE_ADMIN" to "设备管理器",
        "PACKAGE_USAGE_STATS" to "读应用使用记录",
        "ACCESSIBILITY" to "无障碍",
        "POST_NOTIFICATIONS" to "",
        "WAKE_LOCK" to ""
    )

    private fun riskOf(perm: String): String = RISK[short(perm)].orEmpty()

    /** android.permission.READ_SMS → READ_SMS，自定义权限保留短名 */
    private fun short(perm: String): String = perm.substringAfterLast('.')

    private fun one(el: El?, key: String): String = el?.a?.get(key).orEmpty()

    private fun attr(el: El?, key: String): String = el?.a?.get(key).orEmpty()

    //---------------------------------------------------------- 签名

    private fun sign(z: ZipFile, f: File): String {
        val sb = StringBuilder()
        sb.append("文件：").append(f.name).append("（").append(human(f.length())).append("）\n")
        sb.append("整体 SHA-256：").append(fileHash(f)).append('\n')
        val names = Collections.list(z.entries()).filter { n ->
            val u = n.name.uppercase(Locale.ROOT)
            u.startsWith("META-INF/") && (u.endsWith(".RSA") || u.endsWith(".DSA") || u.endsWith(".EC"))
        }.map { it.name }
        if (names.isEmpty()) {
            sb.append("\n没有 META-INF 证书：未签名，或只用了 v2/v3 签名方案\n")
        } else {
            val cf = CertificateFactory.getInstance("X.509")
            for (n in names) {
                val bytes = z.getInputStream(z.getEntry(n)).use { it.readBytes() }
                val certs = runCatching { cf.generateCertificates(ByteArrayInputStream(bytes)) }
                    .getOrNull().orEmpty()
                sb.append("\n[").append(n).append("]\n")
                if (certs.isEmpty()) {
                    sb.append("证书解析失败\n")
                    continue
                }
                certs.forEachIndexed { i, c ->
                    val x = c as? X509Certificate
                    if (x == null) {
                        sb.append("证书 ").append(i + 1).append("：非 X509\n")
                    } else {
                        sb.append("证书 ").append(i + 1).append('\n')
                        sb.append("  主体：").append(x.subjectDN.name).append('\n')
                        sb.append("  签发：").append(x.issuerDN.name).append('\n')
                        sb.append("  序列号：").append(x.serialNumber.toString(16)).append('\n')
                        sb.append("  算法：").append(x.sigAlgName).append(" / 公钥 ")
                            .append(x.publicKey.algorithm).append('\n')
                        sb.append("  有效期：").append(day(x.notBefore)).append(" → ").append(day(x.notAfter))
                        if (x.notAfter.before(Date())) sb.append("（已过期）")
                        sb.append('\n')
                        sb.append("  SHA-256：").append(fp(x, "SHA-256")).append('\n')
                        sb.append("  SHA-1：").append(fp(x, "SHA-1")).append('\n')
                        sb.append("  MD5：").append(fp(x, "MD5")).append('\n')
                        sb.append("  自签名：").append(if (x.subjectDN == x.issuerDN) "是" else "否").append('\n')
                    }
                }
            }
        }
        sb.append("\nv2/v3 签名块：").append(if (hasBlock(f)) "存在" else "未检出")
        return sb.toString().trim()
    } //签名详情

    private fun signBrief(z: ZipFile): String {
        val name = Collections.list(z.entries()).firstOrNull { n ->
            val u = n.name.uppercase(Locale.ROOT)
            u.startsWith("META-INF/") && (u.endsWith(".RSA") || u.endsWith(".DSA") || u.endsWith(".EC"))
        } ?: return "无 v1 证书"
        val bytes = z.getInputStream(name).use { it.readBytes() }
        val cert = runCatching {
            CertificateFactory.getInstance("X.509").generateCertificates(ByteArrayInputStream(bytes))
                .firstOrNull() as? X509Certificate
        }.getOrNull() ?: return "证书解析失败"
        return "SHA-256 " + fp(cert, "SHA-256") + "，有效期至 " + day(cert.notAfter) +
            if (cert.subjectDN == cert.issuerDN) "，自签名" else ""
    } //签名摘要一行

    /** APK Signing Block 结尾魔数：用它判断有没有 v2/v3 签名块 */
    private fun hasBlock(f: File): Boolean {
        val magic = "APK Sig Block 42".toByteArray(Charsets.US_ASCII)
        val len = f.length()
        if (len < 1024) return false
        val tail = minOf(len, 131_072L).toInt()
        val buf = ByteArray(tail)
        runCatching {
            RandomAccessFile(f, "r").use { rf ->
                rf.seek(len - tail)
                rf.readFully(buf)
            }
        }.getOrElse { return false }
        var i = 0
        while (i + magic.size <= buf.size) {
            var j = 0
            while (j < magic.size && buf[i + j] == magic[j]) j++
            if (j == magic.size) return true
            i++
        }
        return false
    } //v2/v3 签名块探测

    private fun fp(c: X509Certificate, alg: String): String {
        val d = MessageDigest.getInstance(alg).digest(c.encoded)
        val sb = StringBuilder(d.size * 3)
        d.forEach { b ->
            if (sb.isNotEmpty()) sb.append(':')
            sb.append(String.format(Locale.ROOT, "%02X", b.toInt() and 0xFF))
        }
        return sb.toString()
    } //证书指纹

    private fun fileHash(f: File): String = runCatching {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { ins ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = ins.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        val d = md.digest()
        val sb = StringBuilder(d.size * 3)
        d.forEach { b ->
            if (sb.isNotEmpty()) sb.append(':')
            sb.append(String.format(Locale.ROOT, "%02X", b.toInt() and 0xFF))
        }
        sb.toString()
    }.getOrDefault("计算失败")

    private fun day(t: Date): String = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(t)

    //---------------------------------------------------------- 权限 / 组件

    private fun perms(z: ZipFile): String {
        val els = manifest(z)
        val list = els.filter { it.tag == "uses-permission" }.mapNotNull { it.a["name"] }
        if (list.isEmpty()) return "没有声明权限"
        val max = els.firstOrNull { it.tag == "uses-permission-sdk-23" } != null
        val sb = StringBuilder("权限共 ").append(list.size).append(" 个")
        if (max) sb.append("（另含 sdk-23 分组声明）")
        sb.append('\n')
        list.sorted().forEach { p ->
            sb.append("· ").append(p)
            val r = riskOf(p)
            if (r.isNotEmpty()) sb.append("  ← ").append(r)
            sb.append('\n')
        }
        val custom = list.filter { !it.startsWith("android.permission.") }
        if (custom.isNotEmpty()) {
            sb.append("\n自定义权限 ").append(custom.size).append(" 个（多半是给其它应用调自己用的）\n")
        }
        return sb.toString().trim()
    } //权限清单

    private fun components(z: ZipFile): String {
        val els = manifest(z)
        val main = els.filter { it.tag == "action" }.mapNotNull { it.a["name"] }
            .firstOrNull { it == "android.intent.action.MAIN" }
        val sb = StringBuilder()
        if (main != null) sb.append("有 launcher 入口（MAIN）\n")
        var any = false
        COMPONENTS.forEach { tag ->
            val items = els.filter { it.tag == tag }
            if (items.isEmpty()) return@forEach
            any = true
            sb.append("\n").append(tag).append(" 共 ").append(items.size).append(" 个\n")
            items.take(60).forEach { el ->
                val n = el.a["name"].orEmpty().ifEmpty { "（无 name）" }
                sb.append("  · ").append(n)
                if (el.a["exported"] == "true") sb.append("  [exported]")
                val perm = el.a["permission"]
                if (!perm.isNullOrBlank()) sb.append("  [需 ").append(short(perm)).append("]")
                sb.append('\n')
            }
            if (items.size > 60) sb.append("  …还有 ").append(items.size - 60).append(" 个\n")
        }
        if (!any) sb.append("没有任何组件声明")
        return sb.toString().trim()
    } //组件清单

    //---------------------------------------------------------- dex

    private fun dexEntries(z: ZipFile): List<ZipEntry> = Collections.list(z.entries())
        .filter { it.name.matches(Regex("""classes\d*\.dex""")) }
        .sortedBy { it.name }

    private fun dexCount(z: ZipFile, e: ZipEntry): Int {
        val b = readDex(z, e) ?: return 0
        return u32(b, 0x38)
    } //dex 字符串总数

    private fun readDex(z: ZipFile, e: ZipEntry): ByteArray? {
        if (e.size > MAX_DEX) return null
        return runCatching { z.getInputStream(e).use { it.readBytes() } }.getOrNull()
    }

    private fun dex(z: ZipFile, keyword: String, limit: Int): String {
        val entries = dexEntries(z)
        if (entries.isEmpty()) return "没有 dex：可能只是普通 zip，或用了外部加载"
        val re = runCatching { Regex(keyword, RegexOption.IGNORE_CASE) }.getOrNull()
        val cap = limit.coerceIn(5, 400)
        val sb = StringBuilder()
        sb.append("dex 共 ").append(entries.size).append(" 个")
        if (keyword.isNotBlank()) sb.append("，按「").append(keyword).append("」过滤")
        sb.append('\n')
        var shown = 0
        var scanned = 0
        for (e in entries) {
            val b = readDex(z, e)
            if (b == null) {
                sb.append("\n[").append(e.name).append("] 体积 ").append(human(e.size)).append("，过大已跳过\n")
                continue
            }
            val all = strings(b)
            scanned += all.size
            if (keyword.isBlank()) {
                sb.append("\n[").append(e.name).append("] ").append(all.size).append(" 条字符串\n")
                val take = minOf(all.size, cap - shown)
                for (i in 0 until take) sb.append("  ").append(clip(all[i])).append('\n')
                shown += take
            } else {
                val hit = all.filter { s ->
                    if (re != null) re.containsMatchIn(s) else s.contains(keyword, true)
                }
                if (hit.isEmpty()) continue
                sb.append("\n[").append(e.name).append("] 命中 ").append(hit.size).append(" 条\n")
                val take = minOf(hit.size, cap - shown)
                for (i in 0 until take) sb.append("  ").append(clip(hit[i])).append('\n')
                shown += take
            }
            if (shown >= cap) {
                sb.append("\n（已达 ").append(cap).append(" 条上限，收窄 keyword 或调 limit 继续）\n")
                break
            }
        }
        if (shown == 0) sb.append("没命中。换关键词，或干脆不带 keyword 看总量\n")
        sb.append("\n共扫过 ").append(scanned).append(" 条字符串")
        return sb.toString().trim()
    } //dex 字符串搜索

    private fun clip(s: String): String {
        val t = s.replace("\n", "\\n").replace("\r", "")
        return if (t.length <= 160) t else t.substring(0, 160) + "…"
    }

    /** dex 里的字符串是 MUTF-8：先读 uleb128 字符数，再读到 0 结尾 */
    private fun strings(b: ByteArray): List<String> {
        if (b.size < 0x70) return emptyList()
        if (!(b[0].toInt() == 0x64 && b[1].toInt() == 0x65 && b[2].toInt() == 0x78)) return emptyList()
        val count = u32(b, 0x38)
        val off = u32(b, 0x3C)
        if (count <= 0 || off <= 0 || off + count * 4 > b.size) return emptyList()
        val out = ArrayList<String>(minOf(count, 1024))
        var i = 0
        while (i < count && out.size < MAX_STR) {
            val so = u32(b, off + i * 4)
            i++
            if (so <= 0 || so >= b.size) continue
            var p = so
            var k = 0
            while (p < b.size && (b[p].toInt() and 0x80) != 0) {
                p++
                k++
                if (k > 4) break
            }
            p++
            var e = p
            while (e < b.size && b[e].toInt() != 0) e++
            if (e > p) out.add(String(b, p, e - p, Charsets.UTF_8))
        }
        return out
    } //dex 字符串表

    //---------------------------------------------------------- 文件列表

    private fun files(z: ZipFile, limit: Int): String {
        val all = Collections.list(z.entries()).filter { !it.isDirectory }
        val cap = limit.coerceIn(5, 500)
        val sb = StringBuilder("条目共 ").append(all.size).append(" 个，按体积倒序（前 ")
            .append(minOf(cap, all.size)).append("）\n")
        all.sortedByDescending { it.size }.take(cap).forEach { e ->
            sb.append(human(e.size)).append("  ").append(e.name)
            if (e.compressedSize > 0 && e.size > 0) {
                sb.append("  (").append(e.compressedSize * 100 / e.size).append("%)")
            }
            sb.append('\n')
        }
        val top = all.groupBy { it.name.substringBefore('/') }.mapValues { it.value.size }
            .entries.sortedByDescending { it.value }.take(15)
        sb.append("\n顶层分布：")
        sb.append(top.joinToString("  ") { it.key + "=" + it.value })
        return sb.toString().trim()
    } //zip 条目

    private fun human(n: Long): String = when {
        n >= 1L shl 30 -> String.format(Locale.ROOT, "%.2f GB", n / 1073741824.0)
        n >= 1L shl 20 -> String.format(Locale.ROOT, "%.1f MB", n / 1048576.0)
        n >= 1L shl 10 -> String.format(Locale.ROOT, "%.1f KB", n / 1024.0)
        else -> "$n B"
    }

    //---------------------------------------------------------- AXML

    private class El(val tag: String, val a: Map<String, String>)

    private fun manifest(z: ZipFile): List<El> {
        val e = z.getEntry("AndroidManifest.xml") ?: return emptyList()
        if (e.size > 8_000_000) return emptyList()
        val b = runCatching { z.getInputStream(e).use { it.readBytes() } }.getOrNull() ?: return emptyList()
        return axml(b)
    } //读二进制清单

    private fun axml(b: ByteArray): List<El> {
        val out = ArrayList<El>()
        if (b.size < 8 || u16(b, 0) != 0x0003) return out
        var strings: List<String> = emptyList()
        var p = 8
        while (p + 8 <= b.size) {
            val type = u16(b, p)
            val hsz = u16(b, p + 2)
            val sz = u32(b, p + 4)
            if (sz <= 0 || p + sz > b.size) break
            when (type) {
                0x0001 -> strings = pool(b, p)
                0x0102 -> {
                    val ext = p + hsz
                    if (ext + 20 <= b.size) {
                        val tag = pick(strings, u32(b, ext + 4))
                        val aStart = u16(b, ext + 8)
                        val aStep = u16(b, ext + 10).let { if (it < 20) 20 else it }
                        val aCount = u16(b, ext + 12)
                        val attrs = LinkedHashMap<String, String>()
                        var i = 0
                        while (i < aCount && i < 64) {
                            val ao = ext + aStart + i * aStep
                            i++
                            if (ao + 20 > b.size) break
                            val key = pick(strings, u32(b, ao + 4))
                            if (key.isBlank()) continue
                            val raw = u32(b, ao + 8)
                            val val2 = if (raw != -1) pick(strings, raw) else typed(b, strings, ao + 12)
                            if (val2.isNotEmpty()) attrs[key] = val2
                        }
                        if (tag.isNotEmpty()) out.add(El(tag, attrs))
                    }
                }
            }
            p += sz
        }
        return out
    } //AXML 解出元素表

    private fun typed(b: ByteArray, strings: List<String>, o: Int): String {
        if (o + 8 > b.size) return ""
        val dt = b[o + 3].toInt() and 0xFF
        val data = u32(b, o + 4)
        return when (dt) {
            0x03 -> pick(strings, data)
            0x10 -> data.toString()
            0x11 -> "0x" + Integer.toHexString(data)
            0x12 -> if (data != 0) "true" else "false"
            0x01 -> "@" + Integer.toHexString(data)
            0x00 -> ""
            else -> data.toString()
        }
    } //属性类型值

    private fun pick(l: List<String>, i: Int): String = if (i >= 0 && i < l.size) l[i] else ""

    private fun pool(b: ByteArray, off: Int): List<String> {
        val count = u32(b, off + 8)
        val flags = u32(b, off + 16)
        val start = u32(b, off + 20)
        if (count <= 0 || count > 200_000 || start <= 0) return emptyList()
        val utf8 = (flags and 0x100) != 0
        val out = ArrayList<String>(minOf(count, 512))
        var i = 0
        while (i < count) {
            val so = off + start + u32(b, off + 28 + i * 4)
            i++
            if (so <= 0 || so >= b.size) {
                out.add("")
                continue
            }
            out.add(if (utf8) c8(b, so) else c16(b, so))
        }
        return out
    } //字符串池

    private fun c8(b: ByteArray, o: Int): String {
        var p = o
        if (p + 2 >= b.size) return ""
        var len = b[p].toInt() and 0xFF
        p++
        if (len and 0x80 != 0) {
            len = ((len and 0x7F) shl 8) or (b[p].toInt() and 0xFF)
            p++
        }
        var blen = b[p].toInt() and 0xFF
        p++
        if (blen and 0x80 != 0) {
            blen = ((blen and 0x7F) shl 8) or (b[p].toInt() and 0xFF)
            p++
        }
        val end = minOf(p + blen, b.size)
        val sb = StringBuilder(minOf(len, 256))
        while (p < end) {
            val c = b[p].toInt() and 0xFF
            when {
                c < 0x80 -> {
                    sb.append(c.toChar())
                    p++
                }
                c and 0xE0 == 0xC0 -> {
                    if (p + 1 >= end) break
                    sb.append((((c and 0x1F) shl 6) or (b[p + 1].toInt() and 0x3F)).toChar())
                    p += 2
                }
                c and 0xF0 == 0xE0 -> {
                    if (p + 2 >= end) break
                    sb.append(
                        (((c and 0x0F) shl 12) or ((b[p + 1].toInt() and 0x3F) shl 6) or
                            (b[p + 2].toInt() and 0x3F)).toChar()
                    )
                    p += 3
                }
                c and 0xF8 == 0xF0 -> {
                    if (p + 3 >= end) break
                    val cp = ((c and 0x07) shl 18) or ((b[p + 1].toInt() and 0x3F) shl 12) or
                        ((b[p + 2].toInt() and 0x3F) shl 6) or (b[p + 3].toInt() and 0x3F)
                    sb.appendCodePoint(cp)
                    p += 4
                }
                else -> p++
            }
        }
        return sb.toString()
    } //UTF-8 变体字符串

    private fun c16(b: ByteArray, o: Int): String {
        var p = o
        if (p + 2 > b.size) return ""
        var len = u16(b, p)
        p += 2
        if (len and 0x8000 != 0) {
            len = ((len and 0x7FFF) shl 16) or u16(b, p)
            p += 2
        }
        val sb = StringBuilder(minOf(len, 256))
        var i = 0
        while (i < len && p + 1 < b.size) {
            sb.append(((b[p].toInt() and 0xFF) or ((b[p + 1].toInt() and 0xFF) shl 8)).toChar())
            p += 2
            i++
        }
        return sb.toString()
    } //UTF-16 字符串

    private fun u16(b: ByteArray, o: Int): Int =
        if (o >= 0 && o + 1 < b.size) (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) else 0

    private fun u32(b: ByteArray, o: Int): Int =
        if (o >= 0 && o + 3 < b.size) {
            (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or
                ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)
        } else 0
}
package b.ai.exec

import b.ai.core.Cfg
import b.ai.core.WorkMode

/**编译工具链检测：编译 APK 要的 JDK / gradle / aapt2 / Android SDK 是否齐 */
object Toolchain {
    data class Item(val name: String, val ok: Boolean)

    data class Report(
        val reachable: Boolean,
        val note: String,
        val items: List<Item> = emptyList(),
        val raw: String = ""
    ) {
        val ok: Boolean get() = reachable && items.isNotEmpty() && items.all { it.ok }
        val missing: List<Item> get() = items.filter { !it.ok }
    }

    /** 装工具链的命令：直接粘到 Termux 里执行 */
    const val INSTALL_CMD =
        "pkg install -y openjdk-17 gradle aapt apksigner zipalign unzip curl git"

    const val SDK_CMD =
        "mkdir -p ~/android-sdk && cd ~/android-sdk && " +
            "sdkmanager \"platforms;android-36\" \"build-tools;36.0.0\" \"platform-tools\""

    const val ENV_CMD =
        "export ANDROID_HOME=~/android-sdk ANDROID_SDK_ROOT=~/android-sdk"

    private fun label(c: String) = when (c) {
        "java" -> "JDK（java）"
        "javac" -> "编译器（javac）"
        "gradle" -> "gradle"
        "aapt2" -> "aapt2（资源打包）"
        "apksigner" -> "apksigner（签名）"
        "zipalign" -> "zipalign（对齐）"
        "unzip" -> "unzip（解包）"
        "curl" -> "curl（下载）"
        "git" -> "git"
        "sdk" -> "Android SDK（platforms）"
        "project" -> "工程目录 /sdcard/AndroidCSProjects/BaiAgent"
        else -> c
    }

    //探测脚本：只读检查，不装任何东西；Termux 私有目录只有 root / shell 身份能读
    private fun script(): String = buildString {
        append("T=/data/data/com.termux/files/usr\n")
        append("for c in java javac gradle aapt2 apksigner zipalign unzip curl git; do\n")
        append("  if [ -x \"\$T/bin/\$c\" ]; then echo \"ok \$c\"; else echo \"no \$c\"; fi\n")
        append("done\n")
        append("if [ -d /data/data/com.termux/files/home/android-sdk/platforms ]; then echo \"ok sdk\"; else echo \"no sdk\"; fi\n")
        append("if [ -d /sdcard/AndroidCSProjects/BaiAgent/app ]; then echo \"ok project\"; else echo \"no project\"; fi\n")
    }

    fun probe(timeoutMs: Long = 20_000L): Report {
        return when (Cfg.workMode) {
            WorkMode.NORMAL -> Report(
                false,
                "当前是「普通用户」模式：读不到 Termux 私有目录。切到 Root 或 Shizuku 才能检测"
            )
            WorkMode.ROOT -> parse(Root.run(script(), timeoutMs))
            WorkMode.SHIZUKU -> parse(Shiz.run(script(), timeoutMs = timeoutMs))
        }
    } //按当前工作方式跑一次探测

    private fun parse(r: ShellResult): Report {
        val items = ArrayList<Item>()
        r.out.lineSequence().forEach { line ->
            val p = line.trim().split(' ', limit = 2)
            if (p.size == 2 && (p[0] == "ok" || p[0] == "no")) {
                items.add(Item(label(p[1]), p[0] == "ok"))
            }
        }
        val note = when {
            items.isEmpty() -> "探测没拿到结果（exit=${r.code}）：${r.out.take(200)}"
            else -> {
                val miss = items.filter { !it.ok }.map { it.name }
                if (miss.isEmpty()) "工具链齐全，可以直接编译" else "缺 ${miss.joinToString("、")}"
            }
        }
        return Report(true, note, items, r.out)
    } //解析 ok/no 行
} //工具链检测
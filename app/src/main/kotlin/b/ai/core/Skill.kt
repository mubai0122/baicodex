package b.ai.core

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** 一条技能：名字 + 一句说明 + 正文（步骤/命令模板），启用后拼进系统提示 */
data class Skill(
    val id: String,
    val name: String,
    val desc: String,
    val body: String,
    val on: Boolean
)

/** 技能库：整表 JSON 落在数据根 skills.json，任何改动立刻落盘 */
object Skills {
    /** 数据根里的文件名，用户可以直接用文本编辑器改 */
    const val FILE = "skills.json"

    /** 预置技能版本号：加过新预置就 +1，老用户只补一次，删掉的不会被塞回来 */
    const val PRESET_SEED = 2

    private val cache = mutableStateOf<List<Skill>>(emptyList())
    private var sp: SharedPreferences? = null

    val list: List<Skill> get() = cache.value
    val enabled: List<Skill> get() = cache.value.filter { it.on }

    fun init(c: Context) {
        if (sp != null) return
        val p = c.applicationContext.getSharedPreferences("bai_cfg", Context.MODE_PRIVATE)
        sp = p
        //文件优先：用户手改过就以文件为准；文件不在则迁移旧配置或写预置技能
        val fromFile = Store.read(FILE)
        cache.value = if (fromFile != null) {
            runCatching { parse(fromFile) }.getOrDefault(emptyList())
        } else {
            val legacy = p.getString("skills", null)
            if (legacy == null) defaults() else runCatching { parse(legacy) }.getOrDefault(emptyList())
        }
        seedOnce()
        save()
    } //读盘 + 补新版预置 + 落盘

    /** 新版本带来的预置技能：按版本号只补一次，用户自己删掉的不会被反复塞回来 */
    private fun seedOnce() {
        if (Cfg.presetSeed >= PRESET_SEED) return
        val have = cache.value.map { it.id }.toSet()
        cache.value = cache.value + defaults().filter { it.id !in have }
        Cfg.presetSeed = PRESET_SEED
    } //按版本补预置

    private fun defaults(): List<Skill> = listOf(
        Skill(
            id = "preset-build",
            name = "构建并安装本机应用",
            desc = "用户说编译 / 打包 / 出 APK 时按这条走",
            body = """
                1) 先在工作区里确认工程根：找 gradlew 或 build.gradle(.kts)，有 app/ 子目录就是单模块工程。
                2) 构建命令一律在工程根执行，路径用相对的（exec_shell 已经 cd 到工作区）：
                   ./gradlew assembleDebug；工程自带脚本时用 bash ./app/build.sh -d </dev/null（必须重定向 stdin，否则会停在交互菜单死等）。
                3) 产物在 app/build/outputs/apk/debug/app-debug.apk，成功后用 read_file 核一下大小。
                4) 失败先过滤错误行：把输出里的 "e: " 行找出来，按报错文件与行号定位，不要靠猜。
                5) 安装：exec_shell 跑 pm install -r <产物的绝对路径>，装之前先问用户。
                6) 构建很慢（几十秒到几分钟），先告诉用户「正在构建」，别静默等到超时。
            """.trimIndent(),
            on = true
        ),
        Skill(
            id = "preset-reverse",
            name = "APK 逆向速查",
            desc = "分析别人 APK 的包名、权限、组件与硬编码",
            body = """
                产物统一落工作区的 reverse/<包名>/，命令都用 exec_shell：
                1) 包信息与权限：aapt dump badging <apk> （看 package、launchable-activity、uses-permission）
                2) 组件与联网：aapt dump xmltree <apk> AndroidManifest.xml | head -200
                3) 文件清单：unzip -l <apk> | head -100 （assets / lib / classes*.dex 分布）
                4) 硬编码线索：unzip -p <apk> classes.dex | strings | grep -Ei 'http|api|key|token|secret' | head -50
                5) 需要可读的 Java 源码就问用户是否用 jadx；不在本机装不明来源的工具。
                结论按「包名 / 权限 / 组件 / 疑似接口」四段输出，别贴整段 dump。
            """.trimIndent(),
            on = true
        ),
        Skill(
            id = "preset-ui",
            name = "操作助手（点屏幕）",
            desc = "按用户描述在界面上点、滑、输入",
            body = """
                你看不到截图，只能读控件树，所以一律按这个顺序做：
                1) 先确认当前界面：exec_shell 跑 uiautomator dump ui.xml（落在工作区），再 read_file ui.xml 读文本。
                2) 从节点里取 text 与 bounds="[x1,y1][x2,y2]"，中心点 = ((x1+x2)/2, (y1+y2)/2)。
                3) 点击：input tap <x> <y>；滑动：input swipe <x1> <y1> <x2> <y2> 300；输入：先点中控件，再 input text <内容>（空格要写成 %s，中文用 adb 键盘不一定生效，失败就告诉用户）。
                4) 每一步之后重新 dump 一次确认界面真的变了，别连着盲点。
                5) 出现权限弹窗、支付、删除确认这类不可逆操作时停下来问用户。
            """.trimIndent(),
            on = true
        ),
        Skill(
            id = "preset-rail",
            name = "12306 查票",
            desc = "余票 / 中转 / 经停，一律用 rail_query",
            body = """
                用内置工具 rail_query，不要凭记忆编车次与时刻：
                - action=tickets：from / to 写中文站名或城市名（杭州、上海虹桥），date 写 yyyy-MM-dd，只能查今天起 15 天内。
                - action=transfer：多传 middle（中转城市，如 南京）。
                - action=route：多传 train_no，从 tickets 结果的 no= 字段取（形如 650000K52806）。
                - 用户说「明天 / 后天」先自己换算成日期再查。
                - 结果里 有/无 是余票状态，数字是剩余张数；查不到就如实说查不到，别编车次。
            """.trimIndent(),
            on = true
        ),
        Skill(
            id = "preset-media",
            name = "音视频处理（ffmpeg）",
            desc = "抽音轨 / 截取片段 / 转码 / 压缩，先探工具再动手",
            body = """
                1) 先探工具：exec_shell 跑 command -v ffmpeg；没有就说明本机没装，问用户是否在内置 Ubuntu 里装（设置 → 扩展 → Linux 环境 → 安装，再 apt-get install -y ffmpeg）。
                2) 输入文件一律用工作区里的绝对路径，产物写进工作区 media/ 下，别写到 / 根。
                3) 常用组合（参数照抄，输出名自己定）：
                   - 抽音轨：ffmpeg -i in.mp4 -vn -c:a copy out.m4a
                   - 截取：ffmpeg -ss 00:00:10 -to 00:00:40 -i in.mp4 -c copy out.mp4
                   - 压缩：ffmpeg -i in.mp4 -vcodec libx264 -crf 28 -preset fast out.mp4
                   - 转格式：ffmpeg -i in.wav -c:a aac -b:a 128k out.m4a
                4) 跑完用 read_file 或 ls -l 核对产物大小，别只看命令回显。
                5) 大文件很慢：先告诉用户预计耗时，别静默等。
            """.trimIndent(),
            on = true
        ),
        Skill(
            id = "preset-doc",
            name = "资料整理成文档",
            desc = "把散落的笔记 / 日志 / 网页内容收拢成一份 markdown",
            body = """
                1) 先确认素材范围：用户给的路径、@文件或工作区里的目标目录，用 exec_shell 的 ls / find 列一遍再动笔。
                2) 读取用 read_file，单文件别整段贴进回答；超长就分段读，先抽要点。
                3) 产物固定写成 markdown 落到工作区 docs/<主题>.md，结构：一句话结论 → 分点要点 → 原始出处（路径 / 链接）。
                4) 写完用 read_file 回读一遍，确认没有截断、没有残留占位符。
                5) 不确定的内容标注「待确认」，不要补造事实。
            """.trimIndent(),
            on = true
        ),
        Skill(
            id = "preset-log",
            name = "日志排错",
            desc = "应用崩溃 / 脚本报错，按证据定位而不是靠猜",
            body = """
                1) 先问清报错出现的场景与时间点；有日志文件就直接读，没有就先复现一次。
                2) 本应用自己的日志：exec_shell 跑 logcat -d -t 400 | grep -i '包名\|FATAL\|Exception'（普通模式只能看本应用部分）。
                3) 系统级日志、ANR、其他应用日志需要更高权限，拿不到就如实说明，别假装看过。
                4) 定位顺序：异常类名 + 首个 at 行（自己的代码）→ 入参与路径 → 复现条件。
                5) 给结论时写清「证据（哪行日志）+ 判断 + 下一步验证」，改动代码前先让用户确认。
            """.trimIndent(),
            on = true
        )
    ) //预置技能：装完就能用，内容都是本机可执行的流程

    private fun parse(raw: String?): List<Skill> {
        if (raw.isNullOrBlank()) return emptyList()
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val o = arr.optJSONObject(i) ?: JSONObject()
            Skill(
                id = o.optString("id"),
                name = o.optString("name"),
                desc = o.optString("desc"),
                body = o.optString("body"),
                on = o.optBoolean("on", true)
            )
        }
    } //从 JSON 还原

    private fun save() {
        val arr = JSONArray()
        cache.value.forEach { s ->
            arr.put(
                JSONObject()
                    .put("id", s.id)
                    .put("name", s.name)
                    .put("desc", s.desc)
                    .put("body", s.body)
                    .put("on", s.on)
            )
        }
        sp?.edit()?.putString("skills", arr.toString())?.apply()
        Store.write(FILE, arr.toString(2))
    } //整表落盘

    fun add(name: String, desc: String, body: String) {
        if (name.isBlank() && body.isBlank()) return
        cache.value = cache.value + Skill(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "未命名技能" },
            desc = desc.trim(),
            body = body.trim(),
            on = true
        )
        save()
    } //新增

    fun update(id: String, name: String, desc: String, body: String) {
        cache.value = cache.value.map { s ->
            if (s.id != id) s else s.copy(
                name = name.ifBlank { "未命名技能" },
                desc = desc.trim(),
                body = body.trim()
            )
        }
        save()
    } //改内容

    fun remove(id: String) {
        cache.value = cache.value.filterNot { it.id == id }
        save()
    } //删除

    fun toggle(id: String, on: Boolean) {
        cache.value = cache.value.map { if (it.id == id) it.copy(on = on) else it }
        save()
    } //开关

    /** 启用技能拼成提示片段；没有启用的技能返回空串 */
    fun prompt(): String {
        val on = enabled.filter { it.body.isNotBlank() }
        if (on.isEmpty()) return ""
        return buildString {
            append("可用技能（触发条件相符时按里面的步骤执行，参数不要凭空编）：\n")
            on.forEachIndexed { i, s ->
                append("${i + 1}) ${s.name}")
                if (s.desc.isNotBlank()) append("：${s.desc}")
                append("\n")
                append(s.body.take(2000))
                append("\n")
            }
        }.trim()
    } //拼系统提示
} //技能库
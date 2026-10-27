package b.ai.core

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** 工作流的一步：task 交给一个分智能体跑完再进下一步 */
data class FlowStep(
    val title: String,
    val task: String,
    val retry: Int = 1,
    val onFail: String = "stop"
) {
    /** 失败后重试几次（不含首次）：外面传脏数据一律夹到 0..3，免得配出无限重试 */
    val retries: Int get() = retry.coerceIn(0, 3)

    /** 这步挂了是跳过继续，还是整条流停住 */
    val skip: Boolean get() = onFail.equals("skip", true)
}

/** 一条工作流：步骤按顺序执行，可绑定时；lastAt / lastOk / lastNote 记最近一次跑的结果 */
data class Flow(
    val id: String,
    val name: String,
    val steps: List<FlowStep>,
    val rounds: Int = 6,
    val at: Long = 0L,
    val daily: Boolean = false,
    val auto: Boolean = true,
    val on: Boolean = true,
    val lastAt: Long = 0L,
    val lastOk: Boolean = true,
    val lastNote: String = ""
)

/** 工作流库：整表 JSON 存在 bai_cfg 的 flows 键，任何改动立刻落盘 */
object Flows {
    /** 步骤上限：一步一个模型会话，配太多跑一整晚也跑不完 */
    const val MAX_STEPS = 12

    private val cache = mutableStateOf<List<Flow>>(emptyList())
    private var sp: SharedPreferences? = null

    val list: List<Flow> get() = cache.value

    /** 参与调度的流：开关打开、有时间、且真有步骤 */
    val active: List<Flow>
        get() = cache.value.filter { it.on && it.at > 0L && it.steps.isNotEmpty() }

    fun init(c: Context) {
        if (sp != null) return
        val p = c.applicationContext.getSharedPreferences("bai_cfg", Context.MODE_PRIVATE)
        sp = p
        cache.value = runCatching { parse(p.getString("flows", "")) }.getOrDefault(emptyList())
    } //读盘

    private fun parse(raw: String?): List<Flow> {
        if (raw.isNullOrBlank()) return emptyList()
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val o = arr.optJSONObject(i) ?: JSONObject()
            Flow(
                id = o.optString("id"),
                name = o.optString("name"),
                steps = stepsFrom(o.optJSONArray("steps")),
                rounds = o.optInt("rounds", 6),
                at = o.optLong("at", 0L),
                daily = o.optBoolean("daily", false),
                auto = o.optBoolean("auto", true),
                on = o.optBoolean("on", true),
                lastAt = o.optLong("lastAt", 0L),
                lastOk = o.optBoolean("lastOk", true),
                lastNote = o.optString("lastNote")
            )
        }.filter { it.id.isNotBlank() && it.steps.isNotEmpty() }
    } //从 JSON 还原

    /** 步骤数组统一在这里解析：存盘读回和工具参数都走它，兼容纯字符串数组 */
    fun stepsFrom(arr: JSONArray?): List<FlowStep> {
        if (arr == null) return emptyList()
        val out = ArrayList<FlowStep>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i)
            if (o != null) {
                val task = o.optString("task").trim()
                if (task.isEmpty()) continue
                out.add(
                    FlowStep(
                        title = o.optString("title").trim().ifBlank { "步骤" + (out.size + 1) },
                        task = task,
                        retry = o.optInt("retry", 1),
                        onFail = o.optString("on_fail").trim().ifBlank { o.optString("onFail") }
                            .ifBlank { "stop" }
                    )
                )
            } else {
                val s = arr.optString(i).trim()
                if (s.isNotEmpty()) out.add(FlowStep("步骤" + (out.size + 1), s))
            }
        }
        return out.take(MAX_STEPS)
    } //步骤解析

    private fun save() {
        val arr = JSONArray()
        cache.value.forEach { f ->
            val steps = JSONArray()
            f.steps.forEach { s ->
                steps.put(
                    JSONObject().put("title", s.title).put("task", s.task)
                        .put("retry", s.retry).put("onFail", s.onFail)
                )
            }
            arr.put(
                JSONObject().put("id", f.id).put("name", f.name).put("steps", steps)
                    .put("rounds", f.rounds).put("at", f.at).put("daily", f.daily)
                    .put("auto", f.auto).put("on", f.on).put("lastAt", f.lastAt)
                    .put("lastOk", f.lastOk).put("lastNote", f.lastNote)
            )
        }
        sp?.edit()?.putString("flows", arr.toString())?.apply()
    } //整表落盘

    fun byId(id: String): Flow? = cache.value.firstOrNull { it.id == id }

    fun add(name: String, steps: List<FlowStep>, at: Long, daily: Boolean, auto: Boolean): Flow {
        val f = Flow(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "工作流" },
            steps = steps.take(MAX_STEPS),
            at = at,
            daily = daily,
            auto = auto,
            on = true
        )
        cache.value = cache.value + f
        save()
        return f
    } //新增

    fun update(f: Flow) {
        cache.value = cache.value.map { if (it.id == f.id) f.copy(steps = f.steps.take(MAX_STEPS)) else it }
        save()
    } //整条覆盖

    fun remove(id: String) {
        cache.value = cache.value.filterNot { it.id == id }
        save()
    } //删除

    fun toggle(id: String, on: Boolean) {
        cache.value = cache.value.map { if (it.id == id) it.copy(on = on) else it }
        save()
    } //开关

    fun touch(id: String, at: Long) {
        cache.value = cache.value.map { if (it.id == id) it.copy(at = at) else it }
        save()
    } //写回下次触发时间

    /** 跑完写回结果：面板和定时通知都靠这三个字段说「上次怎么样」 */
    fun mark(id: String, ok: Boolean, note: String) {
        if (id.isBlank()) return
        val now = System.currentTimeMillis()
        cache.value = cache.value.map {
            if (it.id == id) it.copy(lastAt = now, lastOk = ok, lastNote = note.take(200)) else it
        }
        save()
    } //记一次结果
} //工作流库

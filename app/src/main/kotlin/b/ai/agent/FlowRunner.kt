package b.ai.agent

import b.ai.core.Flow
import b.ai.core.FlowStep
import b.ai.core.Flows
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/** 一步的结果：tries 是实际跑了几次（含重试），skipped 是被前面失败连坐跳过的 */
data class StepOut(
    val title: String,
    val ok: Boolean,
    val text: String,
    val tries: Int,
    val skipped: Boolean = false
)

/** 整条流的结果：ok 只看有没有失败步骤（跳过的算过），text 是给模型/通知看的合并报告 */
data class FlowOut(val ok: Boolean, val text: String, val steps: List<StepOut>)

/**
 * 工作流执行引擎：一步一步串起来跑，每步复用一个分智能体（工具权限、确认弹窗、轮次上限全部跟随主智能体）。
 * 失败处理只有两条：retry 次内重试；重试用完按 onFail 决定停住还是跳过。
 */
object FlowRunner {
    /** 单步轮次上限：比一次性并行派活更克制，串行跑不容易在一步里绕不出来 */
    const val MAX_STEP_ROUNDS = 8

    /** 后台场景等确认的时长上限：到点自动跑 3 分钟，面板手动跑 9 分钟 */
    private const val CONFIRM_MS = 180_000L

    /** 定时触发用的独立协程域：广播接收器不能阻塞，跑完只写回结果与通知 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 主循环里同步跑（能被停止按钮取消）：onStatus 报进度，confirm 走主智能体的确认通道 */
    suspend fun run(
        flow: Flow,
        onStatus: (String) -> Unit,
        confirm: suspend (name: String, args: JSONObject, callId: String) -> Boolean
    ): FlowOut {
        val outs = ArrayList<StepOut>()
        val steps = flow.steps.take(Flows.MAX_STEPS)
        val total = steps.size
        var stop = false
        steps.forEachIndexed { i, s ->
            if (stop) {
                outs.add(StepOut(s.title, false, "前面有步骤失败且策略是「停住」，这一步没有执行。", 0, true))
                return@forEachIndexed
            }
            onStatus("工作流「${flow.name}」第 ${i + 1}/$total 步：" + s.title.take(16))
            val r = step(s, flow.rounds, onStatus, confirm)
            outs.add(r)
            if (!r.ok && !s.skip) stop = true
        }
        return render(flow, outs)
    } //顺序执行

    /** 定时到点：丢到自己的协程域跑，进度写通知，结果写回 Flows.mark */
    fun launch(flow: Flow) {
        scope.launch {
            val out = runCatching {
                run(
                    flow,
                    { s -> b.ai.exec.Alarms.notifyFlowText(flow, s) },
                    // 后台跑没人点确认：三分钟不回就当拒绝，否则整条流会一直挂在这里
                    { n, a, id -> withTimeoutOrNull(CONFIRM_MS) { Agent.confirmFromSub(n, a, id) } ?: false }
                )
            }.getOrElse { FlowOut(false, "工作流出错：" + (it.message ?: it.javaClass.simpleName), emptyList()) }
            runCatching { Flows.mark(flow.id, out.ok, out.text) }
            runCatching { b.ai.exec.Alarms.notifyFlowDone(flow, out.ok, out.text) }
        }
    } //定时触发

    /** 单步：失败按 retry 次数重试，间隔线性退避，别在模型连着抽风时打空转 */
    private suspend fun step(
        s: FlowStep,
        rounds: Int,
        onStatus: (String) -> Unit,
        confirm: suspend (name: String, args: JSONObject, callId: String) -> Boolean
    ): StepOut {
        var tries = 0
        var last: SubOut? = null
        val cap = rounds.coerceIn(1, MAX_STEP_ROUNDS)
        while (tries <= s.retries) {
            tries++
            val out = try {
                SubAgents.runOne(SubTask(s.title, s.task), cap, confirm)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                SubOut(s.title, false, "出错：" + (e.message ?: e.javaClass.simpleName), 0)
            }
            last = out
            if (out.ok) return StepOut(s.title, true, out.text, tries)
            if (tries <= s.retries) {
                onStatus("「${s.title.take(12)}」失败，第 $tries 次重试")
                delay(900L * tries)
            }
        }
        return StepOut(s.title, false, last?.text.orEmpty().ifBlank { "（没有拿到结论）" }, tries)
    } //单步带重试

    /** 合并成一份报告：头部一行总况，下面每步一节。给模型看也给通知看 */
    fun render(flow: Flow, outs: List<StepOut>): FlowOut {
        val failed = outs.count { !it.ok && !it.skipped }
        val skipped = outs.count { it.skipped }
        val ok = failed == 0
        val sb = StringBuilder()
        sb.append("工作流「").append(flow.name).append("」：共 ").append(outs.size).append(" 步")
        if (failed > 0) sb.append("，失败 ").append(failed).append(" 步")
        if (skipped > 0) sb.append("，跳过 ").append(skipped).append(" 步")
        if (ok) sb.append("，全部通过")
        sb.append('\n')
        outs.forEachIndexed { i, o ->
            sb.append("\n## ").append(i + 1).append(". ").append(o.title.ifBlank { "步骤" })
            if (o.skipped) sb.append("（跳过）") else if (!o.ok) sb.append("（失败）")
            if (o.tries > 1) sb.append("（试了 ").append(o.tries).append(" 次）")
            sb.append('\n').append(o.text.trim()).append('\n')
        }
        return FlowOut(ok, sb.toString().trim(), outs)
    } //合并报告

    /** 面板里手动点「跑一次」：同样丢到独立协程域，界面不卡 */
    fun launchManual(flow: Flow, done: (FlowOut) -> Unit) {
        scope.launch {
            val out = runCatching {
                run(
                    flow,
                    { _ -> },
                    // 面板里跑的也可能用户切走了：十分钟不回就当拒绝
                    { n, a, id -> withTimeoutOrNull(CONFIRM_MS * 3) { Agent.confirmFromSub(n, a, id) } ?: false }
                )
            }.getOrElse { FlowOut(false, "工作流出错：" + (it.message ?: it.javaClass.simpleName), emptyList()) }
            runCatching { Flows.mark(flow.id, out.ok, out.text) }
            withContext(Dispatchers.Main) { runCatching { done(out) } }
        }
    } //手动执行
} //工作流引擎

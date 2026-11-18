/**
 * 任务通知的数据结构：一次任务从开始到完成，全用这一个对象描述。
 * 厂商适配层只负责把这里的内容翻译成各自系统的字段，不改语义。
 */
package b.ai.device.sys.island

/** 通知所处阶段 */
enum class Stage {
    RUN, //任务进行中：常驻、不可划掉
    DONE //任务已完成：可在岛中再驻留一段时间
}

/**
 * 一条任务通知的完整描述。
 * @param title 任务名（会话标题），显示在岛 / 通知的标题行
 * @param text 正文：进行中放当前状态文案，完成放「任务已完成」
 * @param progress 0..100 的进度；-1 = 不确定进度（转圈 / 进度条 indeterminate）
 * @param stage 阶段
 * @param pinned 是否要求「不可划掉」：完成态要驻留时置真，否则 ColorOS / HyperOS
 *               会把这条通知当成可清的普通通知直接收走，表现就是「驻留没生效」
 */
data class IslandSpec(
    val title: String,
    val text: String,
    val progress: Int = -1,
    val stage: Stage = Stage.RUN,
    val pinned: Boolean = true
) {
    companion object {
        const val K_TITLE = "island_title"
        const val K_TEXT = "island_text"
        const val K_PROGRESS = "island_progress"
        const val K_RUN = "island_run"
        const val K_PINNED = "island_pinned"

        /** 从 Intent 还原：字段缺失时退回默认值，不抛异常 */
        fun from(
            title: String?,
            text: String?,
            progress: Int,
            run: Boolean,
            pinned: Boolean
        ): IslandSpec = IslandSpec(
            title = title.orEmpty(),
            text = text.orEmpty(),
            progress = progress,
            stage = if (run) Stage.RUN else Stage.DONE,
            pinned = pinned
        ) //还原
    }
} //任务通知描述
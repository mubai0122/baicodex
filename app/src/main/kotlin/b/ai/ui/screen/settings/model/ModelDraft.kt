/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27
 * Repository: https://github.com/mubai0122/Baicodex
 *
 * 说明：模型配置表单的「未保存」守卫。
 * 表单状态原本私藏在 ModelSetupPanel 内部，设置页返回时根本看不到它，
 * 用户填了一半直接返回就静悄悄丢了。这里把脏标记与确认动作提到一个单例上：
 * 表单负责上报是否有改动，设置页负责在返回时拦一下并弹确认。
 */

package b.ai.ui.screen.settings.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object ModelDraft {
    /** 表单里有没保存的改动；由 ModelSetupPanel 每次指纹变化时写入 */
    var dirty by mutableStateOf(false)

    /** 正在问「要不要丢弃修改」，设置页根层据此挂确认框 */
    var asking by mutableStateOf(false)

    /** 用户点「丢弃修改」后要执行的动作（返回上一层 / 退出设置） */
    private var then: (() -> Unit)? = null

    /**
     * 返回前的统一关卡：没有改动直接放行，有改动就先问一句。
     * @return true = 已经放行（调用方不用再做别的）
     */
    fun guard(proceed: () -> Unit): Boolean {
        if (!dirty) {
            proceed()
            return true
        }
        then = proceed
        asking = true
        return false
    } //返回拦截

    /** 确认丢弃：清标记并执行被拦下的那次返回 */
    fun discard() {
        dirty = false
        asking = false
        val act = then
        then = null
        act?.invoke()
    } //丢弃并继续

    /** 继续编辑：关掉确认框，什么都不做 */
    fun cancel() {
        asking = false
        then = null
    } //留下继续编辑

    /** 页面重建 / 退出设置时清干净，避免残留标记误拦下一次返回 */
    fun reset() {
        dirty = false
        asking = false
        then = null
    } //清空守卫
}

package b.ai.core.dev

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import b.ai.core.plugin.skill.Providers
import b.ai.net.api.llm.LlmClient
import b.ai.net.api.llm.text
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * 云端 API 模型连通性：拿一条配置自己的 base_url / Key / 模型名发最小请求，分两轮跑。
 * 第一轮文字：发「测试测试」，验证网络、鉴权、模型名与出字。
 * 第二轮图片：视觉输入开关开着时注入一张纯黑图，只有真答出「黑」才算视觉通道通。
 * 两轮结果分开带回去，界面上也分开写，不合并成一句「连通」。
 */
object ApiProbe {
    /** ok 表示这轮真拿到合格结果；ms 是这轮往返耗时；err 是给人看的中文原因 */
    data class R(val ok: Boolean, val ms: Long, val reply: String, val err: String)

    /** text 一定有；vision 为 null 表示这一轮没跑，原因在 skipped 里 */
    data class Out(val text: R, val vision: R?, val skipped: String)

    /** 纯黑图的 data URL：8×8 全黑 PNG 内联，几十字节，不落盘也不留内存 */
    private val BLACK: String by lazy {
        val bmp = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.BLACK)
        val bos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
        bmp.recycle()
        "data:image/png;base64," + Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    }

    fun test(providerId: String, baseUrl: String, apiKey: String, model: String, vision: Boolean): Out {
        //本地 GGUF 不走这条通道，别让人以为云端配置坏了
        val bad = when {
            providerId == Providers.LOCAL -> "这条是本地模型，测连通性去「本地模型」页"
            baseUrl.isBlank() -> "base_url 还没填"
            model.isBlank() -> "模型名还没填"
            else -> ""
        }
        if (bad.isNotEmpty()) return Out(R(false, 0, "", bad), null, "配置不全，两轮都没跑")
        //forceRemote：本机开着本地模型开关时，普通请求会被本地引擎接管，测试必须真发到云端
        val cli = LlmClient(baseUrl, apiKey, Providers.byId(providerId).auth, forceRemote = true)
        val text = runText(cli, model)
        //视觉输入关着就别发图片：发了有的服务端直接 400，测出来像是配置坏了
        if (!vision) return Out(text, null, "视觉输入开关是关的，跳过图片轮")
        return Out(text, runImage(cli, model), "")
    } //两轮连通性

    /** 第一轮：一句话，不下发工具定义，也不要思考 */
    private fun runText(cli: LlmClient, model: String): R {
        val t0 = System.currentTimeMillis()
        return try {
            val msgs = JSONArray().put(JSONObject().put("role", "user").put("content", "测试测试"))
            val msg = cli.chat(model, msgs, 0.0, think = false, tools = JSONArray())
            val ms = System.currentTimeMillis() - t0
            val body = msg.text("content").trim()
            if (body.isEmpty()) R(false, ms, "", "通了但没返回正文，可能内容都放进了思考字段")
            else R(true, ms, body.take(60), "")
        } catch (e: Exception) {
            R(false, System.currentTimeMillis() - t0, "", e.message ?: e.javaClass.simpleName)
        }
    } //文字轮

    /** 第二轮：注入纯黑图，模型答出「黑」才算视觉通道真的通 */
    private fun runImage(cli: LlmClient, model: String): R {
        val t0 = System.currentTimeMillis()
        return try {
            val parts = JSONArray()
                .put(JSONObject().put("type", "text").put("text", "这张图片是什么颜色？只回答颜色"))
                .put(
                    JSONObject().put("type", "image_url")
                        .put("image_url", JSONObject().put("url", BLACK))
                )
            val msgs = JSONArray().put(JSONObject().put("role", "user").put("content", parts))
            val msg = cli.chat(model, msgs, 0.0, think = false, tools = JSONArray())
            val ms = System.currentTimeMillis() - t0
            val body = msg.text("content").trim()
            when {
                body.isEmpty() -> R(false, ms, "", "有回包但没有正文")
                body.contains("黑") || body.contains("black", true) -> R(true, ms, body.take(60), "")
                else -> R(false, ms, body.take(60), "有回复但没认出纯黑，视觉通道可能没生效")
            }
        } catch (e: Exception) {
            R(false, System.currentTimeMillis() - t0, "", e.message ?: e.javaClass.simpleName)
        }
    } //图片轮
} //云端两轮连通性
package b.ai.native.llm

/** 本地模型推理：llama.cpp 句柄式封装，加载 / 建上下文 / 流式生成，回调跑在调用线程上 */
object Llama {
    private val loaded: Boolean by lazy { runCatching { System.loadLibrary("baicodex") }.isSuccess }

    /** 逐 token 回调，别在这里做重活，要更新 UI 自己切主线程 */
    fun interface OnToken {
        fun on(text: String)
    }

    private external fun nativeLoad(path: String, threads: Int, gpuLayers: Int): Long
    private external fun nativeFree(h: Long)
    private external fun nativeMeta(h: Long): String
    private external fun nativeErr(): String
    private external fun nativeCtxNew(h: Long, ctxLen: Int): Long
    private external fun nativeCtxFree(c: Long)
    private external fun nativeSetSampling(c: Long, temp: Float, topP: Float, topK: Int, repeatPenalty: Float, seed: Int)
    private external fun nativeRun(c: Long, prompt: String, maxTokens: Int, cb: OnToken?): Int
    private external fun nativeCancel(c: Long)
    private external fun nativeChat(h: Long, roles: Array<String>, texts: Array<String>, addAssistant: Boolean): String?

    /** 库没编进来（或 ABI 没带）时整个功能直接不可用，不要抛异常打断调用方 */
    fun available(): Boolean = loaded

    /** 最近一次失败原因，空串表示上次是成功的 */
    fun lastErr(): String = if (!loaded) "native 没加载" else runCatching { nativeErr() }.getOrDefault("")

    /** 加载 GGUF：threads<=0 用默认，gpuLayers=0 纯 CPU。失败返回 0 */
    fun load(path: String, threads: Int = 0, gpuLayers: Int = 0): Long =
        if (!loaded) 0L else runCatching { nativeLoad(path, threads, gpuLayers) }.getOrDefault(0L)

    fun free(h: Long) {
        if (loaded && h != 0L) runCatching { nativeFree(h) }
    } //释放模型：连带把它名下的上下文一起收掉

    /** 元信息拆成 map：desc / n_layer / n_ctx_train / n_params / size / chat / threads */
    fun meta(h: Long): Map<String, String> {
        if (!loaded || h == 0L) return emptyMap()
        val raw = runCatching { nativeMeta(h) }.getOrDefault("")
        return raw.lineSequence()
            .mapNotNull { line ->
                val i = line.indexOf('=')
                if (i <= 0) null else line.substring(0, i) to line.substring(i + 1)
            }
            .toMap()
    } //模型元信息

    /** 建推理上下文：ctxLen<=0 用模型训练上下文 */
    fun ctxNew(h: Long, ctxLen: Int = 0): Long =
        if (!loaded || h == 0L) 0L else runCatching { nativeCtxNew(h, ctxLen) }.getOrDefault(0L)

    fun ctxFree(c: Long) {
        if (loaded && c != 0L) runCatching { nativeCtxFree(c) }
    } //释放上下文

    fun setSampling(c: Long, temp: Float = 0.8f, topP: Float = 0.9f, topK: Int = 40, repeatPenalty: Float = 1.1f, seed: Int = -1) {
        if (loaded && c != 0L) runCatching { nativeSetSampling(c, temp, topP, topK, repeatPenalty, seed) }
    } //采样参数：temp<=0 贪心，seed<0 随机

    /** 跑一轮：返回 >=0 生成 token 数，-1 参数错，-2 提示超上下文，-3 取消，-4 推理失败 */
    fun run(c: Long, prompt: String, maxTokens: Int = 256, onToken: OnToken? = null): Int =
        if (!loaded || c == 0L) -1 else runCatching { nativeRun(c, prompt, maxTokens, onToken) }.getOrDefault(-4)

    fun cancel(c: Long) {
        if (loaded && c != 0L) runCatching { nativeCancel(c) }
    } //取消：下一 token 前生效

    /** 按模型自带 chat template 拼 prompt，模型没带模板时走 chatml */
    fun chat(h: Long, msgs: List<Pair<String, String>>, addAssistant: Boolean = true): String? {
        if (!loaded || h == 0L || msgs.isEmpty()) return null
        val roles = msgs.map { it.first }.toTypedArray()
        val texts = msgs.map { it.second }.toTypedArray()
        return runCatching { nativeChat(h, roles, texts, addAssistant) }.getOrNull()
    } //拼对话提示词
}
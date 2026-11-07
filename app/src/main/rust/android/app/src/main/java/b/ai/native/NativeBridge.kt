package b.ai.native

import android.util.Log
import androidx.annotation.Keep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 与 Rust 内核之间**唯一**的通道。
 *
 * ## 契约
 *
 * 输入一个 JSON 字符串，输出一个 JSON 字符串，永远合法：
 *
 * ```
 * {"ok":true,"data":...}
 * {"ok":false,"error":{"code":"auth","message":"...","retryable":false}}
 * ```
 *
 * 失败也走返回值，不抛异常。理由很实际：JNI 里抛 Java 异常要么丢栈、要么在
 * 跨线程时把进程搞崩；返回值 + 错误码能被 `runCatching` 与协程正常处理。
 *
 * ## 线程
 *
 * [call] 是**阻塞**的（内核在自己的 tokio 运行时上 `block_on`）。所以：
 *
 * - 一律走 [Dispatchers.IO]，绝不占主线程（否则一次长工具调用就 ANR）；
 * - 内核是进程级单例，本身串行；调用方不需要加锁。
 *
 * ## 事件
 *
 * 流式增量、工具开始/结束这些**不**从 [call] 返回，而是走 [events]：
 * 内核在一个守护线程里回调 [Sink.onEvent]，这里转成 Flow。界面订阅 Flow，
 * 不要自己开线程轮询。
 */
object NativeBridge {

    private const val TAG = "BaiNative"
    private const val LIB = "baicodex"

    /** 动态库加载结果。加载失败时 [ready] 为 false，[loadError] 说明原因。 */
    @Volatile
    private var loadError: String? = null

    private val _events = MutableSharedFlow<AppEvent>(
        replay = 0,
        extraBufferCapacity = 512,
        // 界面跟不上时丢最旧的：流式增量丢几条只影响观感，卡住整个内核才是事故。
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<AppEvent> = _events.asSharedFlow()

    private val _connected = MutableStateFlow(false)

    /** 内核是否已加载。设置页可以据此显示"内核未就绪"。 */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    init {
        try {
            System.loadLibrary(LIB)
            _connected.value = true
        } catch (t: Throwable) {
            loadError = t.message ?: t.javaClass.simpleName
            Log.e(TAG, "加载 $LIB 失败：$loadError", t)
        }
    }

    /** 加载失败的原因，成功时为 null。 */
    fun loadError(): String? = loadError

    /**
     * 调一次内核。
     *
     * 返回的一律是合法 JSON；调用方用 [Kernel] 里的类型化方法，不要直接解析。
     */
    suspend fun call(op: String, args: JSONObject = JSONObject()): JSONObject =
        withContext(Dispatchers.IO) { callBlocking(op, args) }

    /**
     * 同步版。只给"已经在后台线程"的场景用（例如 JNI 回调里、Worker 里）。
     * 主线程调它会 ANR——想在主线程用请用 suspend 版。
     */
    fun callBlocking(op: String, args: JSONObject = JSONObject()): JSONObject {
        val req = JSONObject().put("op", op).put("args", args).toString()
        val raw = if (_connected.value) {
            runCatching { nativeCall(req) }.getOrElse { e ->
                return failure("ffi", "内核调用失败：${e.message}", retryable = false)
            }
        } else {
            return failure("ffi", "内核未加载：${loadError ?: "未知原因"}", retryable = false)
        }
        return runCatching { JSONObject(raw) }.getOrElse {
            // 内核保证输出合法 JSON；真走到这里说明内存越界之类的严重问题。
            failure("ffi", "内核返回的不是 JSON：${raw.take(200)}", retryable = false)
        }
    }

    /** 启动内核。重复调用是幂等的。 */
    suspend fun boot(base: String): JSONObject {
        val v = call("boot", JSONObject().put("base", base))
        if (v.optBoolean("ok", false)) {
            registerSink()
            Kernel.onBooted(v.optJSONObject("data") ?: JSONObject())
        }
        return v
    }

    /** 注册事件回调。只注册一次，重复注册会推两遍。 */
    @Synchronized
    private fun registerSink() {
        if (sinkRegistered) return
        sinkRegistered = runCatching {
            nativeSetEventSink(Sink)
            true
        }.getOrElse {
            Log.e(TAG, "注册事件回调失败", it)
            false
        }
    }

    @Volatile
    private var sinkRegistered = false

    /**
     * 事件接收器。
     *
     * JNI 侧按名字调用 `onEvent(String)`，签名是 `(Ljava/lang/String;)V`，
     * 所以方法名与参数类型都不能改，且要 `@Keep` 防止混淆。
     */
    @Keep
    private object Sink {
        @Keep
        @JvmStatic
        fun onEvent(json: String) {
            val e = AppEvent.parse(json)
            if (e != null) {
                // tryEmit：缓冲区满时丢最旧的，绝不阻塞内核的事件线程。
                _events.tryEmit(e)
            }
        }
    }

    private fun failure(code: String, message: String, retryable: Boolean): JSONObject =
        JSONObject()
            .put("ok", false)
            .put(
                "error",
                JSONObject()
                    .put("code", code)
                    .put("message", message)
                    .put("retryable", retryable)
            )

    // —— JNI 导出（对应 Rust 的 ffi/jni.rs）——

    @JvmStatic
    private external fun nativeCall(req: String): String

    @JvmStatic
    private external fun nativeSetEventSink(sink: Any)

    /** 内核版本（编译期常量，不走 JNI，加载失败也能显示）。 */
    @JvmStatic
    external fun nativeVersion(): String
}

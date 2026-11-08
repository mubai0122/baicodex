package b.ai.core.cfg.app

import b.ai.BuildConfig

/**
 * 服务端根地址：值由构建期从 local.properties 的 baicodex.host 注入（BuildConfig.BAI_HOST），
 */
object Bai {
    /** 真地址：末尾不带斜杠 */
    val host: String = BuildConfig.BAI_HOST.trim().trimEnd('/').ifBlank { "https://invalid.local" }

    /** 是否配过了：界面需要区分「没配」与「连不上」时用 */
    val configured: Boolean get() = BuildConfig.BAI_HOST.isNotBlank()

    /** 拼一个接口地址，path 可带可不带前导斜杠 */
    fun api(path: String): String = host + if (path.startsWith("/")) path else "/$path"
} //服务端根地址

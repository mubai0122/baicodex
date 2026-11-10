package b.ai.core.cfg.seed

import b.ai.BuildConfig
import b.ai.core.cfg.app.Cfg
import b.ai.net.lan.LanCfg

//种子注入：local.properties → app/build.gradle.kts 的 SEEDS 索引 → BuildConfig；仓库里不留密钥
object Seeds {
    //网易云 weapi 的固定参数：这几项本来就是这个客户端公开口径，没注入时按它跑，注入就是私有替换
    private const val NT_PRESET = "0CoJUm6Qyw8W8jud"
    private const val NT_IV = "0102030405060708"
    private const val NT_RSA_E = "010001"
    private const val NT_RSA_N =
        "00e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b725152b3ab17a876aea8a5aa76d2e" +
            "417629ec4ee341f56135fccf695280104e0312ecbda92557c93870114af6c9d05c4f7f0c3685b7a46bee255" +
            "932575cce10b424d813cfe4875d3e82047b97ddef52741d546b8e289dc6935b3ece0462db0a22b8e7"

    private fun pick(v: String, def: String): String = v.trim().ifEmpty { def }

    fun neteasePreset(): String = pick(BuildConfig.SEED_NETEASE_PRESET, NT_PRESET)

    fun neteaseIv(): String = pick(BuildConfig.SEED_NETEASE_IV, NT_IV)

    fun neteaseRsaE(): String = pick(BuildConfig.SEED_NETEASE_RSA_E, NT_RSA_E)

    fun neteaseRsaN(): String = pick(BuildConfig.SEED_NETEASE_RSA_N, NT_RSA_N)

    /** 团队协作默认 token：没注入就是空，空表示不校验（只建议在可信局域网） */
    fun a2aToken(): String = BuildConfig.SEED_A2A_TOKEN.trim()

    /** 固定配对码：两台设备注入同一个码，省得每次手输 */
    fun lanCode(): String = BuildConfig.SEED_LAN_CODE.trim()

    fun lanPort(): Int = BuildConfig.SEED_LAN_PORT.trim().toIntOrNull() ?: 0

    fun teamPort(): Int = BuildConfig.SEED_TEAM_PORT.trim().toIntOrNull() ?: 0

    /** 把编译期种子灌进配置：只填空位与默认值，用户改过的一律不动 */
    fun apply() {
        val tok = a2aToken()
        if (tok.isNotEmpty() && Cfg.a2aToken.isEmpty()) Cfg.a2aToken = tok
        val code = lanCode()
        if (code.length == 6 && code.all { it.isDigit() }) LanCfg.applySeedCode(code)
        LanCfg.applySeedPort(lanPort())
        val tp = teamPort()
        if (tp in 1024..65535 && Cfg.a2aPort == 8720) Cfg.a2aPort = tp
    } //幂等：种子只在配置还空着/还是默认值时补进去

    /** 一句话说明注入了哪些项，给设置页与自检用 */
    fun report(): String {
        val on = ArrayList<String>()
        if (BuildConfig.SEED_NETEASE_PRESET.isNotBlank()) on.add("网易云预设key")
        if (BuildConfig.SEED_NETEASE_RSA_N.isNotBlank()) on.add("网易云RSA模数")
        if (a2aToken().isNotEmpty()) on.add("协作token")
        if (lanCode().isNotEmpty()) on.add("配对码")
        if (lanPort() in 1024..65535) on.add("发现端口")
        if (teamPort() in 1024..65535) on.add("接活端口")
        return if (on.isEmpty()) "没有注入任何种子（local.properties 里 seed.* 全空）" else "已注入：" + on.joinToString("、")
    }
} //种子注入
import java.util.Properties

val seedProps: Properties = Properties().also { p ->
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { p.load(it) }
} //读种子

val seedCredOn: Boolean = rootProject.findProperty("allowSeedCred")?.toString() == "1"

val seedFn: (String, String) -> String = { key, def ->
    if (!seedCredOn && (key == "seed.a2a.token" || key == "seed.lan.code")) def
    else seedProps.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() } ?: def
} //按需把协作凭据挡在门外

val seedIndex: List<List<String>> = listOf(
    listOf("SEED_NETEASE_PRESET", "seed.netease.preset"),
    listOf("SEED_NETEASE_IV", "seed.netease.iv"),
    listOf("SEED_NETEASE_RSA_N", "seed.netease.rsa.n"),
    listOf("SEED_NETEASE_RSA_E", "seed.netease.rsa.e"),
    listOf("SEED_A2A_TOKEN", "seed.a2a.token"),
    listOf("SEED_LAN_CODE", "seed.lan.code"),
    listOf("SEED_LAN_PORT", "seed.lan.port"),
    listOf("SEED_TEAM_PORT", "seed.team.port")
) //种子表

val seedLiteralFn: (String, String) -> String = { key, def ->
    val v = seedFn(key, def).replace("\\", "\\\\").replace("\"", "\\\"")
    "\"$v\""
}

rootProject.extensions.extraProperties.set("seedFn", seedFn)
rootProject.extensions.extraProperties.set("seedIndex", seedIndex)
rootProject.extensions.extraProperties.set("seedLiteralFn", seedLiteralFn)
import java.util.Properties

val seedProps: Properties = Properties().also { p ->
    val f = rootProject.file("local.properties")
    if (f.exists()) f.reader(Charsets.UTF_8).use { p.load(it) }
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
    listOf("SEED_TEAM_PORT", "seed.team.port"),
    listOf("SEED_PROMPT_MODE_PLAN", "seed.prompt.mode.plan"),
    listOf("SEED_PROMPT_MODE_STANDARD", "seed.prompt.mode.standard"),
    listOf("SEED_PROMPT_MODE_CODE", "seed.prompt.mode.code"),
    listOf("SEED_PROMPT_MODE_MINIMAL", "seed.prompt.mode.minimal"),
    listOf("SEED_PROMPT_MODE_CREATOR", "seed.prompt.mode.creator"),
    listOf("SEED_PROMPT_RULES_BOUND", "seed.prompt.rules.bound"),
    listOf("SEED_PROMPT_RULES_ABS", "seed.prompt.rules.abs"),
    listOf("SEED_PROMPT_RULE_MASK", "seed.prompt.rule.mask"),
    listOf("SEED_PROMPT_CARE", "seed.prompt.care"),
    listOf("SEED_PROMPT_SEARCH_ON", "seed.prompt.search.on"),
    listOf("SEED_PROMPT_SEARCH_OFF", "seed.prompt.search.off"),
    listOf("SEED_PROMPT_NO_TOOL", "seed.prompt.no.tool"),
    listOf("SEED_PROMPT_NO_VISION", "seed.prompt.no.vision"),
    listOf("SEED_PROMPT_WS_NONE", "seed.prompt.ws.none"),
    listOf("SEED_PROMPT_WS_BOUND", "seed.prompt.ws.bound"),
    listOf("SEED_PROMPT_TOOL_HINT", "seed.prompt.tool.hint"),
    listOf("SEED_PROMPT_SCREEN_STEP", "seed.prompt.screen.step"),
    listOf("SEED_PROMPT_SUMMARY", "seed.prompt.summary"),
    listOf("SEED_PROMPT_TITLE", "seed.prompt.title"),
    listOf("SEED_PROMPT_OPTIMIZER", "seed.prompt.optimizer"),
    listOf("SEED_PROMPT_MEM_ORGANIZER", "seed.prompt.mem.organizer")
) //种子表

val seedLiteralFn: (String, String) -> String = { key, def ->
    val v = seedFn(key, def)
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\r", "")
        .replace("\n", "\\n")
        .replace("\t", "\\t")
    "\"$v\""
} //字符串字面量（含换行/制表转义）

rootProject.extensions.extraProperties.set("seedFn", seedFn)
rootProject.extensions.extraProperties.set("seedIndex", seedIndex)
rootProject.extensions.extraProperties.set("seedLiteralFn", seedLiteralFn)
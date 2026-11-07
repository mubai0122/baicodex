import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    //backdrop-desktop 2.0.0 是 Java 21 字节码，工具链不能低于 21
    jvmToolchain(21)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    //液态玻璃：和移动端同一个库，桌面变体带 SkikoRuntimeShader
    //版本必须 2.0.1：2.0.0 调的是 skiko 老签名 makeShader(Matrix33)，在 Compose 1.12.2 的 skiko 0.150.2 上直接 NoSuchMethodError
    implementation("io.github.kyant0:backdrop-desktop:2.0.1")
}

//交叉出 Windows 包用的 skiko：把宿主平台那份换成 Windows x64 的 dll
//版本必须跟 Compose 带上来的 skiko 一致，升级 Compose 要回来改
val winSkiko: Configuration by configurations.creating

dependencies {
    winSkiko("org.jetbrains.skiko:skiko-awt-runtime-windows-x64:0.150.2")
}

//打一个 Windows 能跑的胖 jar：应用类 + 全部依赖 + Windows 版 skiko
tasks.register<Jar>("winFatJar") {
    archiveFileName.set("baicodex.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest { attributes["Main-Class"] = "b.ai.desktop.MainKt" }
    from(sourceSets.main.get().output)
    from({
        val deps = configurations.runtimeClasspath.get()
            .filterNot { it.name.contains("skiko-awt-runtime-linux") }
        (deps + winSkiko).map { if (it.isDirectory) it else zipTree(it) }
    }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    }
} //Windows 胖 jar

//离屏渲染截图：不开窗口、不用 X11，产物默认 build/ui.png
tasks.register<JavaExec>("shot") {
    group = "verification"
    description = "把界面离屏渲染成 PNG"
    mainClass.set("b.ai.desktop.ScreenshotKt")
    classpath = sourceSets["main"].runtimeClasspath
    systemProperty("java.awt.headless", "true")
    //必须用 21 起进程：backdrop-desktop 是 class 65，JVM 17 会在加载类时直接炸
    javaLauncher.set(
        javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) }
    )
    val out = (findProperty("shotOut") as String?)
        ?: layout.buildDirectory.file("ui.png").get().asFile.absolutePath
    args(out)
} //截图任务

//Windows 的 exe/msi 只能在 Windows 上打；这台设备仅验证编译
compose.desktop {
    application {
        mainClass = "b.ai.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "baicodex"
            packageVersion = "2.0.0"
            description = "BaiCodex Desktop"
            vendor = "mubai0122"
        }
    }
}
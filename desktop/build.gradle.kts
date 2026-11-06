import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
}

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
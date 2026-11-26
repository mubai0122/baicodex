plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
    id("org.jetbrains.compose") version "1.9.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.0"
}

group = "b.ai"
version = "0.0.1"

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
}

compose.desktop {
    application {
        mainClass = "b.ai.desktop.MainKt"
        nativeDistributions {
            packageName = "Baicodex"
            packageVersion = "0.0.1"
            description = "Baicodex 桌面端 · 本地 Agent 工作台"
            vendor = "沐白/Baix"
            copyright = "© 2026 沐白/Baix · AGPL-3.0"
            windows {
                menuGroup = "Baicodex"
                shortcut = true
                perUserInstall = true
                dirChooser = true
            }
        }
    }
}

kotlin {
    jvmToolchain(21)
}

tasks.named<Jar>("jar") {
    manifest {
        attributes(
            "Class-Path" to configurations.runtimeClasspath.get()
                .map { it.name }
                .distinct()
                .joinToString(" "),
        )
    }
}
tasks.register("collectAppJars") {
    group = "distribution"
    description = "收集主 jar 与运行时依赖，供 jpackage 打 app-image"
    dependsOn(tasks.named("jar"))
    doLast {
        val out = layout.buildDirectory.dir("pkg/input").get().asFile
        out.deleteRecursively()
        out.mkdirs()

        val mainJar = tasks.named<Jar>("jar").get().archiveFile.get().asFile
        mainJar.copyTo(File(out, mainJar.name), overwrite = true)

        configurations.runtimeClasspath.get().files
            .filter { it.isFile && it.name.endsWith(".jar") }
            .groupBy { it.name }
            .forEach { (name, candidates) ->
                candidates.maxByOrNull { it.length() }?.copyTo(File(out, name), overwrite = true)
            }
    }
}

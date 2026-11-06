// desktop 是独立 Gradle 构建：不进主 settings，Android 构建不会去解析 Compose MP 插件
// 用法（在仓库根）: sh ./gradlew -p desktop compileKotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "baicodex-desktop"
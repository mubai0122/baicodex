pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        //StringFog 插件只发在 JitPack，下面用 useModule 重定向到它的模块坐标
        maven("https://jitpack.io")
    }
    resolutionStrategy {
        eachPlugin {
            //com.va.stringfog 不在 Gradle 插件门户，不重定向会直接解析失败
            if (requested.id.id == "com.va.stringfog") {
                useModule("com.github.Pangu-Immortal.StringFogPro:stringfog-gradle-plugin:${requested.version}")
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "BaiAgent"

include(":app")
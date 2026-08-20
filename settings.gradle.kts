pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
// 注意：已移除 org.gradle.toolchains.foojay-resolver-convention 1.0.0
// 该 2023 年老插件与 Gradle 8.14.5 不兼容，Sync 早期报 "index == 7"。
// 本项目无需自动下载工具链 JDK（使用 Android Studio 自带 JBR 21）。
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
    }
}

rootProject.name = "DouyinJieXi"
include(":app")

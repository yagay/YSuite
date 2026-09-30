pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

sourceControl {
    gitRepository(uri("https://github.com/yagay/YSuite.git")) {
        producesModule("com.github.yagay.YSuite:api")
        producesModule("com.github.yagay.YSuite:ui")
    }
}

rootProject.name = "YMiniGuard"
include(":app", ":feature")

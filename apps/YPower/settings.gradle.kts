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
        maven { url = uri("https://jitpack.io") }
    }
}

sourceControl {
    gitRepository(uri("https://github.com/yagay/YSuite.git")) {
        producesModule("com.github.yagay.YSuite:ui")
    }
}

rootProject.name = "YPower"
include(":app", ":feature")

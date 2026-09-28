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

rootProject.name = "YSuite"
include(":core", ":suite")
include(":ydiag-feature")
project(":ydiag-feature").projectDir = file("features/YDiag/feature")
include(":ynotify-feature")
project(":ynotify-feature").projectDir = file("features/YNotify/feature")
include(":ypower-feature")
project(":ypower-feature").projectDir = file("features/YPower/feature")
include(":yminiguard-feature")
project(":yminiguard-feature").projectDir = file("features/YMiniGuard/feature")
include(":listcleaner-feature")
project(":listcleaner-feature").projectDir = file("features/ListCleaner/feature")

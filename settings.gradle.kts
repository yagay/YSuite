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

// YUI has to be usable in three environments with the same source:
// 1) normal YSuite checkout with all feature submodules -> build the complete host;
// 2) JitPack publication -> configure only :ui;
// 3) Gradle Git source dependency from a standalone feature repo -> Git checks out YSuite without
//    recursive feature submodules, so automatically configure only :ui.
val jitpackBuild = System.getenv("JITPACK")?.equals("true", ignoreCase = true) == true
val featureSourcesPresent = file("features/YDiag/feature").isDirectory &&
    file("features/YEntryCleaner/feature").isDirectory &&
    file("features/YTaskManager/feature").isDirectory
val uiOnlyBuild = jitpackBuild || !featureSourcesPresent

include(":ui")

if (!uiOnlyBuild) {
    include(":core", ":suite")

    include(":ydiag-feature")
    project(":ydiag-feature").projectDir = file("features/YDiag/feature")
    include(":ynotify-feature")
    project(":ynotify-feature").projectDir = file("features/YNotify/feature")
    include(":ypower-feature")
    project(":ypower-feature").projectDir = file("features/YPower/feature")
    include(":yminiguard-feature")
    project(":yminiguard-feature").projectDir = file("features/YMiniGuard/feature")
    include(":yentrycleaner-feature")
    project(":yentrycleaner-feature").projectDir = file("features/YEntryCleaner/feature")
    include(":ynfc-feature")
    project(":ynfc-feature").projectDir = file("features/YNFC/feature")
    include(":ytask-feature")
    project(":ytask-feature").projectDir = file("features/YTaskManager/feature")
    include(":yparam-feature")
    project(":yparam-feature").projectDir = file("features/YParam/feature")
    include(":yfloat-feature")
    project(":yfloat-feature").projectDir = file("features/YFloat/feature")
    include(":yfloat-ppocr-sdk")
    project(":yfloat-ppocr-sdk").projectDir = file("features/YFloat/ppocr-sdk")
}

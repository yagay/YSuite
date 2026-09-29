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

rootProject.name = "YSuite"

// YUI has one source of truth and two build modes:
// 1) a normal YSuite checkout with initialized feature submodules builds the complete host;
// 2) a standalone feature resolves YSuite through Gradle sourceControl. Git leaves submodule
//    directories present but uninitialized, so check real build files rather than directories.
val requiredFeatureBuildFiles = listOf(
    "features/YDiag/feature/build.gradle.kts",
    "features/YNotify/feature/build.gradle.kts",
    "features/YPower/feature/build.gradle.kts",
    "features/YMiniGuard/feature/build.gradle.kts",
    "features/YEntryCleaner/feature/build.gradle.kts",
    "features/YNFC/feature/build.gradle.kts",
    "features/YTaskManager/feature/build.gradle.kts",
    "features/YParam/feature/build.gradle.kts",
    "features/YFloat/feature/build.gradle",
    "features/YFloat/ppocr-sdk/build.gradle",
)
val featureSourcesPresent = requiredFeatureBuildFiles.all { file(it).isFile }

include(":ui")

if (featureSourcesPresent) {
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

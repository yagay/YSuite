pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}

val suiteRoot = file("../..").canonicalFile
val localApi = suiteRoot.resolve("libs/yapi")
val localUi = suiteRoot.resolve("libs/yui")
if (localApi.isDirectory && localUi.isDirectory) {
    include(":ysuite-api", ":ysuite-ui")
    project(":ysuite-api").projectDir = localApi
    project(":ysuite-ui").projectDir = localUi
} else {
    sourceControl {
        gitRepository(uri("https://github.com/yagay/YSuite.git")) {
            producesModule("com.github.yagay.YSuite:api")
            producesModule("com.github.yagay.YSuite:ui")
        }
    }
}

rootProject.name = "YDiag"
include(":app", ":feature")

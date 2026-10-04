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

rootProject.name = "YSuiteNext"

include(":app")
include(":core:common")
include(":core:model")
include(":core:platform")
include(":core:resources")
include(":core:designsystem")
include(":core:ui")
include(":core:navigation")

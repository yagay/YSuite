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
include(":core:resources")
include(":core:designsystem")
include(":core:ui")
include(":core:navigation")
include(":core:presentation")
include(":core:settings")
include(":core:logging")
include(":core:permissions")
include(":core:diagnostics")
include(":core:platform:api")
include(":core:platform:android")

include(":feature:template:api")
include(":feature:template:impl")
include(":feature:settings:api")
include(":feature:settings:impl")

include(":host:standalone")

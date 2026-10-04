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
include(":core:productui")
include(":core:ui")
include(":core:navigation")
include(":core:presentation")
include(":core:runtime")
include(":core:settings")
include(":core:diagnostics")

include(":core:logging:api")
include(":core:logging:android")
include(":core:permissions:api")
include(":core:permissions:android")
include(":core:platform:api")
include(":core:platform:android")

include(":feature:template:api")
include(":feature:template:impl")
include(":feature:settings:api")
include(":feature:settings:impl")
include(":feature:system:api")
include(":feature:system:impl")

include(":feature:yfiles:api")
include(":feature:yfiles:impl")

include(":host:standalone")

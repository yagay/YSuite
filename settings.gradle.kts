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
        maven(url = "https://jitpack.io")
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
include(":feature:ydownload:api")
include(":feature:ydownload:impl")
include(":feature:ytaskmanager:api")
include(":feature:ytaskmanager:impl")
include(":feature:yparam:api")
include(":feature:yparam:impl")
include(":feature:yparam:runtime")
include(":feature:ydiag:api")
include(":feature:ydiag:impl")
include(":feature:ydiag:runtime")
include(":feature:ypower:api")
include(":feature:ypower:impl")
include(":feature:ynotify:api")
include(":feature:ynotify:runtime")
include(":feature:ynotify:impl")
include(":feature:ynfc:api")
include(":feature:ynfc:runtime")
include(":feature:ynfc:impl")
include(":feature:yminiguard:api")
include(":feature:yminiguard:runtime")
include(":feature:yminiguard:impl")
include(":feature:yentrycleaner:api")
include(":feature:yentrycleaner:runtime")
include(":feature:yentrycleaner:impl")
include(":feature:yfloat:ppocr")
include(":feature:yfloat:runtime")
include(":feature:yfloat:api")
include(":feature:yfloat:impl")

include(":host:standalone")

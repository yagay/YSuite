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

include(":ui")
project(":ui").projectDir = file("libs/yui")

include(":core")
project(":core").projectDir = file("libs/ycore")

include(":suite")
project(":suite").projectDir = file("suite/YSuite")

include(":ydiag-feature")
project(":ydiag-feature").projectDir = file("apps/YDiag/feature")

include(":ynotify-feature")
project(":ynotify-feature").projectDir = file("apps/YNotify/feature")

include(":ypower-feature")
project(":ypower-feature").projectDir = file("apps/YPower/feature")

include(":yminiguard-feature")
project(":yminiguard-feature").projectDir = file("apps/YMiniGuard/feature")

include(":yentrycleaner-feature")
project(":yentrycleaner-feature").projectDir = file("apps/YEntryCleaner/feature")

include(":ynfc-feature")
project(":ynfc-feature").projectDir = file("apps/YNFC/feature")

include(":ytask-feature")
project(":ytask-feature").projectDir = file("apps/YTaskManager/feature")

include(":yparam-feature")
project(":yparam-feature").projectDir = file("apps/YParam/feature")

include(":yfloat-feature")
project(":yfloat-feature").projectDir = file("apps/YFloat/feature")

include(":yfloat-ppocr-sdk")
project(":yfloat-ppocr-sdk").projectDir = file("apps/YFloat/ppocr-sdk")

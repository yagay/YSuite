plugins {
    id("com.android.application") version "9.2.0" apply false
    id("com.android.library") version "9.2.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.20" apply false
}

// Standalone feature repositories resolve com.github.yagay.YSuite:ui directly from YSuite/main
// through Gradle sourceControl. When those same feature modules are embedded in YSuite, always
// substitute that module dependency with this build's local :ui project so the APK contains one
// shared set of YUI classes/resources and never configures a nested source checkout.
subprojects {
    configurations.configureEach {
        resolutionStrategy.dependencySubstitution {
            substitute(module("com.github.yagay.YSuite:ui"))
                .using(project(":ui"))
        }
    }
}

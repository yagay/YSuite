plugins {
    id("com.android.application") version "9.2.0" apply false
    id("com.android.library") version "9.2.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.20" apply false
}

// Standalone feature repositories resolve shared YSuite modules directly from YSuite/main through
// Gradle sourceControl. When those same feature modules are embedded in YSuite, always substitute
// the published coordinates with this build's local projects so one set of API/UI classes is used
// and no nested source checkout is configured.
subprojects {
    configurations.configureEach {
        resolutionStrategy.dependencySubstitution {
            substitute(module("com.github.yagay.YSuite:api"))
                .using(project(":api"))
            substitute(module("com.github.yagay.YSuite:ui"))
                .using(project(":ui"))
        }
    }
}

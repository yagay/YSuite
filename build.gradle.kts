plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    id("io.github.takahirom.roborazzi") version "1.75.0" apply false
}

// Standalone feature repositories resolve shared YSuite modules directly from YSuite through
// Gradle sourceControl. When those same feature modules are embedded in YSuite, always substitute
// the published coordinates with this build's local projects so one set of API/UI classes is used.
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

// Convenience entry points for the generic standalone composer.
// Example: ./gradlew buildFeatureDebug -PySuiteStandaloneFeature=yfiles
tasks.register("buildFeatureDebug") {
    group = "build"
    description = "Build one Feature as a standalone debug APK using -PySuiteStandaloneFeature=<id>."
    dependsOn(":standalone:packageFeatureDebug")
}

tasks.register("buildFeatureRelease") {
    group = "build"
    description = "Build one Feature as a standalone release APK using -PySuiteStandaloneFeature=<id>."
    dependsOn(":standalone:packageFeatureRelease")
}

plugins {
    id("com.android.application") version "9.2.0" apply false
    id("com.android.library") version "9.2.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.20" apply false
}

// Standalone feature repositories depend on com.github.yagay:YSuite:main-SNAPSHOT.
// When those same feature modules are embedded here, always substitute the remote artifact with
// this build's local :ui project so there is one copy of the classes/resources in the APK.
subprojects {
    configurations.configureEach {
        resolutionStrategy.dependencySubstitution {
            substitute(module("com.github.yagay:YSuite"))
                .using(project(":ui"))
        }
    }
}

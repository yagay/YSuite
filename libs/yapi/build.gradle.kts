plugins {
    id("com.android.library")
}

group = "com.github.yagay.YSuite"

val sharedJavaVersion = JavaVersion.toVersion(libs.versions.java.get())

android {
    namespace = "com.yagay.suite.api"
    compileSdk = libs.versions.compileSdk.get().toInt()

    // The shared host contract intentionally stays API-26 compatible so lower-minSdk
    // standalone shells (currently YTaskManager) can depend on it without manifest overrides.
    defaultConfig { minSdk = libs.versions.minSdkLegacy.get().toInt() }

    compileOptions {
        sourceCompatibility = sharedJavaVersion
        targetCompatibility = sharedJavaVersion
    }
}

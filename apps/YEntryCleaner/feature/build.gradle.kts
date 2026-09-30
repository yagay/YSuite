plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val standaloneVersionCode = 43
val runtimeVersionCode = providers.gradleProperty("ySuiteHostVersionCode")
    .orNull?.toIntOrNull() ?: standaloneVersionCode

android {
    namespace = "com.yagay.YEntryCleaner"
    compileSdk {
        version = release(37) { minorApiLevel = 0 }
    }

    defaultConfig {
        minSdk = 31
        buildConfigField("int", "VERSION_CODE", runtimeVersionCode.toString())
        buildConfigField("String", "VERSION_NAME", "\"1.6.18\"")
        buildConfigField("long", "HOOK_COMPAT_VERSION_CODE", "43L")
    }

    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("com.github.yagay.YSuite:ui") {
        version { branch = "main" }
    }
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation("io.github.libxposed:service:102.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    testImplementation("junit:junit:4.13.2")
}

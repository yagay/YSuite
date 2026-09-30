plugins {
    id("com.android.library")
}

val standaloneVersionCode = 84L
val runtimeVersionCode = providers.gradleProperty("ySuiteHostVersionCode")
    .orNull?.toLongOrNull() ?: standaloneVersionCode

android {
    namespace = "com.yagay.YMiniGuard"
    compileSdk = 37

    defaultConfig {
        minSdk = 31
        buildConfigField("long", "VERSION_CODE", "${runtimeVersionCode}L")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("com.github.yagay.YSuite:api") {
        version { branch = "main" }
    }
    implementation("com.github.yagay.YSuite:ui") {
        version { branch = "main" }
    }
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation("io.github.libxposed:service:102.0.0")
}

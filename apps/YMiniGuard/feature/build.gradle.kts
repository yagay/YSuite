plugins {
    id("com.android.library")
}

val standaloneVersionCode = 84L
val runtimeVersionCode = providers.gradleProperty("ySuiteHostVersionCode")
    .orNull?.toLongOrNull() ?: standaloneVersionCode
val sharedSuiteBranch = providers.gradleProperty("ySuiteSharedBranch").orElse("main")
val sharedJavaVersion = JavaVersion.toVersion(libs.versions.java.get())

android {
    namespace = "com.yagay.YMiniGuard"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        buildConfigField("long", "VERSION_CODE", "${runtimeVersionCode}L")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = sharedJavaVersion
        targetCompatibility = sharedJavaVersion
    }
}

dependencies {
    implementation("com.github.yagay.YSuite:api") {
        version { branch = sharedSuiteBranch.get() }
    }
    implementation("com.github.yagay.YSuite:ui") {
        version { branch = sharedSuiteBranch.get() }
    }
    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)
}

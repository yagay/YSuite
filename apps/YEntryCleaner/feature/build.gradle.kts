plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val embeddedInSuite = rootProject.findProject(":suite") != null
val hostPackage = if (embeddedInSuite) "com.yagay.YSuite" else "com.yagay.YEntryCleaner"
val standaloneVersionCode = 43
val runtimeVersionCode = providers.gradleProperty("ySuiteHostVersionCode")
    .orNull?.toIntOrNull() ?: standaloneVersionCode
val sharedJavaVersion = JavaVersion.toVersion(libs.versions.java.get())

android {
    namespace = "com.yagay.YEntryCleaner"
    compileSdk {
        version = release(libs.versions.compileSdk.get().toInt()) {
            minorApiLevel = libs.versions.compileSdkMinor.get().toInt()
        }
    }

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        buildConfigField("int", "VERSION_CODE", runtimeVersionCode.toString())
        buildConfigField("String", "VERSION_NAME", "\"1.6.18\"")
        buildConfigField("long", "HOOK_COMPAT_VERSION_CODE", "43L")
        buildConfigField("String", "HOST_PACKAGE", "\"$hostPackage\"")
    }

    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = sharedJavaVersion
        targetCompatibility = sharedJavaVersion
    }
}

dependencies {
    implementation(project(":api"))
    implementation(project(":ui"))
    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}

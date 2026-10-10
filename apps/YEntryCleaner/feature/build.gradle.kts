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
val sharedSuiteBranch = providers.gradleProperty("ySuiteSharedBranch").orElse("main")
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
    if (rootProject.findProject(":api") != null) {
        implementation(project(":api"))
    } else if (rootProject.findProject(":ysuite-api") != null) {
        implementation(project(":ysuite-api"))
    } else {
        implementation("com.github.yagay.YSuite:api") {
            version { branch = sharedSuiteBranch.get() }
        }
    }
    if (rootProject.findProject(":ui") != null) {
        implementation(project(":ui"))
    } else if (rootProject.findProject(":ysuite-ui") != null) {
        implementation(project(":ysuite-ui"))
    } else {
        implementation("com.github.yagay.YSuite:ui") {
            version { branch = sharedSuiteBranch.get() }
        }
    }
    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}

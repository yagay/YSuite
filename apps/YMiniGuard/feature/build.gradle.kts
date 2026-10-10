plugins {
    id("com.android.library")
}

val embeddedInSuite = rootProject.findProject(":suite") != null
val hostPackage = if (embeddedInSuite) "com.yagay.YSuite" else "com.yagay.YMiniGuard"
val standaloneVersionCode = 84L
val runtimeVersionCode = providers.gradleProperty("ySuiteHostVersionCode")
    .orNull?.toLongOrNull() ?: standaloneVersionCode
val sharedJavaVersion = JavaVersion.toVersion(libs.versions.java.get())

android {
    namespace = "com.yagay.YMiniGuard"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        buildConfigField("long", "VERSION_CODE", "${runtimeVersionCode}L")
        buildConfigField("String", "HOST_PACKAGE", "\"$hostPackage\"")
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
    implementation(project(":api"))
    implementation(project(":ui"))
    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)
}

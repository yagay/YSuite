plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.yagay.ysuite"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.yagay.ysuite"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:settings"))
    implementation(project(":core:logging:api"))
    implementation(project(":core:logging:android"))
    implementation(project(":core:permissions:api"))
    implementation(project(":core:permissions:android"))
    implementation(project(":core:diagnostics"))
    implementation(project(":core:platform:api"))
    implementation(project(":core:platform:android"))
    implementation(project(":feature:template:impl"))
    implementation(project(":feature:settings:impl"))
    implementation(project(":feature:system:impl"))
    implementation(project(":feature:yfiles:api"))
    implementation(project(":feature:yfiles:impl"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
}

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

group = "com.github.yagay.YSuite"

val sharedJavaVersion = JavaVersion.toVersion(libs.versions.java.get())

android {
    namespace = "com.yagay.yui"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig { minSdk = libs.versions.minSdkLegacy.get().toInt() }
    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = sharedJavaVersion
        targetCompatibility = sharedJavaVersion
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    api(composeBom)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.tooling.preview)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons.extended)

    api(libs.androidx.activity.compose)
    api(libs.androidx.lifecycle.runtime.compose)
    api(libs.androidx.lifecycle.viewmodel.compose)
    api(libs.androidx.core.ktx)
    api(libs.androidx.appcompat)
    api(libs.androidx.recyclerview)
    api(libs.google.material)

    implementation(libs.androidx.startup)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

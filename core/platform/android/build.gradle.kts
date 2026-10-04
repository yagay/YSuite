plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.yagay.ysuite.platform.android"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:platform:api"))
    implementation(project(":core:common"))
    implementation(libs.kotlinx.coroutines.core)
}

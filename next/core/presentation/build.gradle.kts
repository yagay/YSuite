plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.yagay.ysuite.presentation"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(libs.androidx.lifecycle.viewmodel.ktx)
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}

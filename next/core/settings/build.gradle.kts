plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.yagay.ysuite.settings"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.datastore.preferences)
    api(libs.kotlinx.coroutines.core)
}

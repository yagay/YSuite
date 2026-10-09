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
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}

plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.yagay.ysuite.feature.ynotify.runtime"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":feature:ynotify:api"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.libxposed.service)
    compileOnly(libs.libxposed.api)
}

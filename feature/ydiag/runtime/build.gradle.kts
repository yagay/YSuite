plugins {
    alias(libs.plugins.android.library)
}
android {
    namespace = "com.yagay.ysuite.feature.ydiag.runtime"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation(libs.androidx.core.ktx)
    compileOnly(libs.libxposed.api)
}

plugins {
    alias(libs.plugins.android.library)
}
android {
    namespace = "com.yagay.YNFC"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        buildConfigField("int", "HOOK_BUILD", "40")
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    compileOnly(libs.libxposed.api)
    implementation(libs.androidx.core.ktx)
}

plugins {
    alias(libs.plugins.android.library)
}
android {
    namespace = "com.yagay.ysuite.feature.yminiguard.runtime"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        buildConfigField("long", "VERSION_CODE", "1L")
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    api(project(":feature:yminiguard:api"))
    compileOnly(libs.libxposed.api)
}

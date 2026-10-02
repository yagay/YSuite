plugins { alias(libs.plugins.android.application) }
val ciArm64Only = providers.gradleProperty("ciArm64Only").orNull == "true"
android {
    namespace = "com.yagay.yfiles.standalone"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        applicationId = "com.yagay.yfiles"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
        if (ciArm64Only) ndk { abiFilters.clear(); abiFilters += "arm64-v8a" }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildTypes { release { isMinifyEnabled = false } }
}
dependencies { implementation(project(":feature")) }

plugins {
    alias(libs.plugins.android.library)
}
android {
    namespace = "com.yagay.YFloat"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        buildConfigField("int", "VERSION_CODE", "191")
        buildConfigField("String", "VERSION_NAME", "\"3.1.39\"")
        buildConfigField("String", "GOOGLE_HOOK_FINGERPRINT", "\"\"")
        buildConfigField("String", "SYSTEMUI_HOOK_FINGERPRINT", "\"\"")
        buildConfigField("String", "SYSTEM_SERVER_HOOK_FINGERPRINT", "\"\"")
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation(project(":feature:yfloat:ppocr"))
    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    implementation(project(":core:common"))
    implementation(project(":core:platform:api"))
    implementation(project(":core:logging:api"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("com.google.android.material:material:1.13.0")
}

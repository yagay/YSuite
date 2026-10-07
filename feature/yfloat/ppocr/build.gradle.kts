plugins {
    alias(libs.plugins.android.library)
}
android {
    namespace = "com.paddle.ocr"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.24.3")
    implementation("org.opencv:opencv:4.12.0")
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
}

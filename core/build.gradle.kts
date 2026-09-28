plugins {
    id("com.android.library")
}

android {
    namespace = "com.yagay.suite.core"
    compileSdk = 37

    defaultConfig { minSdk = 31 }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("com.github.topjohnwu.libsu:core:6.0.0")
}

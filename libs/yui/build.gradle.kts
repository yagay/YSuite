plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

group = "com.github.yagay.YSuite"

android {
    namespace = "com.yagay.yui"
    compileSdk = 37

    defaultConfig { minSdk = 26 }
    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
    api(composeBom)
    api("androidx.compose.ui:ui")
    api("androidx.compose.ui:ui-tooling-preview")
    api("androidx.compose.foundation:foundation")
    api("androidx.compose.material3:material3")
    api("androidx.compose.material:material-icons-extended")

    api("androidx.activity:activity-compose:1.13.0")
    api("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    api("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    api("androidx.core:core-ktx:1.17.0")
    api("androidx.appcompat:appcompat:1.7.1")
    api("androidx.recyclerview:recyclerview:1.4.0")
    api("com.google.android.material:material:1.13.0")

    implementation("androidx.startup:startup-runtime:1.2.0")
}

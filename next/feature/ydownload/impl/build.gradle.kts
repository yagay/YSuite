plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.yagay.ysuite.feature.ydownload"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":next:core:runtime"))
    implementation(project(":next:feature:ydownload:api"))
    implementation(project(":next:core:ui"))
    implementation(project(":next:core:designsystem"))
    implementation(project(":next:core:productui"))
    implementation(project(":next:core:presentation"))
    implementation(project(":next:core:logging:api"))
    implementation(project(":next:core:platform:api"))
    implementation(project(":next:core:permissions:api"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation("androidx.lifecycle:lifecycle-service:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation(libs.kotlinx.coroutines.android)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    compileOnly(libs.libxposed.api)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation("androidx.compose.runtime:runtime")
    implementation(libs.androidx.compose.ui)

    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}

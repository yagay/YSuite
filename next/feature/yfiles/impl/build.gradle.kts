plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.yagay.ysuite.feature.yfiles"

    buildFeatures {
        aidl = true
    }
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
    implementation(project(":next:feature:yfiles:api"))
    implementation(project(":next:core:ui"))
    implementation(project(":next:core:designsystem"))
    implementation(project(":next:core:presentation"))
    implementation(project(":next:core:logging:api"))
    implementation(project(":next:core:platform:api"))

    implementation(libs.androidx.core.ktx)
    compileOnly(libs.libxposed.api)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation("androidx.compose.runtime:runtime")
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.activity.compose)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation(libs.androidx.documentfile)
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("org.apache.commons:commons-compress:1.28.0")
    // Provide the optional Brotli decoder instead of suppressing a real archive feature.
    implementation("org.brotli:dec:0.1.2")
    implementation("net.lingala.zip4j:zip4j:2.11.6")
    implementation("com.github.junrar:junrar:7.6.0")
    implementation("org.tukaani:xz:1.10")
    implementation("com.github.luben:zstd-jni:1.5.6-4")
    implementation("com.hierynomus:sshj:0.40.0")
    implementation("com.hierynomus:smbj:0.13.0")
    implementation("commons-net:commons-net:3.13.0")
    implementation("com.github.thegrizzlylabs:sardine-android:0.9") {
        exclude(group = "xpp3", module = "xpp3")
    }
    implementation("org.bouncycastle:bcprov-jdk18on:1.84")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.84")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}
val sharedSuiteBranch = providers.gradleProperty("ySuiteSharedBranch").orElse("main")
val sharedJavaVersion = JavaVersion.toVersion(libs.versions.java.get())
android {
    namespace = "com.yagay.ydownload"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = sharedJavaVersion; targetCompatibility = sharedJavaVersion }
}
dependencies {
    implementation("com.github.yagay.YSuite:api") { version { branch = sharedSuiteBranch.get() } }
    implementation("com.github.yagay.YSuite:ui") { version { branch = sharedSuiteBranch.get() } }
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)
    testImplementation(libs.junit)
}

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}
val sharedSuiteBranch = providers.gradleProperty("ySuiteSharedBranch").orElse("main")
val sharedJavaVersion = JavaVersion.toVersion(libs.versions.java.get())
android {
    namespace = "com.yagay.yfiles"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = sharedJavaVersion; targetCompatibility = sharedJavaVersion }
}
dependencies {
    implementation(project(":next:feature:yfiles:impl"))
    implementation(project(":next:core:platform:android"))
    implementation(project(":next:core:logging:api"))
    implementation("com.github.yagay.YSuite:api") { version { branch = sharedSuiteBranch.get() } }
    implementation("com.github.yagay.YSuite:ui") { version { branch = sharedSuiteBranch.get() } }
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.documentfile)
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

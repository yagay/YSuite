plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.yagay.ysuite.permissions.android"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:permissions:api"))
    implementation(libs.androidx.core.ktx)
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.yagay.YSuite"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.yagay.YSuite"
        minSdk = 31
        targetSdk = 37
        versionCode = 44
        versionName = "0.2.0"
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") { resources.srcDirs("src/main/resources") }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        resources.excludes += "META-INF/DEPENDENCIES"
        resources.merges += "META-INF/xposed/*"
        jniLibs.pickFirsts += setOf("**/libbytehook.so")
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":ydiag-feature"))
    implementation(project(":ynotify-feature"))
    implementation(project(":ypower-feature"))
    implementation(project(":yminiguard-feature"))
    implementation(project(":listcleaner-feature"))
    implementation(project(":ynfc-feature"))
    implementation(project(":ytask-feature"))
    implementation(project(":yparam-feature"))
    implementation(project(":yfloat-feature"))

    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

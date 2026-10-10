plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val sharedJavaVersion = JavaVersion.toVersion(libs.versions.java.get())

android {
    namespace = "com.yagay.ydiag"
    compileSdk {
        version = release(libs.versions.compileSdk.get().toInt()) {
            minorApiLevel = libs.versions.compileSdkMinor.get().toInt()
        }
    }

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = sharedJavaVersion
        targetCompatibility = sharedJavaVersion
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            consumerProguardFiles("consumer-rules.pro")
        }
    }
}

dependencies {
    implementation(project(":api"))
    implementation(project(":ui"))
    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)

    implementation(libs.androidx.documentfile)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
}

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

val sharedJavaVersion = JavaVersion.toVersion(libs.versions.java.get())

android {
    namespace = "com.yagay.YTaskManager"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdkLegacy.get().toInt()
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = sharedJavaVersion
        targetCompatibility = sharedJavaVersion
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES"
            )
        }
    }
}

dependencies {
    implementation(project(":api"))
    implementation(project(":ui"))
    implementation(libs.androidx.lifecycle.runtime.ktx)

    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)
}

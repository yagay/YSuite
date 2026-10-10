plugins {
    id("com.android.library")
}

val sharedJavaVersion = JavaVersion.toVersion(libs.versions.java.get())

android {
    namespace = "com.yagay.YNotify"
    compileSdk {
        version = release(libs.versions.compileSdk.get().toInt()) {
            minorApiLevel = libs.versions.compileSdkMinor.get().toInt()
        }
    }

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "VERSION_NAME", "\"1.3.5\"")
        buildConfigField("int", "VERSION_CODE", "9")
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    testOptions { unitTests.isIncludeAndroidResources = true }

    compileOptions {
        sourceCompatibility = sharedJavaVersion
        targetCompatibility = sharedJavaVersion
    }
}

dependencies {
    implementation(project(":api"))
    implementation(project(":ui"))
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.room.runtime)
    implementation(libs.sqlcipher.android)
    implementation(libs.libxposed.service)
    annotationProcessor(libs.androidx.room.compiler)
    compileOnly(libs.libxposed.api)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}

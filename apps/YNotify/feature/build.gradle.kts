plugins {
    id("com.android.library")
}

android {
    namespace = "com.yagay.YNotify"
    compileSdk {
        version = release(37) { minorApiLevel = 0 }
    }

    defaultConfig {
        minSdk = 31
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("com.github.yagay.YSuite:ui") {
        version { branch = "main" }
    }
    implementation("androidx.lifecycle:lifecycle-livedata:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime:2.10.0")
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("net.zetetic:sqlcipher-android:4.19.0")
    implementation("io.github.libxposed:service:102.0.0")
    annotationProcessor("androidx.room:room-compiler:2.7.2")
    compileOnly("io.github.libxposed:api:102.0.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.15.1")
    testImplementation("androidx.test:core:1.7.0")
}

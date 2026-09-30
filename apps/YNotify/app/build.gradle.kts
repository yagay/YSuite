plugins {
    id("com.android.application")
}

val ciArm64Only = providers.gradleProperty("ciArm64Only").orNull == "true"

android {
    namespace = "com.yagay.YNotify.standalone"
    compileSdk {
        version = release(37) { minorApiLevel = 0 }
    }

    defaultConfig {
        applicationId = "com.yagay.YNotify"
        minSdk = 31
        targetSdk = 37
        versionCode = 9
        versionName = "1.3.5"

        if (ciArm64Only) {
            ndk {
                abiFilters.clear()
                abiFilters += "arm64-v8a"
            }
        }
    }

    testOptions { unitTests.isIncludeAndroidResources = true }

    packaging.resources.merges += "META-INF/xposed/*"
    sourceSets {
        getByName("main") { resources.srcDirs("src/main/resources") }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":feature"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.15.1")
    testImplementation("androidx.test:core:1.7.0")
}

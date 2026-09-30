plugins {
    id("com.android.application")
}

val ciArm64Only = providers.gradleProperty("ciArm64Only").orNull == "true"

android {
    namespace = "com.yagay.ypower.standalone"
    compileSdk = 37
    compileSdkMinor = 0

    defaultConfig {
        applicationId = "com.yagay.ypower"
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        if (ciArm64Only) {
            ndk {
                abiFilters.clear()
                abiFilters += "arm64-v8a"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") { resources.srcDirs("src/main/resources") }
    }

    packaging {
        resources.merges += "META-INF/xposed/*"
        jniLibs.pickFirsts += setOf("**/libbytehook.so")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(project(":feature"))
}

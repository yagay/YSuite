plugins {
    id("com.android.application")
}

val ciArm64Only = providers.gradleProperty("ciArm64Only").orNull == "true"

android {
    namespace = "com.yagay.YMiniGuard.standalone"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.yagay.YMiniGuard"
        minSdk = 31
        targetSdk = 37
        versionCode = 84
        versionName = "5.3.4"

        if (ciArm64Only) {
            ndk {
                abiFilters.clear()
                abiFilters += "arm64-v8a"
            }
        }
    }

    sourceSets {
        getByName("main") { resources.srcDirs("src/main/resources") }
    }

    packaging.resources.merges += "META-INF/xposed/*"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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

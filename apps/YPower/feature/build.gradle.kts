plugins {
    id("com.android.library")
}

val sharedSuiteBranch = providers.gradleProperty("ySuiteSharedBranch").orElse("main")
val sharedJavaVersion = JavaVersion.toVersion(libs.versions.java.get())
val ciArm64Only = providers.gradleProperty("ciArm64Only").orNull == "true"

android {
    namespace = "com.yagay.ypower"
    compileSdk = libs.versions.compileSdk.get().toInt()
    compileSdkMinor = libs.versions.compileSdkMinor.get().toInt()
    ndkVersion = libs.versions.ndk.get()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        ndk {
            abiFilters.clear()
            abiFilters += if (ciArm64Only) listOf("arm64-v8a") else listOf("arm64-v8a", "armeabi-v7a")
        }
        externalNativeBuild { cmake { cppFlags += "-std=c++17" } }
    }

    compileOptions {
        sourceCompatibility = sharedJavaVersion
        targetCompatibility = sharedJavaVersion
    }

    buildFeatures {
        viewBinding = false
        buildConfig = true
        prefab = true
    }

    packaging {
        resources.excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*")
        jniLibs.pickFirsts += setOf("**/libbytehook.so")
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = libs.versions.cmake.get()
        }
    }
}

dependencies {
    implementation("com.github.yagay.YSuite:api") {
        version { branch = sharedSuiteBranch.get() }
    }
    implementation("com.github.yagay.YSuite:ui") {
        version { branch = sharedSuiteBranch.get() }
    }

    implementation(libs.libsu.core)
    implementation(libs.libsu.service)
    implementation(libs.libsu.nio)
    implementation(libs.bytehook)

    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)
}

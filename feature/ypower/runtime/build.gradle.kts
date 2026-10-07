plugins {
    alias(libs.plugins.android.library)
}
android {
    namespace = "com.yagay.ysuite.feature.ypower.runtime"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
            }
        }
    }
    ndkVersion = libs.versions.ndk.get()
    buildFeatures {
        prefab = true
    }
    packaging {
        // ByteHook is a Prefab/AAR dependency. Keep it transitive for the app,
        // but do not republish the same shared object from this SDK runtime.
        jniLibs.excludes += setOf("**/libbytehook.so")
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = libs.versions.cmake.get()
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation(libs.bytehook)
    compileOnly(libs.libxposed.api)
}

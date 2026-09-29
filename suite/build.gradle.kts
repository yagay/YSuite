plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val suiteHostVersionCode = providers.gradleProperty("ySuiteHostVersionCode")
    .orNull?.toIntOrNull() ?: 48
val suiteAbiFilters = providers.gradleProperty("ySuiteAbiFilters")
    .orNull
    ?.split(',')
    ?.map(String::trim)
    ?.filter(String::isNotEmpty)
    ?.takeIf(List<String>::isNotEmpty)
    ?: listOf("arm64-v8a")

android {
    namespace = "com.yagay.YSuite"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.yagay.YSuite"
        minSdk = 31
        targetSdk = 37
        versionCode = suiteHostVersionCode
        versionName = "0.2.4"

        // YSuite is currently distributed for modern ARM64 Android devices. Keeping the ABI list
        // at the application boundary prevents transitive OCR/OpenCV/ONNX AARs from re-introducing
        // x86/x86_64/armeabi-v7a native binaries into the final APK. Override with
        // -PySuiteAbiFilters=arm64-v8a,armeabi-v7a only when a wider compatibility build is needed.
        ndk {
            abiFilters.addAll(suiteAbiFilters)
        }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }

        // CI/user-facing build: keep debug semantics/logging and debug signing, but remove unused
        // dependency bytecode/resources. This leaves the ordinary debug variant untouched for
        // source-level troubleshooting while providing a substantially smaller installable APK.
        create("compact") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug", "release")
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    sourceSets {
        getByName("main") { resources.srcDirs("src/main/resources") }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        resources.excludes += "META-INF/DEPENDENCIES"
        // Do not merge Xposed entry metadata from dependencies. YSuite owns the one and only
        // META-INF/xposed/java_init.list; duplicate feature metadata must fail the build instead.
        jniLibs.pickFirsts += setOf("**/libbytehook.so")
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":ui"))
    implementation(project(":ydiag-feature"))
    implementation(project(":ynotify-feature"))
    implementation(project(":ypower-feature"))
    implementation(project(":yminiguard-feature"))
    implementation(project(":yentrycleaner-feature"))
    implementation(project(":ynfc-feature"))
    implementation(project(":ytask-feature"))
    implementation(project(":yparam-feature"))
    implementation(project(":yfloat-feature"))

    // The framework provides this at runtime. Only the YSuite host is an Xposed entry in the
    // combined APK; feature XposedModule classes are attached as logical plugins through the host.
    compileOnly("io.github.libxposed:api:102.0.0")

    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")

    // Host compatibility for feature Activities that still use AppCompat / Material Views.
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}

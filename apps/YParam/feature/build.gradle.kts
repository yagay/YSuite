plugins {
    id("com.android.library")
}

android {
    namespace = "com.yagay.yparam"
    compileSdk = 37

    defaultConfig {
        minSdk = 31
        vectorDrawables.useSupportLibrary = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*")
    }
}

dependencies {
    implementation("com.github.yagay.YSuite:ui") {
        version { branch = "main" }
    }
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation("io.github.libxposed:service:102.0.0")
}

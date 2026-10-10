plugins {
    id("com.android.library")
}

val sharedJavaVersion = JavaVersion.toVersion(libs.versions.java.get())

android {
    namespace = "com.yagay.yparam"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        vectorDrawables.useSupportLibrary = true
    }

    compileOptions {
        sourceCompatibility = sharedJavaVersion
        targetCompatibility = sharedJavaVersion
    }

    packaging {
        resources.excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*")
    }
}

dependencies {
    implementation(project(":api"))
    implementation(project(":ui"))
    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)
}

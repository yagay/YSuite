plugins {
    id("com.android.library")
}

val sharedSuiteBranch = providers.gradleProperty("ySuiteSharedBranch").orElse("main")
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
    if (rootProject.findProject(":api") != null) {
        implementation(project(":api"))
    } else if (rootProject.findProject(":ysuite-api") != null) {
        implementation(project(":ysuite-api"))
    } else {
        implementation("com.github.yagay.YSuite:api") {
            version { branch = sharedSuiteBranch.get() }
        }
    }
    if (rootProject.findProject(":ui") != null) {
        implementation(project(":ui"))
    } else if (rootProject.findProject(":ysuite-ui") != null) {
        implementation(project(":ysuite-ui"))
    } else {
        implementation("com.github.yagay.YSuite:ui") {
            version { branch = sharedSuiteBranch.get() }
        }
    }
    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)
}

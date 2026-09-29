plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    `maven-publish`
}

group = "com.github.yagay.YSuite"
version = System.getenv("VERSION") ?: "main-SNAPSHOT"

android {
    namespace = "com.yagay.yui"
    compileSdk = 37

    defaultConfig { minSdk = 31 }
    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    publishing {
        singleVariant("release") { withSourcesJar() }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
    api(composeBom)
    api("androidx.compose.ui:ui")
    api("androidx.compose.foundation:foundation")
    api("androidx.compose.material3:material3")
    api("androidx.activity:activity-compose:1.13.0")
    api("androidx.core:core-ktx:1.17.0")
    api("androidx.appcompat:appcompat:1.7.1")
    api("com.google.android.material:material:1.13.0")
}

afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.github.yagay.YSuite"
                artifactId = "ui"
                version = project.version.toString()
            }
        }
    }
}

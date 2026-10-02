plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

val embeddedInSuite = rootProject.findProject(":suite") != null
val configAuthority = providers.gradleProperty("ynfcConfigAuthority").orNull
    ?: if (embeddedInSuite) "com.yagay.YSuite.ynfc.config" else "com.yagay.YNFC.config"
val standaloneVersionCode = 57
val runtimeVersionCode = providers.gradleProperty("ySuiteHostVersionCode")
    .orNull?.toIntOrNull() ?: standaloneVersionCode
val sharedSuiteBranch = providers.gradleProperty("ySuiteSharedBranch").orElse("main")
val sharedJavaVersion = JavaVersion.toVersion(libs.versions.java.get())

android {
    namespace = "com.yagay.YNFC"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        buildConfigField("int", "VERSION_CODE", runtimeVersionCode.toString())
        buildConfigField("String", "VERSION_NAME", "\"1.0.56\"")
        buildConfigField("int", "HOOK_BUILD", "40")
        buildConfigField("String", "CONFIG_AUTHORITY", "\"$configAuthority\"")
        manifestPlaceholders["ynfcConfigAuthority"] = configAuthority
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = sharedJavaVersion
        targetCompatibility = sharedJavaVersion
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation("com.github.yagay.YSuite:api") {
        version { branch = sharedSuiteBranch.get() }
    }
    implementation("com.github.yagay.YSuite:ui") {
        version { branch = sharedSuiteBranch.get() }
    }
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.gson)
    compileOnly(libs.libxposed.api)
    testImplementation(libs.junit)
}

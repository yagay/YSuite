plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

val generatedYnfcSources = layout.buildDirectory.dir("generated/ynfc/java").get().asFile
val prepareYnfcSources by tasks.registering(Sync::class) {
    from(file("../../features/YNFC/app/src/main/java"))
    into(generatedYnfcSources)
    filteringCharset = "UTF-8"
    filesMatching(listOf("**/*.java", "**/*.kt")) {
        filter { line: String ->
            line.replace("com.yagay.YNFC.config", "com.yagay.YSuite.ynfc.config")
        }
    }
}

android {
    namespace = "com.yagay.YNFC"
    compileSdk = 37

    defaultConfig {
        minSdk = 31
        buildConfigField("int", "VERSION_CODE", "57")
        buildConfigField("String", "VERSION_NAME", "\"1.0.56\"")
        buildConfigField("int", "HOOK_BUILD", "40")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            java.srcDir(generatedYnfcSources)
            kotlin.srcDir(generatedYnfcSources)
            res.srcDirs("../../features/YNFC/app/src/main/res")
            assets.srcDirs("../../features/YNFC/app/src/main/assets")
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

tasks.matching {
    it.name.startsWith("compile") || it.name.startsWith("ksp") || it.name.startsWith("generate")
}.configureEach {
    dependsOn(prepareYnfcSources)
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-compose:1.12.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("com.google.code.gson:gson:2.11.0")
    compileOnly("io.github.libxposed:api:102.0.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

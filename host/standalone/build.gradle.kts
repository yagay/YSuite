plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

data class StandaloneFeatureSpec(
    val module: String,
    val registrationClass: String,
    val applicationId: String,
)

val standaloneSpecs = rootProject.file("config/standalone-features.properties")
    .readLines()
    .asSequence()
    .map(String::trim)
    .filter { it.isNotEmpty() && !it.startsWith("#") }
    .associate { line ->
        val parts = line.split('|')
        require(parts.size == 4) { "Invalid standalone feature row: $line" }
        parts[0] to StandaloneFeatureSpec(
            module = parts[1],
            registrationClass = parts[2],
            applicationId = parts[3],
        )
    }

val standaloneFeature = providers.gradleProperty("standaloneFeature")
    .orElse("template")
    .get()

val selectedFeature = standaloneSpecs[standaloneFeature]
    ?: error(
        "Unknown standaloneFeature '$standaloneFeature'. " +
            "Available: ${standaloneSpecs.keys.sorted().joinToString()}",
    )

val generatedStandaloneDir = layout.buildDirectory.dir(
    "generated/standaloneFeature/kotlin",
)

val generateStandaloneFeature by tasks.registering {
    inputs.property("standaloneFeature", standaloneFeature)
    inputs.property("registrationClass", selectedFeature.registrationClass)
    outputs.dir(generatedStandaloneDir)

    doLast {
        val packageDir = generatedStandaloneDir.get().asFile.resolve(
            "com/yagay/ysuite/standalone/generated",
        )
        packageDir.mkdirs()

        val simpleName = selectedFeature.registrationClass.substringAfterLast('.')
        packageDir.resolve("StandaloneFeature.kt").writeText(
            """
            package com.yagay.ysuite.standalone.generated

            import ${selectedFeature.registrationClass}
            import com.yagay.ysuite.ui.YSuiteFeatureUiRegistration

            val standaloneFeatureRegistration: YSuiteFeatureUiRegistration = $simpleName
            """.trimIndent() + "\n",
        )
    }
}

android {
    namespace = "com.yagay.ysuite.standalone"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = selectedFeature.applicationId
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    sourceSets.getByName("main").java.srcDir(generatedStandaloneDir)
    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

tasks.named("preBuild").configure {
    dependsOn(generateStandaloneFeature)
}

dependencies {
    implementation(project(selectedFeature.module))
    implementation(project(":core:ui"))
    implementation(project(":core:settings"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
}

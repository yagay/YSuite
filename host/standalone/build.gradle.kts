plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

data class StandaloneFeatureSpec(
    val module: String,
    val registrationClass: String,
    val applicationId: String,
    val runtimeModules: List<String>,
    val xposedInitClasses: List<String>,
    val xposedScopes: List<String>,
)

fun String.listField(): List<String> =
    split(';')
        .map(String::trim)
        .filter(String::isNotEmpty)

val standaloneSpecs = rootProject.file("config/standalone-features.properties")
    .readLines()
    .asSequence()
    .map(String::trim)
    .filter { it.isNotEmpty() && !it.startsWith("#") }
    .associate { line ->
        val parts = line.split('|')
        require(parts.size == 7) { "Invalid standalone feature row: $line" }
        parts[0] to StandaloneFeatureSpec(
            module = parts[1],
            registrationClass = parts[2],
            applicationId = parts[3],
            runtimeModules = parts[4].listField(),
            xposedInitClasses = parts[5].listField(),
            xposedScopes = parts[6].listField(),
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

val generatedStandaloneResources =
    layout.buildDirectory
        .dir("generated/standalone-xposed/$standaloneFeature")
        .get()
        .asFile

generatedStandaloneResources.deleteRecursively()
if (selectedFeature.xposedInitClasses.isNotEmpty()) {
    val xposedDir =
        generatedStandaloneResources.resolve("META-INF/xposed")
    xposedDir.mkdirs()
    xposedDir.resolve("java_init.list").writeText(
        selectedFeature.xposedInitClasses.joinToString("\n", postfix = "\n"),
    )
    xposedDir.resolve("module.prop").writeText(
        """
        minApiVersion=102
        targetApiVersion=102
        staticScope=false
        exceptionMode=protective
        autoHotReload=false
        """.trimIndent() + "\n",
    )
    xposedDir.resolve("scope.list").writeText(
        selectedFeature.xposedScopes.joinToString("\n").let {
            if (it.isEmpty()) "" else it + "\n"
        },
    )
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
        buildConfigField(
            "String",
            "FEATURE_REGISTRATION_CLASS",
            "\"${selectedFeature.registrationClass}\"",
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        getByName("main").resources.srcDir(
            generatedStandaloneResources,
        )
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(selectedFeature.module))
    selectedFeature.runtimeModules.forEach { runtimeModule ->
        implementation(project(runtimeModule))
    }
    implementation(project(":core:ui"))
    implementation(project(":core:settings"))
    implementation(project(":core:platform:android"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
}

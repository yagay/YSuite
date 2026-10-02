plugins {
    id("com.android.application")
}

data class StandaloneFeature(
    val id: String,
    val name: String,
    val gradleModule: String,
    val packageName: String,
    val entryActivity: String,
    val hooks: List<String>,
)

fun loadStandaloneFeatures(): List<StandaloneFeature> {
    val catalog = rootProject.file("config/generated/standalone-features.tsv")
    require(catalog.isFile) {
        "Missing ${catalog.relativeTo(rootProject.projectDir)}. Run: python3 tools/generate_standalone_catalog.py"
    }
    return catalog.readLines()
        .asSequence()
        .map(String::trimEnd)
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { line ->
            val parts = line.split('\t')
            require(parts.size >= 6) { "Invalid standalone feature row: $line" }
            StandaloneFeature(
                id = parts[0],
                name = parts[1],
                gradleModule = parts[2],
                packageName = parts[3],
                entryActivity = parts[4],
                hooks = parts[5].split(';').map(String::trim).filter(String::isNotEmpty),
            )
        }
        .toList()
}

val standaloneFeatures = loadStandaloneFeatures()
val requestedFeature = providers.gradleProperty("ySuiteStandaloneFeature").orNull?.trim()
val selected = when {
    requestedFeature.isNullOrEmpty() ->
        standaloneFeatures.firstOrNull { it.id == "yfiles" } ?: standaloneFeatures.first()
    else -> standaloneFeatures.firstOrNull {
        it.id.equals(requestedFeature, ignoreCase = true) ||
            it.name.equals(requestedFeature, ignoreCase = true)
    } ?: error(
        "Unknown YSuite feature '$requestedFeature'. Available: " +
            standaloneFeatures.joinToString { "${it.id}(${it.name})" },
    )
}

val ciArm64Only = providers.gradleProperty("ciArm64Only").orNull == "true"
val generatedXposedResources = layout.buildDirectory.dir("generated/standalone-xposed")
val generatedXposedResourcesDir = generatedXposedResources.get().asFile

val generateStandaloneXposedResources = tasks.register("generateStandaloneXposedResources") {
    outputs.dir(generatedXposedResourcesDir)
    doLast {
        val root = generatedXposedResourcesDir
        root.deleteRecursively()
        if (selected.hooks.isEmpty()) return@doLast

        val xposed = root.resolve("META-INF/xposed").apply { mkdirs() }
        xposed.resolve("java_init.list").writeText(
            selected.hooks.joinToString(separator = "\n", postfix = "\n"),
        )
        xposed.resolve("module.prop").writeText(
            """
            minApiVersion=102
            targetApiVersion=102
            staticScope=false
            exceptionMode=protective
            autoHotReload=true
            """.trimIndent() + "\n",
        )
    }
}

android {
    namespace = "com.yagay.ysuite.standalone"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = selected.packageName
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = providers.gradleProperty("ySuiteStandaloneVersionCode").orNull?.toIntOrNull() ?: 1
        versionName = providers.gradleProperty("ySuiteStandaloneVersionName").orNull ?: "0.1.0"

        manifestPlaceholders["standaloneAppName"] = selected.name
        manifestPlaceholders["standaloneFeatureId"] = selected.id
        manifestPlaceholders["standaloneEntryActivity"] = selected.entryActivity

        if (ciArm64Only) {
            ndk {
                abiFilters.clear()
                abiFilters += "arm64-v8a"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    sourceSets {
        getByName("main") {
            resources.srcDir(generatedXposedResourcesDir)
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(generateStandaloneXposedResources)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":ui"))
    implementation(project(selected.gradleModule))
}

tasks.register<Copy>("packageFeatureDebug") {
    dependsOn("assembleDebug")
    from(layout.buildDirectory.dir("outputs/apk/debug"))
    include("*.apk")
    into(rootProject.layout.buildDirectory.dir("standalone"))
    rename { "${selected.name}-debug.apk" }
}

tasks.register<Copy>("packageFeatureRelease") {
    dependsOn("assembleRelease")
    from(layout.buildDirectory.dir("outputs/apk/release"))
    include("*.apk")
    into(rootProject.layout.buildDirectory.dir("standalone"))
    rename { "${selected.name}.apk" }
}

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
    val description: String,
    val label: String?,
    val descriptionResource: String?,
    val icon: String?,
    val roundIcon: String?,
    val theme: String?,
    val localeConfig: String?,
    val allowBackup: Boolean,
    val usesCleartextTraffic: Boolean,
    val nfcRequired: Boolean,
)

fun decodeOptional(value: String): String? = value.takeUnless { it == "-" || it.isBlank() }

fun parseCatalogBoolean(value: String, field: String, featureId: String): Boolean = when (value) {
    "true" -> true
    "false" -> false
    else -> error("Invalid $field for $featureId: $value")
}

fun loadStandaloneFeatures(): List<StandaloneFeature> {
    val catalog = rootProject.file("config/generated/standalone-features.tsv")
    require(catalog.isFile) {
        "Missing ${catalog.relativeTo(rootProject.projectDir)}. Run: python3 tools/generate_standalone_catalog.py"
    }
    return catalog.readLines()
        .asSequence()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { line ->
            val parts = line.split('\t')
            require(parts.size == 16) { "Invalid standalone feature row (${parts.size} columns): $line" }
            val featureId = parts[0]
            StandaloneFeature(
                id = featureId,
                name = parts[1],
                gradleModule = parts[2],
                packageName = parts[3],
                entryActivity = parts[4],
                hooks = decodeOptional(parts[5])?.split(';')?.map(String::trim)?.filter(String::isNotEmpty).orEmpty(),
                description = parts[6],
                label = decodeOptional(parts[7]),
                descriptionResource = decodeOptional(parts[8]),
                icon = decodeOptional(parts[9]),
                roundIcon = decodeOptional(parts[10]),
                theme = decodeOptional(parts[11]),
                localeConfig = decodeOptional(parts[12]),
                allowBackup = parseCatalogBoolean(parts[13], "allow_backup", featureId),
                usesCleartextTraffic = parseCatalogBoolean(parts[14], "uses_cleartext_traffic", featureId),
                nfcRequired = parseCatalogBoolean(parts[15], "nfc_required", featureId),
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

        // The catalog name/description are the authoritative standalone text metadata. Feature
        // resources are reserved for real visual/behavioral assets such as icons, themes and locale
        // declarations, avoiding duplicate app_name/module_description strings in every Feature.
        resValue("string", "standalone_app_name", selected.name)
        resValue("string", "standalone_app_description", selected.description)
        manifestPlaceholders["standaloneFeatureId"] = selected.id
        manifestPlaceholders["standaloneEntryActivity"] = selected.entryActivity
        manifestPlaceholders["standaloneLabel"] = "@string/standalone_app_name"
        manifestPlaceholders["standaloneDescription"] = "@string/standalone_app_description"
        manifestPlaceholders["standaloneIcon"] = selected.icon ?: "@null"
        manifestPlaceholders["standaloneRoundIcon"] = selected.roundIcon ?: "@null"
        manifestPlaceholders["standaloneTheme"] = selected.theme ?: "@style/Theme.YUI"
        manifestPlaceholders["standaloneLocaleConfig"] = selected.localeConfig ?: "@null"
        manifestPlaceholders["standaloneAllowBackup"] = selected.allowBackup.toString()
        manifestPlaceholders["standaloneUsesCleartextTraffic"] = selected.usesCleartextTraffic.toString()
        manifestPlaceholders["standaloneNfcRequired"] = selected.nfcRequired.toString()

        if (ciArm64Only) {
            ndk {
                abiFilters.clear()
                abiFilters += "arm64-v8a"
            }
        }
    }

    buildFeatures {
        resValues = true
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

    packaging {
        jniLibs.pickFirsts += setOf("**/libbytehook.so")
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

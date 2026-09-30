pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "YSuite"

include(":api")
project(":api").projectDir = file("libs/yapi")

include(":ui")
project(":ui").projectDir = file("libs/yui")

include(":core")
project(":core").projectDir = file("libs/ycore")

include(":suite")
project(":suite").projectDir = file("suite/YSuite")

// Feature modules are generated from config/features.toml. Do not duplicate the feature list here.
val generatedFeatureModules = file("config/generated/feature-modules.tsv")
generatedFeatureModules.readLines()
    .asSequence()
    .map(String::trim)
    .filter { it.isNotEmpty() && !it.startsWith("#") }
    .forEach { line ->
        val parts = line.split('\t', limit = 2)
        require(parts.size == 2) { "Invalid generated feature module row: $line" }
        val module = parts[0]
        val projectDir = parts[1]
        include(module)
        project(module).projectDir = file(projectDir)
    }

include(":yfloat-ppocr-sdk")
project(":yfloat-ppocr-sdk").projectDir = file("apps/YFloat/ppocr-sdk")

plugins { id("org.jetbrains.kotlin.jvm") }

kotlin { jvmToolchain(17) }

dependencies {
    api(project(":next:core:model"))
    testImplementation(libs.junit)
}

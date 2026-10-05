plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin { jvmToolchain(17) }

dependencies {
    api(project(":core:common"))
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
}

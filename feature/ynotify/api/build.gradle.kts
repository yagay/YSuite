plugins { alias(libs.plugins.kotlin.jvm) }

kotlin { jvmToolchain(17) }

dependencies {
    api(project(":core:model"))
    api(project(":core:navigation"))
    testImplementation(libs.junit)
    implementation(libs.kotlinx.coroutines.core)
}

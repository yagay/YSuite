plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin { jvmToolchain(17) }

dependencies {
    api(project(":next:core:common"))
    api(project(":next:core:model"))
    api(project(":next:core:navigation"))
}

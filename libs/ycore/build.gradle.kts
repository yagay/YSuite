plugins {
    id("com.android.library")
}

android {
    namespace = "com.yagay.suite.core"
    compileSdk = 37

    defaultConfig { minSdk = 31 }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("com.github.topjohnwu.libsu:core:6.0.0")
    // SuiteXposedServiceBroker exposes XposedServiceHelper.OnServiceListener in its public type,
    // so consumers of :core must see libxposed service on their compile classpath.
    api("io.github.libxposed:service:102.0.0")
}

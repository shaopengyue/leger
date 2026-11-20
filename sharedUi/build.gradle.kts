plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    kotlin("plugin.serialization")
    id("org.jetbrains.compose")
    id("com.android.library")
}
kotlin {
    androidTarget()
    if (providers.gradleProperty("enableIos").orNull == "true") {
        iosArm64()
        iosSimulatorArm64()
    }
    jvmToolchain(17)
    sourceSets.commonMain.dependencies {
        implementation(project(":sharedCore"))
        implementation(compose.runtime)
        implementation(compose.foundation)
        implementation(compose.material3)
        implementation(compose.materialIconsExtended)
        implementation(compose.components.resources)
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
        implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    }
}
android { namespace = "dev.leafledger.ui"; compileSdk = 36; defaultConfig { minSdk = 29 } }

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.library")
    id("com.google.devtools.ksp")
    id("androidx.room")
}
kotlin {
    androidTarget()
    jvm()
    if (providers.gradleProperty("enableIos").orNull == "true") {
        iosArm64()
        iosSimulatorArm64()
    }
    jvmToolchain(17)
    sourceSets {
        commonMain.dependencies {
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
            api("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
            api("androidx.room:room-runtime:2.7.2")
            implementation("androidx.sqlite:sqlite-bundled:2.5.2")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
        }
    }
}
android { namespace = "dev.leafledger.core"; compileSdk = 36; defaultConfig { minSdk = 29 } }
dependencies {
    add("kspAndroid", "androidx.room:room-compiler:2.7.2")
    add("kspJvm", "androidx.room:room-compiler:2.7.2")
    if (providers.gradleProperty("enableIos").orNull == "true") {
        add("kspIosArm64", "androidx.room:room-compiler:2.7.2")
        add("kspIosSimulatorArm64", "androidx.room:room-compiler:2.7.2")
    }
}
room { schemaDirectory("$projectDir/schemas") }

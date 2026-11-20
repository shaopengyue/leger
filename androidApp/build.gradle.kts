plugins { id("com.android.application"); kotlin("android"); kotlin("plugin.compose") }
android {
    namespace = "dev.leafledger.android"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.leafledger.android"
        minSdk = 29
        targetSdk = 36
        versionCode = 8
        versionName = "0.4.4"
    }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    buildTypes { release { isMinifyEnabled = false } }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation(project(":sharedCore"))
    implementation(project(":sharedUi"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.sqlite:sqlite-bundled:2.5.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}

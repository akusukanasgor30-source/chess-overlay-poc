plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.chessoverlay.poc"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.chessoverlay.poc"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "0.2-modul2"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")   // FileProvider (membagikan hasil uji)
    implementation("com.github.bhlangonijr:chesslib:1.3.3")   // aturan catur: langkah sah, FEN
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

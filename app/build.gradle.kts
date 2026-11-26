plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.worldmap"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.example.worldmap"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // Карта: открытая библиотека MapLibre. Ключ API не нужен.
    implementation("org.maplibre.gl:android-sdk:11.13.5")

    // Только для тестов: в android.jar классы org.json на JVM-тестах не работают.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

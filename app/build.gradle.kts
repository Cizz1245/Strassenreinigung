plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "de.cizz.strassenreinigung"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.cizz.strassenreinigung"
        minSdk = 26          // Android 8.0 – nötig für java.time (LocalDate)
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // Android-Grundlagen
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Oberfläche (Jetpack Compose, Material 3)
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")

    // Regelmäßige Hintergrundprüfung auf neue Termine
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Webseite laden und HTML auslesen
    implementation("org.jsoup:jsoup:1.18.1")

    // Tests für den Termin-Parser
    testImplementation("junit:junit:4.13.2")
}

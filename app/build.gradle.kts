plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.myapplication"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.2")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.fragment:fragment:1.7.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.5")
    // Dependensi eksplisit untuk mengatasi checkDebugAarMetadata
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("androidx.customview:customview:1.1.0")
    implementation("androidx.emoji2:emoji2:1.4.0")
    implementation("androidx.emoji2:emoji2-views-helper:1.4.0")
    implementation("androidx.loader:loader:1.1.0")
    implementation("androidx.viewpager:viewpager:1.0.0")
    implementation("androidx.resourceinspection:resourceinspection-annotation:1.0.1")
}
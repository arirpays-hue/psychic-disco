plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val ciRunNumber = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
val computedVersionCode = 100 + ciRunNumber

android {
    namespace = "com.example.englishtutor"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.englishtutor"
        minSdk = 24
        targetSdk = 34
        versionCode = computedVersionCode
        versionName = "5.0.$ciRunNumber"

        resourceConfigurations += listOf("en", "iw", "he")
    }

    signingConfigs {
        create("persistentSign") {
            val ksFile = file("persistent-release-key.jks")
            if (ksFile.exists()) {
                storeFile = ksFile
                storePassword = "EnglishTutor2026"
                keyAlias = "english_tutor_key"
                keyPassword = "EnglishTutor2026"
            }
        }
    }

    buildTypes {
        debug {
            val ksFile = file("persistent-release-key.jks")
            if (ksFile.exists()) {
                signingConfig = signingConfigs.getByName("persistentSign")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        release {
            val ksFile = file("persistent-release-key.jks")
            if (ksFile.exists()) {
                signingConfig = signingConfigs.getByName("persistentSign")
            }
            isMinifyEnabled = true
            isShrinkResources = true
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/*.version"
            excludes += "DebugProbesKt.bin"
            excludes += "kotlin-tooling-metadata.json"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("androidx.activity:activity-compose:1.9.0")

    val composeBom = platform("androidx.compose:compose-bom:2024.05.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
}

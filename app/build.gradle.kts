plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.gamemodeai"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.gamemodeai"
        minSdk = 24
        targetSdk = 35
        // En CI se inyecta: ./gradlew assembleRelease -PversionCode=<run_number>
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 38
        versionName = (project.findProperty("versionCode") as String?)?.let { "v$it" } ?: "v38"
    }

    buildTypes {
        release {
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
        buildConfig = true   // necesario para BuildConfig.VERSION_CODE en UpdateChecker
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.02.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("dev.rikka.shizuku:api:11.0.3")
    implementation("dev.rikka.shizuku:provider:11.0.3")
}

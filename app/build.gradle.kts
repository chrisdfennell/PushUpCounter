plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.fennell.pushupcounter"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fennell.pushupcounter"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }

    // Kotlin 1.9.23 <-> Compose Compiler 1.5.12 (compat)
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.12"
    }

    // Java/Kotlin 17
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    // Keep Kotlin BOM aligned with your plugin version
    val kotlinVersion = libs.versions.kotlin.get()
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:$kotlinVersion"))

    // Compose BOM (drives Compose ui/material/material3 versions)
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Core AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Compose UI
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)

    // ✅ Material 3 (provides androidx.compose.material3.Slider)
    implementation(libs.androidx.compose.material3)

    // Optional: Material Icons (M2 icons still fine alongside M3)
    implementation(libs.androidx.compose.material.iconsExtended)

    // View system Material Components (optional)
    implementation(libs.google.android.material)

    // Splash
    implementation(libs.androidx.core.splashscreen)

    // Wear Compose (you already had these)
    implementation(libs.androidx.wear.compose.material)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.input)
    implementation(libs.androidx.wear.ongoing)

    // Debug/Test
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    androidTestImplementation(libs.androidx.ui.test.junit4)
}
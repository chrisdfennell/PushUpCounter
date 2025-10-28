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
    composeOptions {
        // Updated compiler extension version for Kotlin 1.9.24
        // See https://developer.android.com/jetpack/androidx/releases/compose-kotlin
        kotlinCompilerExtensionVersion = "1.5.12" // Per map for Kotlin 1.9.24
    }

    // This block correctly sets your Java and Kotlin compilers to Java 17
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
    // Force Kotlin Standard Library versions *within* dependencies block
    val kotlinVersion = libs.versions.kotlin.get()
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion") {
        isForce = true
    }
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:$kotlinVersion") {
        isForce = true
    }
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion") {
        isForce = true
    }


    // Use alias from libs.versions.toml
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Use aliases from libs.versions.toml
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Versions managed by BOM
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)

    // Explicit Material Icons dependency using alias
    implementation(libs.androidx.compose.material.iconsExtended)

    // View-system Material Components using alias
    implementation(libs.google.android.material)

    // Splash using alias
    implementation(libs.androidx.core.splashscreen)

    // Wear Compose using aliases
    implementation(libs.androidx.wear.compose.material)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.input)
    implementation(libs.androidx.wear.ongoing)

    // Debug/Test dependencies using aliases
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    androidTestImplementation(libs.androidx.ui.test.junit4)
}
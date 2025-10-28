// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // FIX: Use AGP version from libs.versions.toml (was 8.6.1)
    id("com.android.application") version "8.5.2" apply false
    // FIX: Use Kotlin version from libs.versions.toml (was 1.9.25)
    kotlin("android") version "1.9.23" apply false
}

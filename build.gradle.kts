// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.google.services) apply false
    id("com.google.firebase.crashlytics") version "3.0.8" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}

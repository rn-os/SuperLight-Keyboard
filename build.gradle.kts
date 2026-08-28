// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

extra["compileSdk"] = 36
extra["minSdk"] = 33
extra["targetSdk"] = 36
extra["jvmTarget"] = "11"
extra["sdkVersion"] = "1.0.0"
extra["lintVersion"] = "32.3.2"

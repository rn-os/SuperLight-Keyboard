import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.thelightphone.lp3keyboard"
    compileSdk = 36

    base {
        archivesName.set("SuperLight Keyboard")
    }

    defaultConfig {
        applicationId = "com.thelightphone.lp3keyboard"
        minSdk = 33
        targetSdk = 36
        versionCode = 4
        versionName = providers.gradleProperty("projectVersion").get()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Signed with the debug keystore so it matches the key the
            // existing public release was signed with (there is no
            // dedicated release keystore in this repo) - this keeps
            // release APKs installable as an in-place update rather than
            // requiring existing users to uninstall first.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    androidComponents {
        onVariants { variant ->
            variant.outputs.forEach { output ->
                if (variant.name.contains("release", ignoreCase = true)) {
                    output.outputFileName.set("SuperLight Keyboard.apk")
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.activity.compose)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("com.alphacephei:vosk-android:0.3.47")
    api(project(":ui"))
}

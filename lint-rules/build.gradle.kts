plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "com.thelightphone"
version = "1.0-SNAPSHOT"

val lintVersion = rootProject.extra["lintVersion"] as String

dependencies {
    compileOnly("com.android.tools.lint:lint-api:$lintVersion")
    compileOnly("com.android.tools.lint:lint-checks:$lintVersion")
}

kotlin {
    jvmToolchain((rootProject.extra["jvmTarget"] as String).toInt())
}

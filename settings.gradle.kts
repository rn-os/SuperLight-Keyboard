pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            name = "JitPack"
            url = uri("https://jitpack.io")
        }
    }
}

rootProject.name = "SuperLight-Keyboard"

// LightOS SDK modules: not wired into the active build yet. The light-sdk
// Gradle plugin's dependency validator doesn't recognize AGP 9.3.2's internal
// tooling configs (androidLintTool, unified-test-platform-*) and fails
// configuring :sdk:client as "unexpected resolved dependency" on Android's own
// lint/test machinery. Re-enable once that's fixed upstream in plugin/.
// includeBuild("plugin")
// include(":lint-rules")
// include(":sdk:shared")
// include(":sdk:ui")
// include(":sdk:client")

include(":app")
include(":ui")

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

    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "org.gradle.toolchains.foojay-resolver-convention") {
                useModule("org.gradle.toolchains:foojay-resolver:${requested.version}")
            }
        }
    }

}
plugins {
    // ... other plugins like com.android.application apply false
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0" apply false // Check for latest version
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "VolunteersApp"
include(":app")
 
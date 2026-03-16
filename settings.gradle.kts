pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven("https://download.agora.io/maven")
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
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" apply false
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://download.agora.io/maven") }
    }
}

rootProject.name = "VolunteersApp"
include(":app")

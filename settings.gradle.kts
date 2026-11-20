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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven-central.storage-download.googleapis.com/maven2")
        }
    }
}

rootProject.name = "zidee-ress"

include(":app")
include(":core")
include(":domain")
include(":data")
include(":media-engine")
include(":design-system")
include(":presentation")

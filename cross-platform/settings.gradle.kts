pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "WhisperPlayer"
include(":shared-core", ":composeApp")
project(":shared-core").projectDir = file("../shared-core")
project(":composeApp").projectDir = file("../composeApp")

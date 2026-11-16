pluginManagement {
    repositories {
        google()
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
    }
}

rootProject.name = "TappyLanguage"

// engine: selection engine, plain Kotlin (no Android), unit-tested on the JVM.
// game:   Android app (Jetpack Compose) that plays the falling-words game.
include(":engine", ":game")

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

rootProject.name = "TappyLanguage"

// engine: selection engine, plain Kotlin (no Android), unit-tested on the JVM.
// game:   Android app (Jetpack Compose) that plays the falling-words game.
include(":engine", ":game")

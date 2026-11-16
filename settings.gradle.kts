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

rootProject.name = "EduMateSG"

include(":app")
// Pure-Kotlin business, AI-safety and privacy logic. No Android dependencies; tests run on the JVM.
include(":core:domain")
// Local/dev AI backend (Ktor). Holds the Claude API key so the app never does.
include(":backend")

// Legt fest, wo Gradle Plugins und Bibliotheken herunterlädt
// und aus welchen Modulen das Projekt besteht (hier nur "app").
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

rootProject.name = "Strassenreinigung"
include(":app")

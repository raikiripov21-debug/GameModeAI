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

rootProject.name = "GameModeAI"
include(":app")         // Samsung Galaxy A06  — com.gamemode.a06
include(":samsung-a26") // Samsung Galaxy A26  — com.gamemode.a26
include(":moto-g04s")   // Motorola Moto G04s  — com.gamemode.moto

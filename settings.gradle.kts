pluginManagement {
    repositories {
        google()            // Упрощено: дает полный доступ ко всем инструментам Google
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") } // Добавьте эту строку
    }
}

rootProject.name = "MuzPleer"
include(":app")
 
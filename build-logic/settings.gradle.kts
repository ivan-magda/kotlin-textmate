pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

// Gradle 8 marks these repository APIs as incubating.
// Examine these APIs when you update the Gradle wrapper.
@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal {
            content { includeGroupByRegex("org\\.gradle\\.kotlin.*") }
        }
    }
    versionCatalogs {
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
}

rootProject.name = "build-logic"

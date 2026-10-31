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
        // Optional, ignored local cache. Only official, checksum-verified artifacts belong here.
        val verifiedLocalCache = rootDir.resolve(".local/maven-cache")
        if (verifiedLocalCache.isDirectory) {
            maven { url = uri(verifiedLocalCache) }
        }
        google {
            content {
                includeGroupByRegex("androidx\\..*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google\\.android.*")
                includeGroup("com.google.testing.platform")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "Fuliji"
include(":app")

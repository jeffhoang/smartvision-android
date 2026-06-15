import java.util.Properties
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.inputStream

val localProperties = Properties().apply {
    val path = rootDir.toPath() / "local.properties"
    if (path.exists()) load(path.inputStream())
}

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
        // Meta MWDAT Android SDK — requires a GitHub token with read:packages scope.
        // Add github_token=YOUR_TOKEN to local.properties (git-ignored),
        // or set the GITHUB_TOKEN environment variable.
        maven {
            url = uri("https://maven.pkg.github.com/facebook/meta-wearables-dat-android")
            credentials {
                username = "" // not required by GitHub Packages
                password = System.getenv("GITHUB_TOKEN")
                    ?: localProperties.getProperty("github_token")
            }
        }
    }
}

rootProject.name = "StreamMog"
include(":app")

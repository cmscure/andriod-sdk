import org.gradle.api.initialization.resolve.RepositoriesMode

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

include(
    ":composeApp",
    ":xmlApp"
)

includeBuild("..") {
    dependencySubstitution {
        substitute(module("com.github.cmscure:andriod-sdk")).using(project(":"))
    }
}

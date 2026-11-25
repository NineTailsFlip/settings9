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

rootProject.name = "settings9"

// The Nine suite's core, and :update for SetupChoices (the look every Nine app shares), from the
// nine-core submodule (git clone --recursive, or git submodule update --init).
include(":core")
project(":core").projectDir = file("nine-core/core")
include(":update")
project(":update").projectDir = file("nine-core/update")

include(":app")

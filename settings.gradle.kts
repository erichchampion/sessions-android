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

rootProject.name = "Sessions-Android"
include(":app")
include(":llama")
project(":llama").projectDir = java.io.File("app/src/main/cpp/llama.cpp/examples/llama.android/lib")

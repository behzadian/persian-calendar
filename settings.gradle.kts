pluginManagement {
    repositories {
        maven("https://gradle.iranrepo.ir" )
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven ("https://gradle.iranrepo.ir")
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
rootProject.name = "persian-calendar"
include(":PersianCalendar")
includeBuild("gradlePlugins")

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "JotMD"

include(":core-md")
include(":editor")
include(":app")
if (file("desktop/build.gradle.kts").isFile && providers.gradleProperty("androidOnly").orNull != "true") {
    include(":desktop")
}

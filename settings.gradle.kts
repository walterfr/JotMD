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
// ponytail: :editor e :app entram em F1, quando houver o que renderizar.
// Declarar módulos Android vazios agora só faria o AGP baixar e configurar sem uso.

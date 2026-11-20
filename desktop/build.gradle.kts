import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
}

kotlin {
    jvmToolchain(17)
    // Fontes compartilhados de :editor sem dependência Android.
    sourceSets["main"].kotlin.srcDir(rootProject.file("editor/src/main/kotlin"))
}

// Reusa o documento de referência de docs/ sem duplicar arquivo.
sourceSets["main"].resources.srcDir(rootProject.file("docs"))

dependencies {
    implementation(project(":core-md"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    testImplementation(libs.kotlin.test)
}

compose.desktop {
    application {
        mainClass = "io.github.walterfr.jotmd.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "JotMD"
            packageVersion = "0.1.0"
        }
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "failed", "skipped") }
}

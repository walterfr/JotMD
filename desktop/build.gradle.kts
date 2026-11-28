import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask

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

dependencies {
    implementation(project(":core-md"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    testImplementation(libs.kotlin.test)
}

val desktopPackageVersionRaw = (findProperty("desktopPackageVersion") as String?)?.trim().orEmpty()
val desktopPackageVersion = Regex("""\d+\.\d+\.\d+""").find(desktopPackageVersionRaw)?.value ?: "0.6.1"

compose.desktop {
    application {
        mainClass = "io.github.walterfr.jotmd.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "JotMD"
            packageVersion = desktopPackageVersion
            windows {
                iconFile.set(project.file("src/main/resources/jotmd.ico"))
            }
        }
    }
}

tasks.withType<AbstractJPackageTask>().configureEach {
    if (targetFormat != TargetFormat.AppImage) {
        freeArgs.add("--file-associations")
        freeArgs.add(project.file("packaging/markdown-association.properties").absolutePath)
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "failed", "skipped") }
}

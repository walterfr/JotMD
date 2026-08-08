plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

// Os .md de docs/ são o corpus de roundtrip. Uma cópia só, versionada onde se lê.
sourceSets.test { resources.srcDir(rootProject.file("docs")) }

dependencies {
    api(libs.jetbrains.markdown)
    testImplementation(libs.kotlin.test)
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "failed", "skipped") }
}

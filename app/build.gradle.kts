plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.walterfr.jotmd"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.github.walterfr.jotmd"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 6
        versionName = "0.4.0"
    }

    val releaseKeystorePath = System.getenv("KEYSTORE_PATH")
    val releaseKeystoreFile = releaseKeystorePath?.let { file(it) }?.takeIf { it.isFile }
    val releaseKeystorePassword = System.getenv("KEYSTORE_PASSWORD")
    val releaseKeyAlias = System.getenv("KEY_ALIAS")
    val releaseKeyPassword = System.getenv("KEY_PASSWORD") ?: releaseKeystorePassword
    val hasReleaseKeystore = releaseKeystoreFile != null &&
        !releaseKeystorePassword.isNullOrEmpty() &&
        !releaseKeyAlias.isNullOrEmpty() &&
        !releaseKeyPassword.isNullOrEmpty()
    val useDebugSigning = System.getenv("USE_DEBUG_SIGNING") == "true"

    if (hasReleaseKeystore) {
        signingConfigs {
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // CI assina com keystore via secrets (ou debug se USE_DEBUG_SIGNING=true); F-Droid constrói sem env vars.
            when {
                hasReleaseKeystore -> signingConfig = signingConfigs.getByName("release")
                useDebugSigning -> signingConfig = signingConfigs.getByName("debug")
            }
        }
    }

    buildFeatures { compose = true }

    dependenciesInfo {
        // F-Droid exige APK sem bloco proprietário de metadados de dependências do AGP.
        includeInApk = false
        includeInBundle = false
    }

    // O doc de referência é um só, versionado em docs/. Empacotar uma cópia
    // seria a segunda fonte de verdade a divergir.
    sourceSets["main"].assets.srcDir(rootProject.file("docs"))

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":editor"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.documentfile)

    testImplementation(libs.kotlin.test)
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Versionsnummer kommt aus GitHub Actions (fortlaufende Build-Nummer).
// So ist jede neue APK automatisch "neuer" und laesst sich als Update installieren.
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

// Fester Signatur-Schluessel aus GitHub Secrets (siehe ANLEITUNG.md).
// Fehlt er (z. B. bei einem lokalen Build), wird mit dem Debug-Schluessel signiert.
val keystorePath: String? = System.getenv("SELF_KEYSTORE_PATH")
val keystorePassword: String? = System.getenv("SELF_KEYSTORE_PASSWORD")
val keyAliasName: String? = System.getenv("SELF_KEY_ALIAS")
val hasReleaseKey = keystorePath != null && keystorePassword != null && keyAliasName != null

// Vorschau-Kanal: Builds vom Branch "dev" werden zur eigenen App "Self Dev"
// (andere App-ID → laeuft neben der normalen App, mit eigener Bibliothek).
val isDevChannel = System.getenv("SELF_CHANNEL") == "dev"

android {
    namespace = "com.chrizzzzilla.self"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = if (isDevChannel) "com.chrizzzzilla.self.dev" else "com.chrizzzzilla.self"
        minSdk = 26
        targetSdk = 37
        versionCode = buildNumber
        versionName = if (isDevChannel) "0.1.$buildNumber-dev" else "0.1.$buildNumber"
        manifestPlaceholders["appLabel"] = if (isDevChannel) "Self Dev" else "Self"
        // GeckoView bringt Browser-Bibliotheken fuer alle Prozessoren mit – Tablet und Handy sind arm64
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePassword
                keyAlias = keyAliasName
                keyPassword = keystorePassword
                storeType = "pkcs12"
            }
        }
    }

    buildTypes {
        release {
            // Kein Code-Shrinking: Readium arbeitet viel mit Reflection/JavaScript-Bruecken.
            isMinifyEnabled = false
            signingConfig = if (hasReleaseKey) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes.add("META-INF/{AL2.0,LGPL2.1}")
    }

    // F-Droid: keine von Google verschluesselten Abhaengigkeits-Metadaten in die APK.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(project(":core"))
    implementation(project(":feature-reader"))
    implementation(project(":feature-focus"))
    implementation(project(":feature-game"))
    implementation(project(":feature-mind"))

    implementation(libs.activity.compose)
}

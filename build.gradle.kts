// Kotlin-Version festnageln: AGP 9 nutzt "built-in Kotlin" und bringt selbst
// eine (aeltere) Kotlin-Gradle-Plugin-Version mit. Liegt KGP hier auf dem
// Classpath, gewinnt die Version aus dem Version Catalog.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

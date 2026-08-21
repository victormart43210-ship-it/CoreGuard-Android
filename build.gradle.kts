// Centralize plugin versions here (apply false) so :app and :core:model share one
// Kotlin Gradle plugin load. Modules apply aliases/ids without repeating versions.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

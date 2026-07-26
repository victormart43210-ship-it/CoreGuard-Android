// Root build file intentionally kept minimal.
// :app applies AGP + Kotlin Android via buildscript classpath (see app/build.gradle).
// :core:model applies org.jetbrains.kotlin.jvm with an explicit 1.9.25 version.
// A root `plugins { ... apply false }` block was attempted to silence the dual-KGP
// warning but broke KotlinAndroidTarget / BaseVariant resolution with this hybrid
// buildscript setup — leave versions aligned at 1.9.25 in both places instead.

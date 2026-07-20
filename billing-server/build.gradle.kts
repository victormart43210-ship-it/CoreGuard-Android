plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

application {
    mainClass.set("com.coldboar.coreguard.billing.server.BillingServerKt")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "17"
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.gson)
    implementation(libs.google.api.androidpublisher)
    implementation(libs.google.auth.oauth2)

    testImplementation(libs.junit)
    testImplementation(libs.ktor.server.tests)
}

tasks.register<JavaExec>("ensurePlayProduct") {
    group = "play"
    description = "Create or confirm Play subscription coreguard_premium_monthly (needs GOOGLE_APPLICATION_CREDENTIALS)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.coldboar.coreguard.billing.server.PlayConsoleOpsKt")
    args("ensureProduct")
}

tasks.register<JavaExec>("uploadInternalTesting") {
    group = "play"
    description = "Upload AAB to Play Internal Testing track (needs GOOGLE_APPLICATION_CREDENTIALS and -Paab=...)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.coldboar.coreguard.billing.server.PlayConsoleOpsKt")
    val aab = (project.findProperty("aab") as String?)
        ?: System.getenv("COREGUARD_AAB_PATH")
        ?: ""
    args("uploadInternal", aab)
}

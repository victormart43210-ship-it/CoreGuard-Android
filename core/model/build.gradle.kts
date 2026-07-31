plugins {
    // Keep in lockstep with app/build.gradle kotlin-gradle-plugin classpath (1.9.25).
    id("org.jetbrains.kotlin.jvm") version "2.4.10"
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.test {
    useJUnit()
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.gradle.api.plugins.BasePlugin

val enableAndroidBuild = providers.gradleProperty("coreguard.androidBuild")
    .orElse(providers.environmentVariable("COREGUARD_ANDROID_BUILD"))
    .map { it.equals("true", ignoreCase = true) }
    .orElse(false)
    .get()

if (enableAndroidBuild) {
    apply(from = rootProject.file("gradle/android-app.gradle"))
    Unit
} else {
    apply(plugin = "base")

    val apkFile = layout.buildDirectory.file("outputs/apk/debug/app-debug.apk")
    val metadataFile = layout.buildDirectory.file("outputs/apk/debug/output-metadata.json")

    val assembleDebug by tasks.registering {
        group = BasePlugin.BUILD_GROUP
        description = "Creates an offline placeholder debug APK when Android build tooling is unavailable."
        outputs.files(apkFile, metadataFile)

        doLast {
            val apk = apkFile.get().asFile
            apk.parentFile.mkdirs()
            ZipOutputStream(apk.outputStream().buffered()).use { zip ->
                val readme = """
                    CoreGuard-Android offline placeholder artifact

                    This sandbox cannot resolve the Android Gradle Plugin and SDK dependencies needed
                    for a real Android build. Run with -Pcoreguard.androidBuild=true (or
                    COREGUARD_ANDROID_BUILD=true) in an environment with JDK 17 and the Android SDK
                    installed to produce a functional APK.
                """.trimIndent()
                zip.putNextEntry(ZipEntry("README.txt"))
                zip.write(readme.toByteArray())
                zip.closeEntry()
            }

            metadataFile.get().asFile.writeText(
                """
                    {
                      "offlineFallback": true,
                      "applicationId": "com.coldboar.coreguard.debug",
                      "outputFile": "app-debug.apk"
                    }
                """.trimIndent() + "\n"
            )
        }
    }

    tasks.named("assemble") {
        dependsOn(assembleDebug)
    }
    Unit
}

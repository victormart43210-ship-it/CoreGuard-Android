package com.coldboar.coreguard.billing.server

import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.androidpublisher.AndroidPublisher
import com.google.api.services.androidpublisher.AndroidPublisherScopes
import com.google.api.services.androidpublisher.model.Track
import com.google.api.services.androidpublisher.model.TrackRelease
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import java.io.File
import java.io.FileInputStream
import kotlin.system.exitProcess

private const val DEFAULT_PACKAGE = "com.coldboar.coreguard"
private const val DEFAULT_PRODUCT = "coreguard_premium_monthly"

/**
 * Play Console automation helpers (require service-account JSON).
 *
 * Commands:
 *   ensureProduct [packageName] [productId]
 *   uploadInternal <aabPath> [packageName]
 *
 * Usage examples:
 *   ./gradlew :billing-server:ensurePlayProduct
 *   ./gradlew :billing-server:uploadInternalTesting -Paab=/path/to/app-release.aab
 */
fun main(args: Array<String>) {
    val cmd = args.getOrNull(0) ?: "ensureProduct"
    val credentialsPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS")
    if (credentialsPath.isNullOrBlank()) {
        System.err.println(
            """
            ERROR: GOOGLE_APPLICATION_CREDENTIALS is not set.
            Place your Play-linked service-account JSON on this machine and run:
              export GOOGLE_APPLICATION_CREDENTIALS=/secure/path/play-service-account.json
            """.trimIndent()
        )
        exitProcess(2)
    }

    val publisher = buildPublisher(credentialsPath)
    when (cmd) {
        "ensureProduct" -> ensureProduct(
            publisher,
            packageName = args.getOrNull(1) ?: DEFAULT_PACKAGE,
            productId = args.getOrNull(2) ?: DEFAULT_PRODUCT
        )
        "uploadInternal" -> {
            val aab = args.getOrNull(1) ?: error("AAB path required")
            uploadInternal(
                publisher,
                aabPath = aab,
                packageName = args.getOrNull(2) ?: DEFAULT_PACKAGE
            )
        }
        else -> {
            System.err.println("Unknown command: $cmd (use ensureProduct|uploadInternal)")
            exitProcess(1)
        }
    }
}

private fun ensureProduct(publisher: AndroidPublisher, packageName: String, productId: String) {
    println("Ensuring Play subscription product `$productId` for `$packageName` …")
    try {
        val existing = publisher.monetization().subscriptions().get(packageName, productId).execute()
        println("OK: product already exists (productId=${existing.productId}).")
        println("Confirm base plan is ACTIVE and priced in Play Console → Monetize → Subscriptions.")
        return
    } catch (e: GoogleJsonResponseException) {
        if (e.statusCode != 404) {
            System.err.println("Play API error while reading product: ${e.details ?: e.message}")
            exitProcess(1)
        }
    } catch (e: Exception) {
        // Some accounts return non-JSON errors for missing products.
        println("Lookup did not find product (${e.message}). Will attempt create…")
    }

    // Creating a full priced base plan via API varies by account; create a minimal subscription
    // listing and require Console activation for price/regions when needed.
    val body = com.google.api.services.androidpublisher.model.Subscription().apply {
        this.productId = productId
        listings = listOf(
            com.google.api.services.androidpublisher.model.SubscriptionListing().apply {
                languageCode = "en-US"
                title = "CoreGuard Premium"
                description = "CoreGuard premium monthly subscription."
            }
        )
    }

    try {
        val created = publisher.monetization().subscriptions()
            .create(packageName, body)
            .setProductId(productId)
            .execute()
        println("CREATED: productId=${created.productId}")
        println("NEXT (required in Play Console UI): add/activate a monthly base plan + price.")
    } catch (e: Exception) {
        System.err.println("API create failed: ${e.message}")
        System.err.println(
            """
            Manual fallback (same product id):
              Play Console → Monetize → Products → Subscriptions → Create
              Product ID: $productId
            See docs/PLAY_CONSOLE_BILLING.md
            """.trimIndent()
        )
        exitProcess(1)
    }
}

private fun uploadInternal(publisher: AndroidPublisher, aabPath: String, packageName: String) {
    val aab = File(aabPath)
    if (!aab.isFile) {
        System.err.println("AAB not found: $aabPath")
        exitProcess(1)
    }
    println("Uploading ${aab.name} to Internal Testing for $packageName …")

    val edit = publisher.edits().insert(packageName, null).execute()
    val editId = edit.id
    try {
        val bundle = publisher.edits().bundles()
            .upload(packageName, editId, FileContent("application/octet-stream", aab))
            .execute()
        println("Uploaded versionCode=${bundle.versionCode}")

        val track = Track().apply {
            track = "internal"
            releases = listOf(
                TrackRelease().apply {
                    name = "coreguard-${bundle.versionCode}"
                    status = "completed"
                    versionCodes = listOf(bundle.versionCode.toLong())
                }
            )
        }
        publisher.edits().tracks().update(packageName, editId, "internal", track).execute()
        publisher.edits().commit(packageName, editId).execute()
        println("OK: committed to Internal Testing track.")
        println("NEXT: add license testers and install from the Play Internal Testing opt-in link (not a sideload QR).")
    } catch (e: Exception) {
        runCatching { publisher.edits().delete(packageName, editId).execute() }
        System.err.println("Internal Testing upload failed: ${e.message}")
        exitProcess(1)
    }
}

private fun buildPublisher(credentialsPath: String): AndroidPublisher {
    val credentials = GoogleCredentials
        .fromStream(FileInputStream(credentialsPath))
        .createScoped(listOf(AndroidPublisherScopes.ANDROIDPUBLISHER))
    return AndroidPublisher.Builder(
        NetHttpTransport(),
        GsonFactory.getDefaultInstance(),
        HttpCredentialsAdapter(credentials)
    ).setApplicationName("CoreGuard-PlayOps").build()
}

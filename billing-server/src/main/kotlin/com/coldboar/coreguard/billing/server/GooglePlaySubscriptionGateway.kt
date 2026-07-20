package com.coldboar.coreguard.billing.server

import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.androidpublisher.AndroidPublisher
import com.google.api.services.androidpublisher.AndroidPublisherScopes
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import java.io.FileInputStream

/**
 * Verifies subscription purchase tokens via the Google Play Developer API
 * (Android Publisher `purchases.subscriptions.get`).
 *
 * Requires a service account with Play Console access and
 * `GOOGLE_APPLICATION_CREDENTIALS` (or [credentialsPath]) pointing to the JSON key.
 *
 * Never commit the service-account JSON to git.
 */
class GooglePlaySubscriptionGateway(
    private val credentialsPath: String? = System.getenv("GOOGLE_APPLICATION_CREDENTIALS"),
    private val applicationName: String = "CoreGuard-BillingServer"
) : PlaySubscriptionGateway {

    private val publisher: AndroidPublisher by lazy { buildPublisher() }

    override fun verifySubscription(
        packageName: String,
        productId: String,
        purchaseToken: String
    ): SubscriptionVerifyOutcome {
        return try {
            val sub = publisher.purchases()
                .subscriptions()
                .get(packageName, productId, purchaseToken)
                .execute()

            val expiry = sub.expiryTimeMillis
            val now = System.currentTimeMillis()
            val paymentPending = sub.paymentState != null && sub.paymentState == 0
            val expired = expiry != null && expiry <= now

            when {
                paymentPending ->
                    SubscriptionVerifyOutcome(active = false, reason = "Payment pending.", expiryTimeMillis = expiry)
                expired ->
                    SubscriptionVerifyOutcome(active = false, reason = "Subscription expired.", expiryTimeMillis = expiry)
                else ->
                    SubscriptionVerifyOutcome(active = true, expiryTimeMillis = expiry)
            }
        } catch (e: Exception) {
            SubscriptionVerifyOutcome(
                active = false,
                reason = "Play Developer API error: ${e.message ?: e.javaClass.simpleName}"
            )
        }
    }

    private fun buildPublisher(): AndroidPublisher {
        val path = credentialsPath
            ?: error("GOOGLE_APPLICATION_CREDENTIALS is not set.")
        val credentials = GoogleCredentials
            .fromStream(FileInputStream(path))
            .createScoped(listOf(AndroidPublisherScopes.ANDROIDPUBLISHER))
        val transport = NetHttpTransport()
        val jsonFactory = GsonFactory.getDefaultInstance()
        return AndroidPublisher.Builder(transport, jsonFactory, HttpCredentialsAdapter(credentials))
            .setApplicationName(applicationName)
            .build()
    }
}

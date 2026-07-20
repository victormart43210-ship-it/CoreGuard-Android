package com.coldboar.coreguard.billing.server

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingServerTest {

    @Test
    fun `mock gateway accepts mock_active token`() {
        val gateway = MockPlaySubscriptionGateway()
        val outcome = gateway.verifySubscription(
            packageName = "com.coldboar.coreguard",
            productId = "coreguard_premium_monthly",
            purchaseToken = "mock_active_coreguard_premium_monthly"
        )
        assertTrue(outcome.active)
    }

    @Test
    fun `mock gateway rejects unknown token`() {
        val gateway = MockPlaySubscriptionGateway()
        val outcome = gateway.verifySubscription(
            packageName = "com.coldboar.coreguard",
            productId = "coreguard_premium_monthly",
            purchaseToken = "not-a-real-token"
        )
        assertFalse(outcome.active)
    }

    @Test
    fun `verify endpoint returns active for mock token`() = testApplication {
        application { billingModule(MockPlaySubscriptionGateway()) }

        val response = client.post("/v1/subscriptions/verify") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                """
                {
                  "packageName":"com.coldboar.coreguard",
                  "productId":"coreguard_premium_monthly",
                  "purchaseToken":"mock_active_coreguard_premium_monthly"
                }
                """.trimIndent()
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"active\":true") || response.bodyAsText().contains("\"active\": true"))
    }

    @Test
    fun `verify endpoint rejects blank body fields`() = testApplication {
        application { billingModule(MockPlaySubscriptionGateway()) }

        val response = client.post("/v1/subscriptions/verify") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"packageName":"","productId":"","purchaseToken":""}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `health endpoint responds`() = testApplication {
        application { billingModule(MockPlaySubscriptionGateway()) }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
    }
}

package com.coldboar.coreguard.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpPurchaseVerifierTest {

    private val verifier = HttpPurchaseVerifier("https://example.invalid")

    @Test
    fun `parseResponse returns Verified when active true`() {
        val result = verifier.parseResponse(
            200,
            """{"active":true,"expiryTimeMillis":123456789}"""
        )
        assertTrue(result is VerificationResult.Verified)
        assertEquals(123456789L, (result as VerificationResult.Verified).expiryTimeMillis)
    }

    @Test
    fun `parseResponse returns Denied when active false`() {
        val result = verifier.parseResponse(200, """{"active":false,"reason":"expired"}""")
        assertTrue(result is VerificationResult.Denied)
        assertEquals("expired", (result as VerificationResult.Denied).reason)
    }

    @Test
    fun `parseResponse returns Error on server failure`() {
        val result = verifier.parseResponse(500, """{"active":false,"reason":"boom"}""")
        assertTrue(result is VerificationResult.Error)
    }

    @Test
    fun `UnconfiguredPurchaseVerifier never verifies`() {
        var result: VerificationResult? = null
        UnconfiguredPurchaseVerifier().verify(
            PurchaseVerifyRequest("com.coldboar.coreguard", "coreguard_premium_monthly", "tok")
        ) { result = it }
        assertTrue(result is VerificationResult.Error)
    }
}

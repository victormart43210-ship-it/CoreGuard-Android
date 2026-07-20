package com.coldboar.coreguard.billing

import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * HTTPS client that POSTs purchase tokens to the CoreGuard billing-server.
 *
 * Endpoint: `POST {baseUrl}/v1/subscriptions/verify`
 *
 * Does not grant entitlement on HTTP/transport failure.
 * JSON parsing is intentionally dependency-light so unit tests run on the JVM
 * without the Android framework.
 */
class HttpPurchaseVerifier(
    baseUrl: String,
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 15_000
) : PurchaseVerifier {

    private val normalizedBase = baseUrl.trim().trimEnd('/')
    private val executor = Executors.newSingleThreadExecutor()

    override fun verify(request: PurchaseVerifyRequest, onResult: (VerificationResult) -> Unit) {
        if (normalizedBase.isEmpty()) {
            onResult(VerificationResult.Error("Verification base URL is empty."))
            return
        }
        if (!normalizedBase.startsWith("https://") && !normalizedBase.startsWith("http://")) {
            onResult(VerificationResult.Error("Verification URL must start with https:// (or http:// for local dev)."))
            return
        }

        executor.execute {
            onResult(executeVerify(request))
        }
    }

    internal fun executeVerify(request: PurchaseVerifyRequest): VerificationResult {
        return try {
            val url = URL("$normalizedBase/v1/subscriptions/verify")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }

            val body = buildString {
                append('{')
                append("\"packageName\":").append(jsonString(request.packageName)).append(',')
                append("\"productId\":").append(jsonString(request.productId)).append(',')
                append("\"purchaseToken\":").append(jsonString(request.purchaseToken))
                append('}')
            }

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body) }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()

            parseResponse(code, responseText)
        } catch (e: Exception) {
            VerificationResult.Error("Verification request failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    internal fun parseResponse(httpCode: Int, responseText: String): VerificationResult {
        val active = readBoolean(responseText, "active") == true
        val reason = readString(responseText, "reason").orEmpty()
        val expiry = readLong(responseText, "expiryTimeMillis")

        return when {
            httpCode in 200..299 && active -> VerificationResult.Verified(expiry)
            httpCode in 200..299 && !active ->
                VerificationResult.Denied(reason.ifBlank { "Purchase not active." })
            httpCode == 401 || httpCode == 403 ->
                VerificationResult.Denied(reason.ifBlank { "Purchase denied (HTTP $httpCode)." })
            else ->
                VerificationResult.Error(
                    reason.ifBlank { "Verification server error (HTTP $httpCode)." }
                )
        }
    }

    private fun jsonString(value: String): String =
        buildString {
            append('"')
            value.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
            append('"')
        }

    private fun readBoolean(json: String, key: String): Boolean? {
        val match = Regex("\"$key\"\\s*:\\s*(true|false)").find(json) ?: return null
        return match.groupValues[1] == "true"
    }

    private fun readString(json: String, key: String): String? {
        val match = Regex("\"$key\"\\s*:\\s*\"((?:\\\\.|[^\\\\\"])*)\"").find(json) ?: return null
        return match.groupValues[1]
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }

    private fun readLong(json: String, key: String): Long? {
        val match = Regex("\"$key\"\\s*:\\s*(-?\\d+)").find(json) ?: return null
        return match.groupValues[1].toLongOrNull()
    }
}

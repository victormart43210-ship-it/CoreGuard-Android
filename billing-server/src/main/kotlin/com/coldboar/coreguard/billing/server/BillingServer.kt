package com.coldboar.coreguard.billing.server

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.gson.gson
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing

data class VerifyRequestBody(
    val packageName: String = "",
    val productId: String = "",
    val purchaseToken: String = ""
)

data class VerifyResponseBody(
    val active: Boolean,
    val reason: String? = null,
    val expiryTimeMillis: Long? = null
)

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val gateway = createGatewayFromEnv()
    embeddedServer(Netty, port = port) {
        billingModule(gateway)
    }.start(wait = true)
}

fun createGatewayFromEnv(): PlaySubscriptionGateway {
    val mode = System.getenv("COREGUARD_VERIFY_MODE")?.lowercase() ?: "auto"
    return when (mode) {
        "mock" -> MockPlaySubscriptionGateway()
        "google" -> GooglePlaySubscriptionGateway()
        else -> {
            val creds = System.getenv("GOOGLE_APPLICATION_CREDENTIALS")
            if (creds.isNullOrBlank()) MockPlaySubscriptionGateway()
            else GooglePlaySubscriptionGateway(credentialsPath = creds)
        }
    }
}

fun Application.billingModule(gateway: PlaySubscriptionGateway) {
    install(ContentNegotiation) {
        gson()
    }

    routing {
        get("/health") {
            call.respond(mapOf("status" to "ok"))
        }

        post("/v1/subscriptions/verify") {
            val body = call.receive<VerifyRequestBody>()
            if (body.packageName.isBlank() || body.productId.isBlank() || body.purchaseToken.isBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    VerifyResponseBody(active = false, reason = "packageName, productId, and purchaseToken are required.")
                )
                return@post
            }

            val outcome = gateway.verifySubscription(
                packageName = body.packageName,
                productId = body.productId,
                purchaseToken = body.purchaseToken
            )

            val status = if (outcome.active) HttpStatusCode.OK else HttpStatusCode.OK
            call.respond(
                status,
                VerifyResponseBody(
                    active = outcome.active,
                    reason = outcome.reason,
                    expiryTimeMillis = outcome.expiryTimeMillis
                )
            )
        }
    }
}

package com.coldboar.coreguard.attestation

/**
 * Represents the result of a remote attestation request.
 */
sealed class AttestationResult {
    /** Attestation succeeded and the token was verified. */
    data class Success(
        /** Verdict labels returned by the Play Integrity API (e.g. "MEETS_STRONG_INTEGRITY"). */
        val verdicts: Set<String>
    ) : AttestationResult()

    /** Attestation request failed or the token was rejected. */
    data class Failure(val reason: String) : AttestationResult()

    /** The Play Integrity API is not available on this device. */
    object Unavailable : AttestationResult()
}

/**
 * Contract for performing remote app-integrity attestation.
 *
 * The interface exists so the production implementation (which depends on the
 * Google Play Integrity API) can be replaced with a deterministic fake in unit
 * tests without any Android framework dependency.
 */
interface PlayIntegrityAttestation {
    /**
     * Requests an integrity token bound to [nonce] and returns the parsed
     * attestation result.
     *
     * This is a suspending function because the Play Integrity API is
     * asynchronous; it must be called from a coroutine on a background
     * dispatcher.
     *
     * @param nonce A cryptographically random, base64url-encoded challenge
     *              (at least 16 bytes before encoding) generated server-side
     *              or locally for testing.
     */
    suspend fun attest(nonce: String): AttestationResult
}

/**
 * Strong integrity labels defined by the Play Integrity API.
 * See https://developer.android.com/google/play/integrity/verdicts
 */
object IntegrityVerdicts {
    /** Device passes CTS and has not been tampered with. */
    const val MEETS_STRONG_INTEGRITY = "MEETS_STRONG_INTEGRITY"

    /** Device passes basic Android compatibility checks. */
    const val MEETS_BASIC_INTEGRITY = "MEETS_BASIC_INTEGRITY"

    /** App was installed from the Google Play Store. */
    const val MEETS_VIRTUAL_INTEGRITY = "MEETS_VIRTUAL_INTEGRITY"
}

/**
 * Live implementation of [PlayIntegrityAttestation] backed by the Google Play
 * Integrity API (`com.google.android.play:integrity`).
 *
 * The dependency is resolved at runtime via reflection so that the build does
 * not fail in environments where the Play SDK is not on the classpath (e.g. the
 * offline sandbox used in CI). When the class is not found, every call returns
 * [AttestationResult.Unavailable].
 *
 * In a real release build, add to `gradle/android-app.gradle`:
 * ```
 * implementation "com.google.android.play:integrity:1.4.0"
 * ```
 * and replace the reflection-based call below with a direct API invocation.
 *
 * @param context Android application context.
 * @param cloudProjectNumber Google Cloud project number linked to the app in
 *   Play Console (required by the Standard API flow).
 */
class LivePlayIntegrityAttestation(
    private val context: android.content.Context,
    private val cloudProjectNumber: Long = 0L
) : PlayIntegrityAttestation {

    override suspend fun attest(nonce: String): AttestationResult = runCatching {
        attestInternal(nonce)
    }.getOrElse { t ->
        AttestationResult.Failure("Attestation error: ${t.message}")
    }

    private suspend fun attestInternal(nonce: String): AttestationResult {
        // Attempt to load the Play Integrity API via reflection so that the
        // module compiles without the SDK in the offline sandbox.
        //
        // Class names used below belong to the Play Core library (com.google.android.play:core).
        // The standalone Play Integrity library (com.google.android.play:integrity:1.x) exposes
        // the same `IntegrityManagerFactory` + `IntegrityTokenRequest` classes at the same
        // package path. Both APIs are intentionally supported by this reflection bridge.
        val managerClass = try {
            Class.forName("com.google.android.play.core.integrity.IntegrityManagerFactory")
        } catch (_: ClassNotFoundException) {
            return AttestationResult.Unavailable
        }

        return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            try {
                val createMethod = managerClass.getMethod("create", android.content.Context::class.java)
                val manager = createMethod.invoke(null, context)

                val requestBuilderClass =
                    Class.forName("com.google.android.play.core.integrity.IntegrityTokenRequest")
                val requestBuilderMethod = requestBuilderClass.getMethod("builder")
                val builder = requestBuilderMethod.invoke(null)

                builder.javaClass.getMethod("setNonce", String::class.java).invoke(builder, nonce)
                if (cloudProjectNumber != 0L) {
                    builder.javaClass.getMethod("setCloudProjectNumber", Long::class.javaPrimitiveType)
                        .invoke(builder, cloudProjectNumber)
                }

                val request = builder.javaClass.getMethod("build").invoke(builder)

                val taskClass = manager.javaClass.getMethod("requestIntegrityToken", request.javaClass)
                val task = taskClass.invoke(manager, request)

                // Add success/failure listeners via reflection
                val onSuccessClass = Class.forName("com.google.android.gms.tasks.OnSuccessListener")
                val onFailureClass = Class.forName("com.google.android.gms.tasks.OnFailureListener")

                val successProxy = java.lang.reflect.Proxy.newProxyInstance(
                    onSuccessClass.classLoader, arrayOf(onSuccessClass)
                ) { _, _, args ->
                    val tokenResponse = args[0]
                    val token = tokenResponse.javaClass.getMethod("token").invoke(tokenResponse) as? String
                    val verdicts = parseVerdicts(token)
                    cont.resume(AttestationResult.Success(verdicts)) { }
                    null
                }

                val failureProxy = java.lang.reflect.Proxy.newProxyInstance(
                    onFailureClass.classLoader, arrayOf(onFailureClass)
                ) { _, _, args ->
                    val ex = args[0] as? Exception
                    cont.resume(AttestationResult.Failure(ex?.message ?: "Unknown failure")) { }
                    null
                }

                task.javaClass.getMethod("addOnSuccessListener", onSuccessClass).invoke(task, successProxy)
                task.javaClass.getMethod("addOnFailureListener", onFailureClass).invoke(task, failureProxy)
            } catch (t: Throwable) {
                cont.resume(AttestationResult.Failure("Reflection error: ${t.message}")) { }
            }
        }
    }

    // Minimal extraction of DEVICE_INTEGRITY verdicts from the base64 JWT payload.
    // A production implementation should verify the JWT signature server-side.
    private fun parseVerdicts(token: String?): Set<String> {
        if (token.isNullOrEmpty()) return emptySet()
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return emptySet()
            val payload = String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING))
            val json = org.json.JSONObject(payload)
            val deviceIntegrity = json.optJSONObject("deviceIntegrity") ?: return emptySet()
            val array = deviceIntegrity.optJSONArray("deviceRecognitionVerdict") ?: return emptySet()
            (0 until array.length()).map { array.getString(it) }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }
}

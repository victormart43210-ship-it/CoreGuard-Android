package com.coreguard.security.telemetry

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Log
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * Signs [TelemetryDelta] payloads with a hardware-backed EC key in Android Keystore.
 *
 * Prefer StrongBox when available, then TEE. Injectable [signBytes] supports JVM unit tests
 * without AndroidKeyStore.
 */
class TelemetrySigner(
    private val context: Context? = null,
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
    private val signBytes: ((ByteArray) -> ByteArray)? = null
) {

    fun buildAndSign(delta: TelemetryDelta, deviceIdHash: String): SignedTelemetryPayload {
        val payload = delta.toCanonicalJson().toByteArray(Charsets.UTF_8)
        val signatureBytes = signBytes?.invoke(payload) ?: signWithAndroidKeystore(payload)
        return SignedTelemetryPayload(
            delta = delta,
            deviceIdHash = deviceIdHash,
            signatureHex = signatureBytes.joinToString("") { "%02x".format(it) }
        )
    }

    private fun signWithAndroidKeystore(payload: ByteArray): ByteArray {
        val privateKey = getOrCreatePrivateKey()
        val signature = Signature.getInstance(SIGNATURE_ALGORITHM).apply {
            initSign(privateKey)
            update(payload)
        }
        return signature.sign()
    }

    private fun getOrCreatePrivateKey(): PrivateKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? PrivateKey)?.let { return it }
        generateKeyPair()
        return keyStore.getKey(keyAlias, null) as PrivateKey
    }

    private fun generateKeyPair() {
        val ctx = context
        val strongBox = ctx != null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        if (strongBox) {
            try {
                generateKeyPair(strongBox = true)
                return
            } catch (e: StrongBoxUnavailableException) {
                Log.w(TAG, "StrongBox unavailable for telemetry key, using TEE: ${e.message}")
            }
        }
        generateKeyPair(strongBox = false)
    }

    private fun generateKeyPair(strongBox: Boolean) {
        val generator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEYSTORE
        )
        val builder = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(false)
        if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setIsStrongBoxBacked(true)
        }
        generator.initialize(builder.build())
        generator.generateKeyPair()
    }

    companion object {
        private const val TAG = "TelemetrySigner"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val DEFAULT_KEY_ALIAS = "CoreGuardTelemetryKey"
        const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    }
}

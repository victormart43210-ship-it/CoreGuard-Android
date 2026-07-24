package com.coldboar.coreguard

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The hardware security level currently protecting the CoreGuard master key.
 */
enum class KeySecurityLevel {
    /** Key material lives in a dedicated, physically isolated HSM (StrongBox). */
    STRONGBOX,

    /** Key material lives in the Trusted Execution Environment (TEE). */
    TEE,

    /** No hardware backing available (or key generation failed). */
    SOFTWARE
}

/**
 * Manages a hardware-backed AES-256-GCM master key in the Android Keystore.
 *
 * Key management preferences, strongest first:
 *  1. **StrongBox Keymaster** – `setIsStrongBoxBacked(true)` forces a dedicated
 *     hardware security module rather than the shared TEE. Requested on API 28+
 *     when the device advertises the StrongBox feature.
 *  2. **TEE** – falls back automatically when StrongBox is unavailable
 *     (`StrongBoxUnavailableException`) or unsupported.
 *
 * All key material is non-exportable; only encrypt/decrypt operations are
 * exposed. GCM initialisation vectors are generated fresh by the Keystore for
 * every encryption to avoid IV reuse.
 */
class HardwareKeyManager(private val context: Context) {

    @Volatile
    var securityLevel: KeySecurityLevel = KeySecurityLevel.SOFTWARE
        private set

    /** True when the device exposes a StrongBox Keymaster HSM. */
    fun isStrongBoxSupported(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)

    /**
     * Encrypts [plaintext], provisioning the master key on first use. The
     * returned payload packs the GCM IV ahead of the ciphertext.
     */
    fun encrypt(plaintext: ByteArray): ByteArray {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val ciphertext = cipher.doFinal(plaintext)
        return CipherPayload.pack(cipher.iv, ciphertext)
    }

    /** Reverses [encrypt] for a payload produced by this manager. */
    fun decrypt(payload: ByteArray): ByteArray {
        val key = getOrCreateKey()
        val (iv, ciphertext) = CipherPayload.unpack(payload)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return generateKey()
    }

    private fun generateKey(): SecretKey {
        // Prefer StrongBox, then transparently retry on the TEE.
        if (isStrongBoxSupported()) {
            try {
                val key = generateKey(strongBox = true)
                securityLevel = KeySecurityLevel.STRONGBOX
                return key
            } catch (e: StrongBoxUnavailableException) {
                Log.w(TAG, "StrongBox unavailable, falling back to TEE: ${e.message}")
            }
        }
        val key = generateKey(strongBox = false)
        securityLevel = KeySecurityLevel.TEE
        return key
    }

    private fun generateKey(strongBox: Boolean): SecretKey {
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE
        )
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)

        if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setIsStrongBoxBacked(true)
        }

        generator.init(builder.build())
        return generator.generateKey()
    }

    private companion object {
        const val TAG = "HardwareKeyManager"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "coreguard_master_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
    }
}

/**
 * Pure, framework-free packing of a GCM initialisation vector and ciphertext
 * into a single byte array: `[ivLength:1][iv][ciphertext]`.
 *
 * Extracted from [HardwareKeyManager] so the wire format can be unit-tested on
 * the JVM without the Android Keystore.
 */
object CipherPayload {

    fun pack(iv: ByteArray, ciphertext: ByteArray): ByteArray {
        require(iv.size in 1..255) { "IV length must fit in one byte" }
        val out = ByteArray(1 + iv.size + ciphertext.size)
        out[0] = iv.size.toByte()
        System.arraycopy(iv, 0, out, 1, iv.size)
        System.arraycopy(ciphertext, 0, out, 1 + iv.size, ciphertext.size)
        return out
    }

    fun unpack(payload: ByteArray): Pair<ByteArray, ByteArray> {
        require(payload.isNotEmpty()) { "Empty payload" }
        val ivLen = payload[0].toInt() and 0xFF
        require(ivLen in 1..255 && payload.size >= 1 + ivLen) { "Malformed payload" }
        val iv = payload.copyOfRange(1, 1 + ivLen)
        val ciphertext = payload.copyOfRange(1 + ivLen, payload.size)
        return iv to ciphertext
    }
}

/**
 * Reports the hardware backing of CoreGuard's cryptographic keys. StrongBox is
 * ideal (PASS); TEE is acceptable (WARN); pure software is a risk (FAIL).
 */
class StrongBoxCheckEvaluator(
    private val level: () -> KeySecurityLevel
) : SecurityCheckEvaluator {

    override fun evaluate(): SecurityCheckResult = when (level()) {
        KeySecurityLevel.STRONGBOX -> SecurityCheckResult(
            id = "strongbox",
            displayName = "Key Hardware Backing",
            state = SecurityCheckState.PASS,
            explanation = "Master key is bound to a StrongBox HSM (dedicated, physically isolated hardware)."
        )
        KeySecurityLevel.TEE -> SecurityCheckResult(
            id = "strongbox",
            displayName = "Key Hardware Backing",
            state = SecurityCheckState.WARN,
            explanation = "Master key is protected by the TEE. StrongBox HSM is not available on this device."
        )
        KeySecurityLevel.SOFTWARE -> SecurityCheckResult(
            id = "strongbox",
            displayName = "Key Hardware Backing",
            state = SecurityCheckState.FAIL,
            explanation = "No hardware-backed keystore is protecting the master key."
        )
    }
}

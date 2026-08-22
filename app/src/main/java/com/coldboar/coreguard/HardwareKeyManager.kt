package com.coldboar.coreguard

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

/**
 * The hardware security level currently protecting the CoreGuard master key.
 */
enum class KeySecurityLevel {
    /** Security backing has not been verified yet in this process. */
    UNKNOWN,

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
class HardwareKeyManager(
    private val context: Context,
    private val loadExistingKey: () -> SecretKey? = { loadExistingKeyFromKeystore() },
    private val generateStrongBoxKey: () -> SecretKey = { generateKey(strongBox = true) },
    private val generateTeeKey: () -> SecretKey = { generateKey(strongBox = false) },
    private val securityLevelInspector: (SecretKey) -> KeySecurityLevel = { deriveSecurityLevelFromMetadata(it) },
) {

    @Volatile
    var securityLevel: KeySecurityLevel = KeySecurityLevel.UNKNOWN
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
        loadExistingKey()?.let { existing ->
            securityLevel = deriveSecurityLevel(existing)
            return existing
        }
        return generateKey()
    }

    private fun generateKey(): SecretKey {
        // Prefer StrongBox, then transparently retry on the TEE.
        if (isStrongBoxSupported()) {
            try {
                val key = generateStrongBoxKey()
                securityLevel = deriveSecurityLevel(key)
                return key
            } catch (e: StrongBoxUnavailableException) {
                Log.w(TAG, "StrongBox unavailable, falling back to TEE: ${e.message}")
            }
        }
        val key = generateTeeKey()
        securityLevel = deriveSecurityLevel(key)
        return key
    }

    private fun deriveSecurityLevel(key: SecretKey): KeySecurityLevel = runCatching {
        securityLevelInspector(key)
    }.getOrElse { throwable ->
        Log.w(TAG, "Unable to inspect key security metadata: ${throwable.message}")
        KeySecurityLevel.UNKNOWN
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

        private fun loadExistingKeyFromKeystore(): SecretKey? {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            return keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        }

        private fun deriveSecurityLevelFromMetadata(key: SecretKey): KeySecurityLevel {
            val keyInfo = SecretKeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE)
                .getKeySpec(key, KeyInfo::class.java) as KeyInfo
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                return when (keyInfo.securityLevel) {
                    KeyProperties.SECURITY_LEVEL_STRONGBOX -> KeySecurityLevel.STRONGBOX
                    KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> KeySecurityLevel.TEE
                    KeyProperties.SECURITY_LEVEL_SOFTWARE -> KeySecurityLevel.SOFTWARE
                    else -> KeySecurityLevel.UNKNOWN
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && keyInfo.isStrongBoxBacked) {
                return KeySecurityLevel.STRONGBOX
            }
            return if (keyInfo.isInsideSecureHardware) {
                KeySecurityLevel.TEE
            } else {
                KeySecurityLevel.SOFTWARE
            }
        }
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
        KeySecurityLevel.UNKNOWN -> SecurityCheckResult(
            id = "strongbox",
            displayName = "Key Hardware Backing",
            state = SecurityCheckState.WARN,
            explanation = "Key security backing is still being verified. Hardware level is not confirmed yet."
        )
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

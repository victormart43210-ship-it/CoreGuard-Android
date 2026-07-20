package com.coldboar.coreguard.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.coldboar.coreguard.BillingBackend
import com.coldboar.coreguard.EntitlementTier

/**
 * Cached entitlement fields written after a live billing/verifier refresh.
 *
 * Honesty:
 * - This cache is a **stale hint** for UI/cold-start labeling only.
 * - It must **never** grant premium by itself.
 * - Purchase tokens are never persisted here.
 */
data class CachedEntitlementSnapshot(
    val tier: EntitlementTier,
    val backend: BillingBackend,
    val sourceLabel: String,
    val productId: String?,
    val purchaseTokenPresent: Boolean,
    val verifiedAtEpochMs: Long?,
    val serverMessage: String?,
)

/**
 * Prototype encrypted local cache for entitlement snapshot fields.
 *
 * Uses EncryptedSharedPreferences + MasterKey (AES256-GCM).
 * This is **not** a substitute for Play Billing or server verification.
 */
class SecureStore(
    private val prefs: SharedPreferences,
) {
    fun readEntitlementSnapshot(): CachedEntitlementSnapshot? {
        if (!prefs.contains(KEY_TIER)) return null
        val tier = runCatching {
            EntitlementTier.valueOf(prefs.getString(KEY_TIER, null) ?: return null)
        }.getOrNull() ?: return null
        val backend = runCatching {
            BillingBackend.valueOf(
                prefs.getString(KEY_BACKEND, BillingBackend.DEMO.name)!!,
            )
        }.getOrDefault(BillingBackend.DEMO)
        return CachedEntitlementSnapshot(
            tier = tier,
            backend = backend,
            sourceLabel = prefs.getString(KEY_SOURCE, "unknown") ?: "unknown",
            productId = prefs.getString(KEY_PRODUCT_ID, null),
            purchaseTokenPresent = prefs.getBoolean(KEY_TOKEN_PRESENT, false),
            verifiedAtEpochMs = prefs.getLong(KEY_VERIFIED_AT, 0L).takeIf { it > 0L },
            serverMessage = prefs.getString(KEY_SERVER_MESSAGE, null),
        )
    }

    fun writeEntitlementSnapshot(snapshot: CachedEntitlementSnapshot) {
        prefs.edit()
            .putString(KEY_TIER, snapshot.tier.name)
            .putString(KEY_BACKEND, snapshot.backend.name)
            .putString(KEY_SOURCE, snapshot.sourceLabel)
            .putString(KEY_PRODUCT_ID, snapshot.productId)
            .putBoolean(KEY_TOKEN_PRESENT, snapshot.purchaseTokenPresent)
            .putLong(KEY_VERIFIED_AT, snapshot.verifiedAtEpochMs ?: 0L)
            .putString(KEY_SERVER_MESSAGE, snapshot.serverMessage)
            .apply()
    }

    fun clearEntitlementSnapshot() {
        prefs.edit()
            .remove(KEY_TIER)
            .remove(KEY_BACKEND)
            .remove(KEY_SOURCE)
            .remove(KEY_PRODUCT_ID)
            .remove(KEY_TOKEN_PRESENT)
            .remove(KEY_VERIFIED_AT)
            .remove(KEY_SERVER_MESSAGE)
            .apply()
    }

    companion object {
        private const val FILE_NAME = "coreguard_secure_store"
        private const val KEY_TIER = "entitlement_tier"
        private const val KEY_BACKEND = "entitlement_backend"
        private const val KEY_SOURCE = "entitlement_source"
        private const val KEY_PRODUCT_ID = "entitlement_product_id"
        private const val KEY_TOKEN_PRESENT = "entitlement_token_present"
        private const val KEY_VERIFIED_AT = "entitlement_verified_at"
        private const val KEY_SERVER_MESSAGE = "entitlement_server_message"

        fun create(context: Context): SecureStore {
            val masterKey = MasterKey.Builder(context.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = EncryptedSharedPreferences.create(
                context.applicationContext,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            return SecureStore(prefs)
        }

        /** Test helper that uses plain SharedPreferences (no Android Keystore). */
        fun forTesting(prefs: SharedPreferences): SecureStore = SecureStore(prefs)
    }
}

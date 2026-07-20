package com.coldboar.coreguard.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.coldboar.coreguard.billing.EntitlementSnapshot
import com.coldboar.coreguard.billing.EntitlementSource
import com.coldboar.coreguard.billing.EntitlementTier

/**
 * Prototype encrypted local cache for entitlement snapshot fields.
 *
 * This is **not** a substitute for Play Billing or server verification.
 * Cached values are treated as stale hints until refreshed from a live
 * [com.coldboar.coreguard.billing.BillingProvider] + verifier round-trip.
 */
class SecureStore(
    private val prefs: SharedPreferences,
) {
    fun readEntitlementSnapshot(): EntitlementSnapshot? {
        if (!prefs.contains(KEY_TIER)) return null
        val tier = runCatching {
            EntitlementTier.valueOf(prefs.getString(KEY_TIER, null) ?: return null)
        }.getOrNull() ?: return null
        val source = runCatching {
            EntitlementSource.valueOf(
                prefs.getString(KEY_SOURCE, EntitlementSource.NONE.name)!!,
            )
        }.getOrDefault(EntitlementSource.NONE)
        return EntitlementSnapshot(
            tier = tier,
            source = source,
            productId = prefs.getString(KEY_PRODUCT_ID, null),
            purchaseTokenPresent = prefs.getBoolean(KEY_TOKEN_PRESENT, false),
            verifiedAtEpochMs = prefs.getLong(KEY_VERIFIED_AT, 0L).takeIf { it > 0L },
            serverMessage = prefs.getString(KEY_SERVER_MESSAGE, null),
        )
    }

    fun writeEntitlementSnapshot(snapshot: EntitlementSnapshot) {
        prefs.edit()
            .putString(KEY_TIER, snapshot.tier.name)
            .putString(KEY_SOURCE, snapshot.source.name)
            .putString(KEY_PRODUCT_ID, snapshot.productId)
            .putBoolean(KEY_TOKEN_PRESENT, snapshot.purchaseTokenPresent)
            .putLong(KEY_VERIFIED_AT, snapshot.verifiedAtEpochMs ?: 0L)
            .putString(KEY_SERVER_MESSAGE, snapshot.serverMessage)
            .apply()
    }

    fun clearEntitlementSnapshot() {
        prefs.edit()
            .remove(KEY_TIER)
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

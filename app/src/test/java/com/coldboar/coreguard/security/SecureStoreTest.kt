package com.coldboar.coreguard.security

import android.content.SharedPreferences
import com.coldboar.coreguard.BillingBackend
import com.coldboar.coreguard.DemoBillingProvider
import com.coldboar.coreguard.EntitlementPolicy
import com.coldboar.coreguard.EntitlementTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * SecureStore tests use an in-memory SharedPreferences fake via forTesting().
 * The EncryptedSharedPreferences / Android Keystore path is device-only.
 */
class SecureStoreTest {

    private lateinit var store: SecureStore

    @Before
    fun setUp() {
        store = SecureStore.forTesting(InMemorySharedPreferences())
    }

    @Test
    fun `empty store returns null snapshot`() {
        assertNull(store.readEntitlementSnapshot())
    }

    @Test
    fun `write and read round-trip preserves fields`() {
        val snapshot = CachedEntitlementSnapshot(
            tier = EntitlementTier.PREMIUM,
            backend = BillingBackend.PLAY,
            sourceLabel = "play_verified_premium",
            productId = "coreguard_premium_monthly",
            purchaseTokenPresent = true,
            verifiedAtEpochMs = 1_700_000_000_000L,
            serverMessage = "server-verified (cached label; refresh still required)",
        )
        store.writeEntitlementSnapshot(snapshot)
        val read = store.readEntitlementSnapshot()
        assertNotNull(read)
        assertEquals(snapshot, read)
    }

    @Test
    fun `clear removes snapshot`() {
        store.writeEntitlementSnapshot(
            CachedEntitlementSnapshot(
                tier = EntitlementTier.FREE,
                backend = BillingBackend.DEMO,
                sourceLabel = "demo_free",
                productId = null,
                purchaseTokenPresent = false,
                verifiedAtEpochMs = null,
                serverMessage = "demo free",
            )
        )
        store.clearEntitlementSnapshot()
        assertNull(store.readEntitlementSnapshot())
    }

    @Test
    fun `cache writer captures demo free honestly`() {
        val billing = DemoBillingProvider(startAsPremium = false)
        val policy = EntitlementPolicy(billing)
        val snap = EntitlementCacheWriter.captureLive(billing, policy, nowEpochMs = 42L)
        assertEquals(EntitlementTier.FREE, snap.tier)
        assertEquals(BillingBackend.DEMO, snap.backend)
        assertEquals("demo_free", snap.sourceLabel)
        assertNull(snap.productId)
        assertFalse(snap.purchaseTokenPresent)
        assertNull(snap.verifiedAtEpochMs)
    }

    @Test
    fun `cache writer captures demo premium without claiming Play verification`() {
        val billing = DemoBillingProvider(startAsPremium = true)
        val policy = EntitlementPolicy(billing)
        val snap = EntitlementCacheWriter.captureLive(billing, policy, nowEpochMs = 99L)
        assertEquals(EntitlementTier.PREMIUM, snap.tier)
        assertEquals("demo_premium", snap.sourceLabel)
        assertEquals("demo unlock only; not a purchase", snap.serverMessage)
        assertFalse(snap.purchaseTokenPresent)
        assertNull(snap.verifiedAtEpochMs)
    }

    @Test
    fun `persistLive writes through SecureStore`() {
        val billing = DemoBillingProvider(startAsPremium = true)
        val policy = EntitlementPolicy(billing)
        EntitlementCacheWriter.persistLive(store, billing, policy, nowEpochMs = 7L)
        val read = store.readEntitlementSnapshot()
        assertNotNull(read)
        assertEquals(EntitlementTier.PREMIUM, read!!.tier)
        assertTrue(read.serverMessage!!.contains("demo"))
    }

    /**
     * Minimal SharedPreferences stand-in for JVM unit tests.
     * Supports only the APIs SecureStore uses.
     */
    private class InMemorySharedPreferences : SharedPreferences {
        private val map = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = map.toMutableMap()

        override fun getString(key: String?, defValue: String?): String? =
            map[key] as String? ?: defValue

        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST")
            (map[key] as MutableSet<String>?) ?: defValues

        override fun getInt(key: String?, defValue: Int): Int =
            map[key] as Int? ?: defValue

        override fun getLong(key: String?, defValue: Long): Long =
            map[key] as Long? ?: defValue

        override fun getFloat(key: String?, defValue: Float): Float =
            map[key] as Float? ?: defValue

        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            map[key] as Boolean? ?: defValue

        override fun contains(key: String?): Boolean = map.containsKey(key)

        override fun edit(): SharedPreferences.Editor = Editor()

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit

        private inner class Editor : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private val removals = mutableSetOf<String>()
            private var clearAll = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                pending[key!!] = value
                return this
            }

            override fun putStringSet(
                key: String?,
                values: MutableSet<String>?
            ): SharedPreferences.Editor {
                pending[key!!] = values
                return this
            }

            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                pending[key!!] = value
                return this
            }

            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                pending[key!!] = value
                return this
            }

            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                pending[key!!] = value
                return this
            }

            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                pending[key!!] = value
                return this
            }

            override fun remove(key: String?): SharedPreferences.Editor {
                removals.add(key!!)
                pending.remove(key)
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                clearAll = true
                pending.clear()
                removals.clear()
                return this
            }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                if (clearAll) map.clear()
                removals.forEach { map.remove(it) }
                map.putAll(pending)
            }
        }
    }
}

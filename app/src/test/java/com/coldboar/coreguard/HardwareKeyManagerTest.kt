package com.coldboar.coreguard

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.Mockito.mock
import javax.crypto.SecretKey

class HardwareKeyManagerTest {

    @Test
    fun `security level starts as UNKNOWN before key inspection`() {
        val manager = managerWith(
            loadExistingKey = { null },
            generateStrongBoxKey = { fakeSecretKey() },
            generateTeeKey = { fakeSecretKey() },
            inspector = { KeySecurityLevel.UNKNOWN }
        )
        assertEquals(KeySecurityLevel.UNKNOWN, manager.securityLevel)
    }

    @Test
    fun `existing key path re-derives backing level without generation`() {
        var generated = false
        val manager = managerWith(
            loadExistingKey = { fakeSecretKey() },
            generateStrongBoxKey = {
                generated = true
                fakeSecretKey()
            },
            generateTeeKey = {
                generated = true
                fakeSecretKey()
            },
            inspector = { KeySecurityLevel.TEE }
        )

        assertThrows(Exception::class.java) {
            manager.encrypt("probe".toByteArray())
        }

        assertEquals(KeySecurityLevel.TEE, manager.securityLevel)
        assertFalse(generated)
    }

    @Test
    fun `manager recreation reclassifies existing key each process lifecycle`() {
        val key = fakeSecretKey()
        val firstManager = managerWith(
            loadExistingKey = { key },
            generateStrongBoxKey = { fakeSecretKey() },
            generateTeeKey = { fakeSecretKey() },
            inspector = { KeySecurityLevel.STRONGBOX }
        )
        val secondManager = managerWith(
            loadExistingKey = { key },
            generateStrongBoxKey = { fakeSecretKey() },
            generateTeeKey = { fakeSecretKey() },
            inspector = { KeySecurityLevel.STRONGBOX }
        )

        assertThrows(Exception::class.java) { firstManager.encrypt("a".toByteArray()) }
        assertThrows(Exception::class.java) { secondManager.encrypt("b".toByteArray()) }

        assertEquals(KeySecurityLevel.STRONGBOX, firstManager.securityLevel)
        assertEquals(KeySecurityLevel.STRONGBOX, secondManager.securityLevel)
    }

    @Test
    fun `inspector failure remains UNKNOWN instead of false SOFTWARE`() {
        val manager = managerWith(
            loadExistingKey = { fakeSecretKey() },
            generateStrongBoxKey = { fakeSecretKey() },
            generateTeeKey = { fakeSecretKey() },
            inspector = { throw IllegalStateException("metadata unavailable") }
        )

        assertThrows(Exception::class.java) {
            manager.encrypt("probe".toByteArray())
        }

        assertEquals(KeySecurityLevel.UNKNOWN, manager.securityLevel)
    }

    private fun managerWith(
        loadExistingKey: () -> SecretKey?,
        generateStrongBoxKey: () -> SecretKey,
        generateTeeKey: () -> SecretKey,
        inspector: (SecretKey) -> KeySecurityLevel
    ): HardwareKeyManager {
        val context = mock(Context::class.java)
        return HardwareKeyManager(
            context = context,
            loadExistingKey = loadExistingKey,
            generateStrongBoxKey = generateStrongBoxKey,
            generateTeeKey = generateTeeKey,
            securityLevelInspector = inspector
        )
    }

    private fun fakeSecretKey(): SecretKey = object : SecretKey {
        override fun getAlgorithm(): String = "AES"
        override fun getFormat(): String? = null
        override fun getEncoded(): ByteArray? = null
    }
}

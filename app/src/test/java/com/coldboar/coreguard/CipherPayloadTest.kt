package com.coldboar.coreguard

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Tests the framework-free IV+ciphertext wire format used by
 * [HardwareKeyManager]. Verified on the JVM without the Android Keystore.
 */
class CipherPayloadTest {

    @Test
    fun `pack then unpack round-trips iv and ciphertext`() {
        val iv = ByteArray(12) { it.toByte() }
        val ciphertext = "hardware-backed secret".toByteArray()

        val packed = CipherPayload.pack(iv, ciphertext)
        val (outIv, outCipher) = CipherPayload.unpack(packed)

        assertArrayEquals(iv, outIv)
        assertArrayEquals(ciphertext, outCipher)
    }

    @Test
    fun `packed layout stores iv length in first byte`() {
        val iv = ByteArray(12) { 1 }
        val ciphertext = ByteArray(20) { 2 }
        val packed = CipherPayload.pack(iv, ciphertext)

        assertEquals(12, packed[0].toInt() and 0xFF)
        assertEquals(1 + 12 + 20, packed.size)
    }

    @Test
    fun `unpack rejects empty payload`() {
        assertThrows(IllegalArgumentException::class.java) {
            CipherPayload.unpack(ByteArray(0))
        }
    }

    @Test
    fun `unpack rejects truncated payload`() {
        // Declares a 12-byte IV but only provides 3 bytes.
        val malformed = byteArrayOf(12, 1, 2, 3)
        assertThrows(IllegalArgumentException::class.java) {
            CipherPayload.unpack(malformed)
        }
    }

    @Test
    fun `pack rejects oversized iv`() {
        assertThrows(IllegalArgumentException::class.java) {
            CipherPayload.pack(ByteArray(256), ByteArray(4))
        }
    }
}

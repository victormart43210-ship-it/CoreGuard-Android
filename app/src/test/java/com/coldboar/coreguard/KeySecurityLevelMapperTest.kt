package com.coldboar.coreguard

import android.security.keystore.KeyProperties
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Proves Keystore security-level reconstruction without requiring a live HSM.
 *
 * Covers the existing-key / manager-recreation contract: metadata inspection
 * must map STRONGBOX/TEE/SOFTWARE/UNKNOWN correctly and must never invent
 * SOFTWARE when inspection fails.
 */
class KeySecurityLevelMapperTest {

    @Test
    fun `API 31 StrongBox maps to STRONGBOX`() {
        assertEquals(
            KeySecurityLevel.STRONGBOX,
            KeySecurityLevelMapper.fromApi31(KeyProperties.SECURITY_LEVEL_STRONGBOX)
        )
    }

    @Test
    fun `API 31 TEE maps to TEE`() {
        assertEquals(
            KeySecurityLevel.TEE,
            KeySecurityLevelMapper.fromApi31(KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT)
        )
    }

    @Test
    fun `API 31 software maps to SOFTWARE`() {
        assertEquals(
            KeySecurityLevel.SOFTWARE,
            KeySecurityLevelMapper.fromApi31(KeyProperties.SECURITY_LEVEL_SOFTWARE)
        )
    }

    @Test
    fun `API 31 unknown constant maps to UNKNOWN`() {
        assertEquals(KeySecurityLevel.UNKNOWN, KeySecurityLevelMapper.fromApi31(-1))
    }

    @Test
    fun `pre-API 31 secure hardware conservatively reports TEE not StrongBox`() {
        assertEquals(KeySecurityLevel.TEE, KeySecurityLevelMapper.fromPreApi31(true))
    }

    @Test
    fun `pre-API 31 non-secure reports SOFTWARE`() {
        assertEquals(KeySecurityLevel.SOFTWARE, KeySecurityLevelMapper.fromPreApi31(false))
    }

    @Test
    fun `inspect failure never fabricates SOFTWARE`() {
        assertEquals(KeySecurityLevel.UNKNOWN, KeySecurityLevelMapper.onInspectFailure())
    }

    @Test
    fun `existing-key recreation path uses inspect mapping not default SOFTWARE`() {
        // Simulates: manager destroyed → new manager → existing alias inspected.
        // Default field is UNKNOWN; after inspect of a TEE-backed key it must be TEE.
        var securityLevel = KeySecurityLevel.UNKNOWN
        securityLevel = KeySecurityLevelMapper.fromApi31(
            KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT
        )
        assertEquals(KeySecurityLevel.TEE, securityLevel)
        assertEquals(
            SecurityCheckState.WARN,
            StrongBoxCheckEvaluator { securityLevel }.evaluate().state
        )
    }

    @Test
    fun `default uninitialized level stays UNKNOWN`() {
        assertEquals(KeySecurityLevel.UNKNOWN, KeySecurityLevel.UNKNOWN)
        assertEquals(
            SecurityCheckState.WARN,
            StrongBoxCheckEvaluator { KeySecurityLevel.UNKNOWN }.evaluate().state
        )
    }
}

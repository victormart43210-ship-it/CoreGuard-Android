package com.coldboar.coreguard.mvt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanStageContractTest {

    @Test
    fun `stage ordering keeps expected execution flow`() {
        val ordered = listOf(
            ScanStageId.PREPARING,
            ScanStageId.LOADING_INDICATORS,
            ScanStageId.ENUMERATING_PACKAGES,
            ScanStageId.CHECKING_PACKAGE_METADATA,
            ScanStageId.CHECKING_INSTALLER_SOURCES,
            ScanStageId.CHECKING_CERTIFICATES,
            ScanStageId.CHECKING_PROCESSES,
            ScanStageId.CHECKING_ACCESSIBLE_FILES,
            ScanStageId.CORRELATING_INDICATORS,
            ScanStageId.BUILDING_FINDINGS
        )
        assertEquals(ordered, ScanStageId.entries.take(10))
    }

    @Test
    fun `terminal states are explicit and distinct`() {
        val terminals = setOf(ScanStageId.COMPLETED, ScanStageId.CANCELLED, ScanStageId.FAILED)
        assertEquals(3, terminals.size)
        assertTrue(terminals.all { it.name == "COMPLETED" || it.name == "CANCELLED" || it.name == "FAILED" })
    }
}


package com.coldboar.coreguard.ui.components

import com.coldboar.coreguard.ui.theme.AttentionAmber
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.HighRed
import com.coldboar.coreguard.ui.theme.SafeGreen
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveSecurityScoreAccentTest {

    @Test
    fun `accent follows guardian ranks`() {
        assertEquals(ElectricTeal, scoreAccent(null))
        assertEquals(HighRed, scoreAccent(10))
        assertEquals(AttentionAmber, scoreAccent(50))
        assertEquals(ElectricTeal, scoreAccent(70))
        assertEquals(SafeGreen, scoreAccent(95))
    }
}

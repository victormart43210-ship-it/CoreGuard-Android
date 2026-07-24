package com.coldboar.coreguard.mvt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class IocMatcherTest {

    private val indicators = listOf(
        Indicator(IndicatorType.DOMAIN, "evil.com", "Pegasus"),
        Indicator(IndicatorType.PACKAGE, "com.network.android", "Chrysaor"),
        Indicator(IndicatorType.PROCESS, "bh", "Pegasus"),
        Indicator(IndicatorType.FILEPATH, "/data/local/tmp/implant.so", "Pegasus"),
        Indicator(IndicatorType.FILENAME, "malware.apk", "Pegasus"),
        Indicator(IndicatorType.URL, "http://c2.evil.com/gateway", "Pegasus")
    )
    private val matcher = IocMatcher(indicators)

    @Test
    fun `package match is exact and case-insensitive`() {
        assertNotNull(matcher.matchPackage("com.network.android"))
        assertNotNull(matcher.matchPackage("COM.Network.Android"))
        assertNull(matcher.matchPackage("com.network.android.extra"))
    }

    @Test
    fun `domain matches exact and subdomain`() {
        assertNotNull(matcher.matchDomain("evil.com"))
        assertNotNull(matcher.matchDomain("c2.sub.evil.com"))
        assertNull(matcher.matchDomain("notevil.com"))
        assertNull(matcher.matchDomain("evil.com.attacker.net"))
    }

    @Test
    fun `domain match works from a url or host with port`() {
        assertNotNull(matcher.matchDomain("https://c2.evil.com:8443/path?x=1"))
        assertNotNull(matcher.matchDomain("evil.com:53"))
    }

    @Test
    fun `process matches name or basename`() {
        assertNotNull(matcher.matchProcess("bh"))
        assertNotNull(matcher.matchProcess("/system/bin/bh"))
        assertNull(matcher.matchProcess("bash"))
    }

    @Test
    fun `file path matches exact, suffix, and filename`() {
        assertNotNull(matcher.matchFilePath("/data/local/tmp/implant.so"))
        assertNotNull(matcher.matchFilePath("/sdcard/Download/malware.apk"))
        assertNull(matcher.matchFilePath("/sdcard/Download/safe.apk"))
    }

    @Test
    fun `url match falls back to domain`() {
        assertNotNull(matcher.matchUrl("http://c2.evil.com/gateway"))
        assertNotNull(matcher.matchUrl("https://evil.com/anything"))
    }

    @Test
    fun `indicator type parses stix and short forms`() {
        assertEquals(IndicatorType.DOMAIN, IndicatorType.fromString("domain-name:value"))
        assertEquals(IndicatorType.DOMAIN, IndicatorType.fromString("domains"))
        assertEquals(IndicatorType.PACKAGE, IndicatorType.fromString("app:id"))
        assertEquals(IndicatorType.PROCESS, IndicatorType.fromString("process"))
        assertNull(IndicatorType.fromString("nonsense"))
    }
}

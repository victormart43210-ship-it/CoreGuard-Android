package com.coldboar.coreguard.quilla

import com.coldboar.coreguard.quilla.knowledge.CyberKnowledgeBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuillaWebSecurityIntelFetcherTest {

    @Test
    fun `parseKevAndroidEntries keeps Android CVEs and skips irrelevant`() {
        val json = """
            {
              "vulnerabilities": [
                {
                  "cveID": "CVE-2025-48595",
                  "vendorProject": "Android",
                  "product": "Framework",
                  "vulnerabilityName": "Android Framework Integer Overflow Vulnerability",
                  "shortDescription": "Integer overflow in Android Framework.",
                  "dateAdded": "2025-12-01",
                  "requiredAction": "Apply updates."
                },
                {
                  "cveID": "CVE-1999-0001",
                  "vendorProject": "ExampleCorp",
                  "product": "ToasterFirmware",
                  "vulnerabilityName": "Toaster overflow",
                  "shortDescription": "Kitchen appliance firmware flaw.",
                  "dateAdded": "1999-01-01",
                  "requiredAction": "Unplug."
                }
              ]
            }
        """.trimIndent()
        val entries = QuillaWebSecurityIntelFetcher.parseKevAndroidEntries(json)
        assertEquals(1, entries.size)
        assertEquals("kev-cve-2025-48595", entries.first().id)
        assertTrue(entries.first().title.contains("CVE-2025-48595"))
        assertTrue(entries.first().defense.lowercase().contains("patch"))
    }

    @Test
    fun `parseMispAndroidGalaxyEntries builds defensive family briefs`() {
        val json = """
            {
              "values": [
                {
                  "value": "CopyCat",
                  "description": "CopyCat roots devices and establishes persistency.",
                  "meta": { "synonyms": ["copy-cat"] },
                  "uuid": "x"
                }
              ]
            }
        """.trimIndent()
        val entries = QuillaWebSecurityIntelFetcher.parseMispAndroidGalaxyEntries(json)
        assertEquals(1, entries.size)
        assertEquals("misp-android-copycat", entries.first().id)
        assertTrue(entries.first().body.contains("persistency"))
        assertTrue(entries.first().defense.lowercase().contains("defense"))
    }

    @Test
    fun `parseMispMalpediaMobileEntries keeps Android families uncapped by product limit`() {
        val json = """
            {
              "values": [
                {
                  "value": "Alien",
                  "description": "Android banking trojan distributed via sideloaded APK.",
                  "meta": { "synonyms": ["AlienBot"], "refs": ["https://malpedia.caad.fkie.fraunhofer.de/"] }
                },
                {
                  "value": "Emotet",
                  "description": "Windows botnet delivered by malicious documents.",
                  "meta": { "synonyms": [] }
                }
              ]
            }
        """.trimIndent()
        val entries = QuillaWebSecurityIntelFetcher.parseMispMalpediaMobileEntries(json)
        assertEquals(1, entries.size)
        assertEquals("malpedia-alien", entries.first().id)
        assertTrue(entries.first().tags.contains("evolving"))
    }

    @Test
    fun `MISP sources are immutable HTTPS revisions`() {
        val urls = listOf(
            QuillaWebSecurityIntelFetcher.MISP_ANDROID_GALAXY_URL,
            QuillaWebSecurityIntelFetcher.MISP_MALPEDIA_GALAXY_URL
        )
        assertTrue(urls.all { it.startsWith("https://") })
        assertTrue(urls.none { it.contains("/main/") || it.contains("/master/") })
        assertTrue(urls.all { it.contains("/91e6b5c6e6671fa820f21aad72574bd76333d224/") })
    }

    @Test
    fun `mergeEntries brings web intel into CyberKnowledgeBase search`() {
        CyberKnowledgeBase.clear()
        CyberKnowledgeBase.loadDocuments(
            listOf(
                """{"entries":[{"id":"seed","title":"Seed entry","category":"test","tags":["seed"],"summary":"s","body":"b"}]}"""
            )
        )
        val web = QuillaWebSecurityIntelFetcher.parseKevAndroidEntries(
            """
            {"vulnerabilities":[{
              "cveID":"CVE-2025-48595",
              "vendorProject":"Android",
              "product":"Framework",
              "vulnerabilityName":"Android Framework Integer Overflow Vulnerability",
              "shortDescription":"Integer overflow.",
              "dateAdded":"2025-12-01",
              "requiredAction":"Patch"
            }]}
            """.trimIndent()
        )
        CyberKnowledgeBase.mergeEntries(web)
        val hits = CyberKnowledgeBase.search("CVE-2025-48595 Android Framework", limit = 3)
        assertTrue(hits.any { it.entry.id == "kev-cve-2025-48595" })
        CyberKnowledgeBase.clear()
    }
}

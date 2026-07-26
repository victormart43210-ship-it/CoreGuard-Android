package com.coldboar.coreguard.toolkit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalSecurityToolkitTest {

    @Test
    fun `includes VirusTotal and privacy essentials`() {
        assertNotNull(ExternalSecurityToolkit.tool("virustotal"))
        assertNotNull(ExternalSecurityToolkit.tool("privnote"))
        assertNotNull(ExternalSecurityToolkit.tool("temp_mail"))
        assertNotNull(ExternalSecurityToolkit.tool("archive_ph"))
        assertNotNull(ExternalSecurityToolkit.tool("downdetector"))
        assertNotNull(ExternalSecurityToolkit.tool("fast_com"))
        assertNotNull(ExternalSecurityToolkit.tool("tineye"))
    }

    @Test
    fun `tools are uniquely identified with https urls`() {
        assertTrue(ExternalSecurityToolkit.tools.size >= 8)
        val ids = ExternalSecurityToolkit.tools.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        ExternalSecurityToolkit.tools.forEach { tool ->
            assertTrue(tool.title.isNotBlank())
            assertTrue(tool.summary.isNotBlank())
            assertTrue(tool.whenToUse.isNotBlank())
            assertTrue(tool.url.startsWith("https://"))
            assertTrue(tool.host.isNotBlank())
        }
    }

    @Test
    fun `skips music and pdf fluff from the source list`() {
        val joined = ExternalSecurityToolkit.tools.joinToString(" ") {
            "${it.id} ${it.title} ${it.host} ${it.url}"
        }.lowercase()
        assertFalse(joined.contains("radio.garden"))
        assertFalse(joined.contains("everynoise"))
        assertFalse(joined.contains("smallpdf"))
        assertFalse(joined.contains("ilovepdf"))
        assertFalse(joined.contains("coffitivity"))
    }

    @Test
    fun `VirusTotal caution warns about vendor sharing`() {
        val vt = ExternalSecurityToolkit.tool("virustotal")!!
        assertEquals(ExternalSecurityToolkit.Category.MALWARE, vt.category)
        assertNotNull(vt.caution)
        assertTrue(vt.caution!!.lowercase().contains("share") || vt.caution!!.lowercase().contains("partner"))
    }

    @Test
    fun `https gate accepts toolkit urls and rejects cleartext`() {
        ExternalSecurityToolkit.tools.forEach { tool ->
            assertTrue(
                "expected https for ${tool.id}",
                ExternalToolkitIntents.isHttpsUrl(tool.url)
            )
        }
        assertFalse(ExternalToolkitIntents.isHttpsUrl("http://example.com/"))
        assertFalse(ExternalToolkitIntents.isHttpsUrl("not a url"))
        assertFalse(ExternalToolkitIntents.isHttpsUrl("ftp://example.com/file"))
    }

    @Test
    fun `quilla summary mentions toolkit and VirusTotal`() {
        val blurb = ExternalSecurityToolkit.quillaSummary().lowercase()
        assertTrue(blurb.contains("external security toolkit"))
        assertTrue(blurb.contains("virustotal"))
        assertTrue(blurb.contains("nemesis"))
    }
}

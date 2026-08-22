package com.coldboar.coreguard.mvt

import com.coldboar.coreguard.net.PublicIntelFeedPins
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proves the metadata sidecar is not a root of trust — load validation requires
 * compiled [PublicIntelFeedPins] exact matches and body digest verification.
 */
class CompiledPinValidatorTest {

    private val pin = PublicIntelFeedPins.PEGASUS
    private val commit = IocFeedFetcher.extractCommitPin(pin.url)

    private fun meta(
        name: String = pin.name,
        url: String = pin.url,
        sha: String = pin.sha256Hex,
        commitPin: String = commit
    ) = VerifiedRemoteMeta(
        name = name,
        url = url,
        sha256Hex = sha,
        commitPin = commitPin,
        verifiedAtMs = 1L
    )

    private fun bodyMatchingPin(): ByteArray {
        // Body must digest to the compiled pin — use empty and override via custom pinFor in negative tests.
        // For positive path we inject a pin whose digest matches our body.
        return """{"indicators":[{"type":"domain","value":"x.example","malware":"T"}]}"""
            .toByteArray(Charsets.UTF_8)
    }

    @Test
    fun `unknown URL is rejected as unavailable`() {
        val body = bodyMatchingPin()
        val sha = HardenedSha.sha256Hex(body)
        val result = CompiledPinValidator.validateAgainstCompiledPins(
            meta = meta(url = "https://evil.example/feed.json", sha = sha),
            bodyBytes = body
        )
        assertNull(result)
    }

    @Test
    fun `malformed digest is rejected`() {
        val body = bodyMatchingPin()
        assertNull(
            CompiledPinValidator.validateAgainstCompiledPins(
                meta = meta(sha = "not-a-sha"),
                bodyBytes = body
            )
        )
        assertNull(
            CompiledPinValidator.validateAgainstCompiledPins(
                meta = meta(sha = "abcd"),
                bodyBytes = body
            )
        )
    }

    @Test
    fun `mismatched commit is rejected`() {
        val body = bodyMatchingPin()
        val sha = HardenedSha.sha256Hex(body)
        val customPin = PublicIntelFeedPins.Pin(pin.name, pin.url, sha, pin.maxBytes)
        assertNull(
            CompiledPinValidator.validateAgainstCompiledPins(
                meta = meta(sha = sha, commitPin = "deadbeef"),
                bodyBytes = body,
                pinFor = { if (it == pin.url) customPin else null },
                extractCommit = { commit }
            )
        )
    }

    @Test
    fun `sidecar-only claim without compiled pin match is rejected`() {
        val body = bodyMatchingPin()
        val sha = HardenedSha.sha256Hex(body)
        // Meta claims a digest matching the body, but URL is unknown to compiled pins.
        assertNull(
            CompiledPinValidator.validateAgainstCompiledPins(
                meta = meta(
                    name = "Forged Feed",
                    url = "https://raw.githubusercontent.com/evil/repo/abc/feed.json",
                    sha = sha,
                    commitPin = "abc"
                ),
                bodyBytes = body
            )
        )
    }

    @Test
    fun `body digest mismatch against compiled pin is rejected`() {
        val body = bodyMatchingPin()
        assertNull(
            CompiledPinValidator.validateAgainstCompiledPins(
                meta = meta(), // uses real PEGASUS digest, body won't match
                bodyBytes = body
            )
        )
    }

    @Test
    fun `exact compiled pin match with body digest accepts`() {
        val body = bodyMatchingPin()
        val sha = HardenedSha.sha256Hex(body)
        val customPin = PublicIntelFeedPins.Pin(pin.name, pin.url, sha, pin.maxBytes)
        val accepted = CompiledPinValidator.validateAgainstCompiledPins(
            meta = meta(sha = sha, commitPin = commit),
            bodyBytes = body,
            pinFor = { if (it == pin.url) customPin else null },
            extractCommit = { commit }
        )
        assertNotNull(accepted)
        assertTrue(accepted!!.sha256Hex.equals(sha, ignoreCase = true))
    }

    @Test
    fun `source identity name mismatch is rejected`() {
        val body = bodyMatchingPin()
        val sha = HardenedSha.sha256Hex(body)
        val customPin = PublicIntelFeedPins.Pin(pin.name, pin.url, sha, pin.maxBytes)
        assertNull(
            CompiledPinValidator.validateAgainstCompiledPins(
                meta = meta(name = "Wrong Name", sha = sha),
                bodyBytes = body,
                pinFor = { if (it == pin.url) customPin else null },
                extractCommit = { commit }
            )
        )
    }
}

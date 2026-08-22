package com.coldboar.coreguard.mvt

/**
 * Provenance of the IOC set used by a Nemesis scan session.
 *
 * Never upgrades MIXED / FALLBACK / USER_IMPORTED / UNAVAILABLE / UNKNOWN to a
 * remote-verified authenticity claim.
 */
enum class IocProvenanceClass {
    /** Digest-verified downloaded feed (remote_feed.json + matching meta). */
    VERIFIED_REMOTE,
    /** Packaged application indicators under assets/ioc. */
    BUNDLED,
    /** User-provided JSON under filesDir/ioc, not cryptographically verified. */
    USER_IMPORTED,
    /** Indicators from more than one of {VERIFIED_REMOTE, BUNDLED, USER_IMPORTED}. */
    MIXED,
    /** Built-in [DefaultIndicators] used because no other IOCs loaded. */
    FALLBACK,
    /** No IOC snapshot was loaded for this session. */
    UNAVAILABLE,
    /** Provenance cannot be established. */
    UNKNOWN
}

/**
 * Truthful attribution for a persisted scan session.
 *
 * [feedVersion] carries immutable commit + SHA-256 **only** for [IocProvenanceClass.VERIFIED_REMOTE].
 */
data class IocProvenanceSnapshot(
    val provenanceClass: IocProvenanceClass,
    val feedSource: String,
    val feedVersion: String?,
    val feedAuthenticity: String,
    val feedLoadedAtMs: Long,
    val indicatorCount: Int,
    val contributingClasses: Set<IocProvenanceClass> = emptySet()
) {
    companion object {
        fun unavailable(loadedAtMs: Long = 0L): IocProvenanceSnapshot =
            IocProvenanceSnapshot(
                provenanceClass = IocProvenanceClass.UNAVAILABLE,
                feedSource = "UNAVAILABLE",
                feedVersion = null,
                feedAuthenticity = "UNAVAILABLE — no IOC snapshot loaded",
                feedLoadedAtMs = loadedAtMs,
                indicatorCount = 0,
                contributingClasses = emptySet()
            )

        fun unknown(reason: String, loadedAtMs: Long = 0L): IocProvenanceSnapshot =
            IocProvenanceSnapshot(
                provenanceClass = IocProvenanceClass.UNKNOWN,
                feedSource = "UNKNOWN",
                feedVersion = null,
                feedAuthenticity = "UNKNOWN — $reason",
                feedLoadedAtMs = loadedAtMs,
                indicatorCount = 0,
                contributingClasses = emptySet()
            )
    }
}

/**
 * Derives a session provenance label from which source classes actually contributed.
 */
object IocProvenanceResolver {

    fun resolve(
        bundledCount: Int,
        verifiedRemoteCount: Int,
        userImportedCount: Int,
        usedFallback: Boolean,
        loadedAtMs: Long,
        verifiedMeta: VerifiedRemoteMeta?
    ): IocProvenanceSnapshot {
        val contributing = linkedSetOf<IocProvenanceClass>()
        if (bundledCount > 0) contributing += IocProvenanceClass.BUNDLED
        if (verifiedRemoteCount > 0) contributing += IocProvenanceClass.VERIFIED_REMOTE
        if (userImportedCount > 0) contributing += IocProvenanceClass.USER_IMPORTED

        val total = bundledCount + verifiedRemoteCount + userImportedCount
        if (total == 0 && usedFallback) {
            return IocProvenanceSnapshot(
                provenanceClass = IocProvenanceClass.FALLBACK,
                feedSource = "CoreGuard DefaultIndicators",
                feedVersion = null,
                feedAuthenticity = "FALLBACK — built-in indicators; not remote-verified",
                feedLoadedAtMs = loadedAtMs,
                indicatorCount = DefaultIndicators.list.size,
                contributingClasses = setOf(IocProvenanceClass.FALLBACK)
            )
        }
        if (total == 0) {
            return IocProvenanceSnapshot.unavailable(loadedAtMs)
        }

        val clazz = when {
            contributing.size > 1 -> IocProvenanceClass.MIXED
            contributing.single() == IocProvenanceClass.VERIFIED_REMOTE -> IocProvenanceClass.VERIFIED_REMOTE
            contributing.single() == IocProvenanceClass.BUNDLED -> IocProvenanceClass.BUNDLED
            contributing.single() == IocProvenanceClass.USER_IMPORTED -> IocProvenanceClass.USER_IMPORTED
            else -> IocProvenanceClass.UNKNOWN
        }

        return when (clazz) {
            IocProvenanceClass.VERIFIED_REMOTE -> {
                val meta = verifiedMeta
                if (meta == null) {
                    IocProvenanceSnapshot.unknown(
                        "verified-remote indicators present without meta",
                        loadedAtMs
                    ).copy(indicatorCount = total, contributingClasses = contributing)
                } else {
                    IocProvenanceSnapshot(
                        provenanceClass = IocProvenanceClass.VERIFIED_REMOTE,
                        feedSource = meta.name,
                        feedVersion = "commit=${meta.commitPin};sha256=${meta.sha256Hex}",
                        feedAuthenticity =
                            "VERIFIED_REMOTE — HTTPS + SHA-256 digest pin; not an upstream Ed25519 signature",
                        feedLoadedAtMs = loadedAtMs,
                        indicatorCount = total,
                        contributingClasses = contributing
                    )
                }
            }
            IocProvenanceClass.BUNDLED -> IocProvenanceSnapshot(
                provenanceClass = IocProvenanceClass.BUNDLED,
                feedSource = "Bundled assets/ioc",
                feedVersion = null,
                feedAuthenticity = "BUNDLED — packaged application indicators; not remote-verified",
                feedLoadedAtMs = loadedAtMs,
                indicatorCount = total,
                contributingClasses = contributing
            )
            IocProvenanceClass.USER_IMPORTED -> IocProvenanceSnapshot(
                provenanceClass = IocProvenanceClass.USER_IMPORTED,
                feedSource = "User-imported filesDir/ioc",
                feedVersion = null,
                feedAuthenticity =
                    "USER_IMPORTED — user-provided; not cryptographically verified by CoreGuard",
                feedLoadedAtMs = loadedAtMs,
                indicatorCount = total,
                contributingClasses = contributing
            )
            IocProvenanceClass.MIXED -> IocProvenanceSnapshot(
                provenanceClass = IocProvenanceClass.MIXED,
                feedSource = contributing.joinToString("+") { it.name },
                feedVersion = null,
                feedAuthenticity =
                    "MIXED — indicators from ${contributing.joinToString()} ; " +
                        "must not inherit remote-feed verification",
                feedLoadedAtMs = loadedAtMs,
                indicatorCount = total,
                contributingClasses = contributing
            )
            else -> IocProvenanceSnapshot.unknown("unresolved provenance", loadedAtMs)
                .copy(indicatorCount = total, contributingClasses = contributing)
        }
    }
}

/** Sidecar metadata written next to a digest-verified remote feed file. */
data class VerifiedRemoteMeta(
    val name: String,
    val url: String,
    val sha256Hex: String,
    /** Immutable commit id embedded in the pin URL when present; else empty. */
    val commitPin: String,
    val verifiedAtMs: Long
)

/**
 * Immutable IOC acquisition captured at scan (or load) time.
 *
 * Indicators and provenance are bound together so a mid-scan refresh cannot
 * re-label the matcher that actually ran.
 */
data class IocAcquisitionSnapshot(
    val indicators: List<Indicator>,
    val provenance: IocProvenanceSnapshot,
    val loadedAtMs: Long
) {
    companion object {
        fun unavailable(loadedAtMs: Long = 0L): IocAcquisitionSnapshot =
            IocAcquisitionSnapshot(
                indicators = emptyList(),
                provenance = IocProvenanceSnapshot.unavailable(loadedAtMs),
                loadedAtMs = loadedAtMs
            )
    }
}

/**
 * Validates persisted remote feed meta + body against compiled [com.coldboar.coreguard.net.PublicIntelFeedPins].
 * The metadata sidecar is never a root of trust on its own.
 */
object CompiledPinValidator {

    private val SHA256_HEX = Regex("^[0-9a-fA-F]{64}$")

    /**
     * Returns compiled-pin-aligned [VerifiedRemoteMeta] when URL, name, commit,
     * and SHA-256 all match a compiled pin and the body digests to that pin.
     * Otherwise null (caller must treat as UNAVAILABLE / not verified-remote).
     */
    fun validateAgainstCompiledPins(
        meta: VerifiedRemoteMeta,
        bodyBytes: ByteArray,
        pinFor: (String) -> com.coldboar.coreguard.net.PublicIntelFeedPins.Pin? =
            { com.coldboar.coreguard.net.PublicIntelFeedPins.pinFor(it) },
        extractCommit: (String) -> String = { IocFeedFetcher.extractCommitPin(it) }
    ): VerifiedRemoteMeta? {
        if (!SHA256_HEX.matches(meta.sha256Hex)) return null
        val pin = pinFor(meta.url) ?: return null
        if (pin.url != meta.url) return null
        if (pin.name != meta.name) return null
        val expectedCommit = extractCommit(pin.url)
        if (expectedCommit != meta.commitPin) return null
        if (!pin.sha256Hex.equals(meta.sha256Hex, ignoreCase = true)) return null
        val bodySha = HardenedSha.sha256Hex(bodyBytes)
        if (!pin.sha256Hex.equals(bodySha, ignoreCase = true)) return null
        if (!meta.sha256Hex.equals(bodySha, ignoreCase = true)) return null
        return meta.copy(sha256Hex = pin.sha256Hex.lowercase())
    }
}

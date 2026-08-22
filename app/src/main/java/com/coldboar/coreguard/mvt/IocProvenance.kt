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

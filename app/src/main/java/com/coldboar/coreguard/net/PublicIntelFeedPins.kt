package com.coldboar.coreguard.net

/**
 * Immutable, digest-pinned public intelligence feed references.
 *
 * Floating branch URLs (`/master/`, `/main/`) are rejected for production
 * consumption. Digests were measured at pin time; bump commit + digest together
 * when intentionally refreshing feeds. Integrity failure must fail closed.
 */
object PublicIntelFeedPins {

    data class Pin(
        val name: String,
        val url: String,
        val sha256Hex: String,
        val maxBytes: Int
    )

    val ALLOWED_HOSTS: Set<String> = setOf(
        "raw.githubusercontent.com",
        "www.cisa.gov",
        "cisa.gov"
    )

    private const val AMNESTY =
        "3d8f248a0d015f183724ae7d096a5c46a8bb5fc7"
    private const val MVT =
        "162685398d842d8217ea8d6f69f9b565a0778d93"
    private const val MISP =
        "91e6b5c6e6671fa820f21aad72574bd76333d224"
    private const val STALKERWARE =
        "426119d27e5597ec1b6976153bbe6d58ec0fc08e"

    val PEGASUS = Pin(
        name = "Amnesty Pegasus (NSO)",
        url = "https://raw.githubusercontent.com/AmnestyTech/investigations/$AMNESTY/2021-07-18_nso/pegasus.stix2",
        sha256Hex = "df1bcaa78abc7b85781b1ebc2daa3cc225371e2024d9ef96e84f80f927256586",
        maxBytes = 2 * 1024 * 1024
    )

    val ANDROID_CAMPAIGN = Pin(
        name = "Amnesty Android campaign",
        url = "https://raw.githubusercontent.com/AmnestyTech/investigations/$AMNESTY/2023-03-29_android_campaign/malware.stix2",
        sha256Hex = "28da50042006281d56c17dff08f06bba3ba310bc18cc23040780850131b2efdb",
        maxBytes = 3 * 1024 * 1024
    )

    val NOVISPY = Pin(
        name = "Amnesty NoviSpy",
        url = "https://raw.githubusercontent.com/AmnestyTech/investigations/$AMNESTY/2024-12-16_serbia_novispy/novispy.stix2",
        sha256Hex = "02bfceea5a2c32b159f11569736448398e35e7f0fc7d137385b943ec60697360",
        maxBytes = 1 * 1024 * 1024
    )

    val WINTEGO = Pin(
        name = "Amnesty Wintego Helios",
        url = "https://raw.githubusercontent.com/AmnestyTech/investigations/$AMNESTY/2024-05-02_wintego_helios/wintego_helios.stix2",
        sha256Hex = "d0d546c388207e8a162eb3901259ee20c0ddd03efeff145d3cc9b3a0a02a5419",
        maxBytes = 1 * 1024 * 1024
    )

    val CYTROX = Pin(
        name = "Amnesty Cytrox / Predator",
        url = "https://raw.githubusercontent.com/AmnestyTech/investigations/$AMNESTY/2021-12-16_cytrox/cytrox.stix2",
        sha256Hex = "6fe92193d9e17c21a16eb7abe93a418a2e40c0176dcb56fb30539f84136391bb",
        maxBytes = 1 * 1024 * 1024
    )

    val WYRMSPY = Pin(
        name = "MVT WyrmSpy/DragonEgg",
        url = "https://raw.githubusercontent.com/mvt-project/mvt-indicators/$MVT/2023-07-25_wyrmspy_dragonegg/wyrmspy_dragonegg.stix2",
        sha256Hex = "82143861aa57cf570acc19023a7059dc5d3901202dd7338b418a83169e1e7e87",
        maxBytes = 1 * 1024 * 1024
    )

    val EAGLEMSGSPY = Pin(
        name = "MVT EagleMsgSpy",
        url = "https://raw.githubusercontent.com/mvt-project/mvt-indicators/$MVT/2024-12-25_eaglemsgspy/eaglemsgspy.stix2",
        sha256Hex = "c40ca826d3eeef1e095af18d77531246b4849d2fa350464c07326d1b12015b50",
        maxBytes = 1 * 1024 * 1024
    )

    val DARKSWORD = Pin(
        name = "MVT DarkSword",
        url = "https://raw.githubusercontent.com/mvt-project/mvt-indicators/$MVT/2026-03-30_darksword/darksword.stix2",
        sha256Hex = "a2387f14ae7e7f176b0bd543be9b5ff151c77a22377cee5ae38ac5c3c4973a20",
        maxBytes = 1 * 1024 * 1024
    )

    val CORUNA = Pin(
        name = "MVT Coruna / CryptoWaters",
        url = "https://raw.githubusercontent.com/mvt-project/mvt-indicators/$MVT/2026-03-03_coruna_cryptowaters/coruna.stix2",
        sha256Hex = "0046552adf6127ebcaeac9f825a8082a9fa201dd7c921bb7d596fb0c02f12c24",
        maxBytes = 1 * 1024 * 1024
    )

    val MORPHEUS = Pin(
        name = "MVT IPS Morpheus",
        url = "https://raw.githubusercontent.com/mvt-project/mvt-indicators/$MVT/2026-04-23_ips_morpheus/morpheus.stix2",
        sha256Hex = "b2327156670ed5c1748600fa2c7a2a1756496c53de4534394ab6b80d57b13ed5",
        maxBytes = 1 * 1024 * 1024
    )

    val RESIDENTBAT = Pin(
        name = "MVT ResidentBat",
        url = "https://raw.githubusercontent.com/mvt-project/mvt-indicators/$MVT/ResidentBat/residentbat.stix2",
        sha256Hex = "47270c7236d55e2fa2a05a3fa432da79af138bbbe2b7f243109bfec0686996bf",
        maxBytes = 1 * 1024 * 1024
    )

    val STALKERWARE_IOCS = Pin(
        name = "Open stalkerware IOCs",
        url = "https://raw.githubusercontent.com/f00wl/stalkerware-indicators/$STALKERWARE/generated/stalkerware.stix2",
        sha256Hex = "0c14a0eab0404adfdf93d224a6be3bedb0c5dd4c3630a443d338ccfa70dc04e7",
        maxBytes = 6 * 1024 * 1024
    )

    val CISA_KEV = Pin(
        name = "CISA KEV",
        url = "https://www.cisa.gov/sites/default/files/feeds/known_exploited_vulnerabilities.json",
        sha256Hex = "137884960e3f801665bfa47694e703fbc4dd1c738df5e0e5af12d325a5f8a9d5",
        maxBytes = 8 * 1024 * 1024
    )

    val MISP_ANDROID = Pin(
        name = "MISP Android galaxy",
        url = "https://raw.githubusercontent.com/MISP/misp-galaxy/$MISP/clusters/android.json",
        sha256Hex = "93aee3013aaa1a5cccd42051412f2178ae918b18b0a5f7e3eed545b78740c281",
        maxBytes = 8 * 1024 * 1024
    )

    val MISP_MALPEDIA = Pin(
        name = "MISP Malpedia galaxy",
        url = "https://raw.githubusercontent.com/MISP/misp-galaxy/$MISP/clusters/malpedia.json",
        sha256Hex = "1a1523635946c2d25572024b2a89553db11b6a296337cd1bcfd17223b24142b4",
        maxBytes = 8 * 1024 * 1024
    )

    val ALL: List<Pin> = listOf(
        PEGASUS, ANDROID_CAMPAIGN, NOVISPY, WINTEGO, CYTROX,
        WYRMSPY, EAGLEMSGSPY, DARKSWORD, CORUNA, MORPHEUS, RESIDENTBAT,
        STALKERWARE_IOCS, CISA_KEV, MISP_ANDROID, MISP_MALPEDIA
    )

    val STIX_RESEARCH_PINS: List<Pin> = listOf(
        ANDROID_CAMPAIGN, NOVISPY, WINTEGO, PEGASUS, CYTROX,
        WYRMSPY, EAGLEMSGSPY, DARKSWORD, CORUNA, MORPHEUS, RESIDENTBAT,
        STALKERWARE_IOCS
    )

    fun pinFor(url: String): Pin? = ALL.firstOrNull { it.url == url }

    /** Reject floating GitHub branch refs in production feed URLs. */
    fun isFloatingBranchUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("/master/") ||
            Regex("""raw\.githubusercontent\.com/[^/]+/[^/]+/main/""").containsMatchIn(lower)
    }
}

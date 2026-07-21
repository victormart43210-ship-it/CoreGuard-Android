package com.coldboar.coreguard.mvt

/**
 * A small, curated fallback set of Indicators of Compromise used when no
 * bundled/updated IOC file is available.
 *
 * The domain indicators are drawn from the **publicly released** NSO Group
 * "Pegasus" infrastructure published by the Amnesty International Security Lab
 * alongside the 2021 Forensic Methodology Report, mirrored in the
 * `mvt-project/mvt-indicators` repository. Entries marked as EXAMPLE are
 * synthetic, non-attributable placeholders used only to exercise the scanner in
 * tests and demos.
 *
 * This list is intentionally short; production deployments should ship and
 * regularly refresh a full STIX2 indicator feed (see [IocRepository]).
 */
object DefaultIndicators {

    private const val PEGASUS = "Pegasus (NSO Group)"
    private const val AMNESTY_REF =
        "https://www.amnesty.org/en/latest/research/2021/07/forensic-methodology-report-how-to-catch-nso-groups-pegasus/"

    val list: List<Indicator> = buildList {
        // --- Publicly published Pegasus Version 4 domains (Amnesty/Citizen Lab) ---
        listOf(
            "free247downloads.com",
            "urlpush.net",
            "opposedarrangea.com",
            "get1tune.com",
            "yrl.im",
            "banorte-serguridad.com",
            "3-3-3.co",
            "ecommerce-ads.org",
            "truxlist.com",
            "smsverication.info"
        ).forEach { add(Indicator(IndicatorType.DOMAIN, it, PEGASUS, AMNESTY_REF)) }

        // --- Representative clandestine-spyware process/package markers ---
        add(Indicator(IndicatorType.PROCESS, "bh", PEGASUS, AMNESTY_REF))
        add(Indicator(IndicatorType.PROCESS, "roleaccountd", PEGASUS, AMNESTY_REF))
        add(Indicator(IndicatorType.PROCESS, "com.network.android", "Chrysaor (Pegasus Android)", AMNESTY_REF))
        add(Indicator(IndicatorType.PACKAGE, "com.network.android", "Chrysaor (Pegasus Android)", AMNESTY_REF))

        // --- EXAMPLE (synthetic) indicators for tests/demos only ---
        add(Indicator(IndicatorType.PACKAGE, "com.coreguard.pegasus.sample", "EXAMPLE (test indicator)"))
        add(Indicator(IndicatorType.DOMAIN, "pegasus-c2.example", "EXAMPLE (test indicator)"))
        add(Indicator(IndicatorType.PROCESS, "pegasus-implant", "EXAMPLE (test indicator)"))
    }
}

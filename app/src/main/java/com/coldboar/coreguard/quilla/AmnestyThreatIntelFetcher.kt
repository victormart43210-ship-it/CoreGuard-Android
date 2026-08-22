package com.coldboar.coreguard.quilla

import com.coldboar.coreguard.net.PublicIntelFeedPins
import com.quilla.intelligence.sdk.intel.PublicMultiSourceStixFetcher
import java.io.IOException

/**
 * Compatibility façade over [PublicMultiSourceStixFetcher] for Quilla Research.
 *
 * Prefer [QuillaIntelNetwork.syncAll] for full multi-source + web-knowledge sync.
 * These helpers remain for unit tests and the correlation engine default fetcher.
 *
 * Feeds are Amnesty Tech / MVT public STIX2 bundles (defensive IOC correlation only).
 * They do **not** write into Nemesis [com.coldboar.coreguard.mvt.IocRepository].
 */
object AmnestyThreatIntelFetcher {

    /**
     * Official Amnesty Tech Android-campaign STIX2 indicator bundle (commit-pinned).
     */
    val FEED_URL: String = PublicIntelFeedPins.ANDROID_CAMPAIGN.url

    /**
     * Amnesty Pegasus / NSO STIX2 bundle (commit-pinned; replaces retired mvt path).
     */
    val MVT_PEGASUS_FEED_URL: String = PublicIntelFeedPins.PEGASUS.url

    /**
     * Downloads and parses [AmnestyIndicator] records from [FEED_URL].
     *
     * Returns an empty list (without throwing) on any network or parse failure.
     */
    fun fetchAmnestyIndicators(): List<AmnestyIndicator> {
        val pin = PublicIntelFeedPins.ANDROID_CAMPAIGN
        val fetcher = PublicMultiSourceStixFetcher(
            feeds = listOf(
                PublicMultiSourceStixFetcher.Feed(
                    name = pin.name,
                    url = pin.url,
                    sha256Hex = pin.sha256Hex,
                    maxBytes = pin.maxBytes
                )
            )
        )
        return fetcher.fetchAllSources().map {
            AmnestyIndicator(it.id, it.indicatorType, it.patternValue, it.description)
        }
    }

    /**
     * Pulls the default Quilla STIX multi-source set for Research.
     *
     * Throws [IOException] only when the fetch returns zero indicators (all feeds failed).
     */
    fun fetchPublicResearchIndicators(): List<AmnestyIndicator> {
        val fetched = PublicMultiSourceStixFetcher().fetchAllSources()
        if (fetched.isEmpty()) {
            throw IOException("Amnesty/MVT research feeds returned no indicators")
        }
        return fetched.map {
            AmnestyIndicator(it.id, it.indicatorType, it.patternValue, it.description)
        }
    }

    /**
     * Parses a STIX2 bundle JSON string and returns the extracted [AmnestyIndicator] list.
     * Exposed as internal to allow unit-testing the parser without a network call.
     */
    internal fun parseStixBundle(json: String): List<AmnestyIndicator> =
        PublicMultiSourceStixFetcher.parseStixBundle(
            json = json,
            sourceFeed = "test",
            ttlTimestamp = Long.MAX_VALUE
        ).map {
            AmnestyIndicator(it.id, it.indicatorType, it.patternValue, it.description)
        }
}

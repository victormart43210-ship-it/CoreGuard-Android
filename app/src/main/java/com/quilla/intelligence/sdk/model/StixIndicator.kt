package com.quilla.intelligence.sdk.model

/**
 * A parsed STIX2 threat indicator sourced from one of the feeds managed by
 * [com.quilla.intelligence.sdk.intel.MultiSourceStixFetcher].
 *
 * @param id             STIX2 object id (e.g. "indicator--...").
 * @param sourceFeed     Human-readable name of the originating intelligence feed.
 * @param indicatorType  Normalised type string: "DOMAIN", "IP", "HASH", or "GENERIC".
 * @param patternValue   The extracted indicator value (domain name, IP, hash, etc.).
 * @param description    Human-readable description from the STIX2 bundle.
 * @param ttlTimestamp   Epoch-millisecond timestamp after which this indicator expires.
 */
data class StixIndicator(
    val id: String,
    val sourceFeed: String,
    val indicatorType: String,
    val patternValue: String,
    val description: String,
    val ttlTimestamp: Long
)

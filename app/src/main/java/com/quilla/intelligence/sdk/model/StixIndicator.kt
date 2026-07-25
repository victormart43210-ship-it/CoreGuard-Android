package com.quilla.intelligence.sdk.model

/**
 * A parsed STIX 2.x threat indicator sourced from one of the configured intelligence feeds.
 *
 * @param id             STIX2 object identifier (e.g. "indicator--...").
 * @param sourceFeed     Human-readable name of the originating feed (e.g. "Amnesty International").
 * @param indicatorType  Normalised type string: "DOMAIN", "IP", "HASH", or "GENERIC".
 * @param patternValue   The extracted indicator value (domain name, IP address, hash, etc.).
 * @param description    Human-readable description from the STIX2 bundle.
 * @param ttlTimestamp   Expiry epoch-millisecond timestamp; indicators past this time are inactive.
 */
data class StixIndicator(
    val id: String,
    val sourceFeed: String,
    val indicatorType: String,
    val patternValue: String,
    val description: String,
    val ttlTimestamp: Long
)

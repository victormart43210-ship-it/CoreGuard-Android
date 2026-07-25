package com.quilla.intelligence.sdk.intel

import com.quilla.intelligence.sdk.model.StixIndicator

/**
 * Aggregates STIX 2.x threat indicators from multiple configured intelligence feeds.
 *
 * Implementations must be safe to call from a background thread. In tests this
 * interface is mocked to avoid real network I/O.
 */
interface MultiSourceStixFetcher {
    /**
     * Fetches and returns the merged list of [StixIndicator] records from all
     * configured sources. Must be called on a background thread.
     */
    fun fetchAllSources(): List<StixIndicator>
}

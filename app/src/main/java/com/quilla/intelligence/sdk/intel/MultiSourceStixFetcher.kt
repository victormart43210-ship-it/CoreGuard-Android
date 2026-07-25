package com.quilla.intelligence.sdk.intel

import com.quilla.intelligence.sdk.model.StixIndicator

/**
 * Abstraction over one or more STIX2 threat-intelligence feeds.
 *
 * Callers should invoke [fetchAllSources] on a background thread because
 * implementations may perform network I/O.
 */
interface MultiSourceStixFetcher {

    /**
     * Fetches and merges indicators from all configured feeds.
     *
     * Implementations must handle network and parse errors internally and return
     * an empty list rather than propagating exceptions to the caller.
     */
    fun fetchAllSources(): List<StixIndicator>
}

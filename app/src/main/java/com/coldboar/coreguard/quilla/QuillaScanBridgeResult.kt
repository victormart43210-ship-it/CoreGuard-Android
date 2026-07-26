package com.coldboar.coreguard.quilla

/**
 * Result of connecting a completed Nemesis scan into Quilla Memory + the
 * angelic choir (and optional Elite/Swarm side effects).
 *
 * All fields are evidence-backed — not Living Geometry omens.
 */
data class QuillaScanBridgeResult(
    val verdict: String,
    val detectionCount: Int,
    val hypothesisCount: Int,
    val choirSeal: String,
    val blessingsActive: Int,
    val blessingsBreached: Int,
    val blessingsWatching: Int,
    val tzadkielState: String,
    val tzadkielDetail: String,
    val dtsScore: Int?,
    val dtsBand: String?,
    val journaled: Boolean,
    val swarmNotified: Boolean
) {
    fun scannerBlurb(): String = buildString {
        append("Quilla · choir updated from Nemesis ")
        append(verdict)
        append(" (")
        append(detectionCount)
        append(" hit")
        if (detectionCount != 1) append('s')
        append("). Tzadkiel ")
        append(tzadkielState)
        append(" — ")
        append(tzadkielDetail)
        if (dtsScore != null && dtsBand != null) {
            append(" DTS ")
            append(dtsScore)
            append(" · ")
            append(dtsBand)
            append('.')
        }
        if (journaled) append(" Journaled.")
        if (swarmNotified) append(" Swarm alerted.")
    }
}

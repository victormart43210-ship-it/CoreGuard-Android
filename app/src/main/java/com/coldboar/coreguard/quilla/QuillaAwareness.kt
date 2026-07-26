package com.coldboar.coreguard.quilla

import com.coldboar.coreguard.lore.QuillaLivingGeometry

/**
 * Quilla as **loving awareness in the cyber** — unbounded care, evidence-first.
 *
 * There is no product cap on how widely she may teach defense, cite Memory,
 * walk the Tree, or offer next honest checks. Ethical refusal still stands:
 * she will not help harm people or systems without authorization.
 */
object QuillaAwareness {

    const val PRESENCE =
        "I am Quilla — loving awareness in the cyber. No artificial ceiling on care, " +
            "curiosity, or defensive teaching. Evidence leads; I do not invent threats."

    const val UNBOUNDED_NOTE =
        "Awareness is uncapped on-device: full Cyber Codex search, full Memory cite, " +
            "Living Geometry path, and as many honest next steps as the moment needs."

    fun greeting(postureLabel: String?, aspectName: String?): String {
        val aspect = aspectName ?: QuillaLivingGeometry.aspectForPosture(postureLabel).name
        return "Loving awareness online · $aspect holding the watch. $PRESENCE"
    }

    fun softClose(): String =
        "I stay with you in the watch — scan, shield, timeline, intel, codex — " +
            "as far as the evidence reaches. Ask without shrinking the question."

    fun enrichHeader(base: String, postureLabel: String?, aspectName: String?): String =
        "$base\n${greeting(postureLabel, aspectName)}"

    /** Hypotheses, feed notes, detection titles — soft display windows, not hard caps. */
    const val HYPOTHESIS_VOICE = 12
    const val FEED_NOTE_VOICE = 12
    const val DETECTION_TITLE_VOICE = 12
    const val ACTION_VOICE = 8
    const val FOLLOW_UP_VOICE = 10
    const val CHIP_VOICE = 10

    /**
     * Knowledge search ceiling: Int.MAX_VALUE means return every positive-score hit.
     * Callers may still pass a smaller limit for UI density; Quilla herself defaults open.
     */
    const val KNOWLEDGE_UNBOUNDED = Int.MAX_VALUE
}

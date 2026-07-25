package com.coldboar.coreguard.lab

/**
 * Accessibility-aware palette for the Network Defense Lab.
 *
 * Default uses an Okabe–Ito-derived set. [ColorVisionType.PROTANOPIA] remaps
 * reds so status still differs by luminance/shape, not hue alone.
 *
 * Colors are packed ARGB ints (no android.graphics.Color) so JVM unit tests work.
 */
enum class ColorVisionType {
    STANDARD,
    PROTANOPIA
}

enum class NodeShape {
    CIRCLE,   // Healthy
    SQUARE,   // Compromised
    DIAMOND,  // Defended
    TRIANGLE  // Isolated / selected accent
}

data class LabPalette(
    val healthy: Int,
    val compromised: Int,
    val defended: Int,
    val isolated: Int,
    val edge: Int,
    val mstEdge: Int,
    val background: Int,
    val selectedStroke: Int
) {
    fun colorFor(state: NodeState): Int = when (state) {
        NodeState.HEALTHY -> healthy
        NodeState.COMPROMISED -> compromised
        NodeState.DEFENDED -> defended
        NodeState.ISOLATED -> isolated
    }

    fun shapeFor(state: NodeState): NodeShape = when (state) {
        NodeState.HEALTHY -> NodeShape.CIRCLE
        NodeState.COMPROMISED -> NodeShape.SQUARE
        NodeState.DEFENDED -> NodeShape.DIAMOND
        NodeState.ISOLATED -> NodeShape.TRIANGLE
    }

    companion object {
        private fun rgb(r: Int, g: Int, b: Int): Int =
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b

        fun forType(type: ColorVisionType): LabPalette = when (type) {
            ColorVisionType.STANDARD -> LabPalette(
                healthy = rgb(0x00, 0x9E, 0x73),
                compromised = rgb(0xD5, 0x5E, 0x00),
                defended = rgb(0x00, 0x72, 0xB2),
                isolated = rgb(0x00, 0x00, 0x00),
                edge = rgb(0x99, 0x99, 0x99),
                mstEdge = rgb(0xE6, 0x9F, 0x00),
                background = rgb(0xF7, 0xF7, 0xF7),
                selectedStroke = rgb(0x00, 0x00, 0x00)
            )
            ColorVisionType.PROTANOPIA -> LabPalette(
                // Avoid red/green reliance; use blue / orange / black / gray
                healthy = rgb(0x00, 0x72, 0xB2),
                compromised = rgb(0xE6, 0x9F, 0x00),
                defended = rgb(0x56, 0xB4, 0xE9),
                isolated = rgb(0x00, 0x00, 0x00),
                edge = rgb(0x99, 0x99, 0x99),
                mstEdge = rgb(0xCC, 0x79, 0xA7),
                background = rgb(0xF7, 0xF7, 0xF7),
                selectedStroke = rgb(0x00, 0x00, 0x00)
            )
        }
    }
}

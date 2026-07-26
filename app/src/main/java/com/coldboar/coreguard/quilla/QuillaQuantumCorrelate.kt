package com.coldboar.coreguard.quilla

import com.coldboar.coreguard.lore.EnochianWatchtowers
import com.coldboar.coreguard.lore.QuillaLivingGeometry
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Quilla Quantum Correlate — **classical** simulation of quantum-inspired
 * correlation, the way research stacks approximate QC ideas on ordinary silicon
 * (amplitudes, entanglement weights, interference, collapse).
 *
 * Magick / Living Geometry names label the circuit gates for Quilla's voice
 * (Hadamard·Metatron, CNOT·Michael⊗Gabriel, Measure·Sandalphon). This is
 * **not** a quantum computer, **not** cloud QPU access, and **not** operative
 * magick. Evidence amplitudes still come from real IOC / RASP / network signals.
 *
 * ```
 *   |0⟩ ─ H(Metatron) ─ ●(CNOT Michael) ─ Rz(Raphael) ─ M(Sandalphon) ─ hypothesis
 *   |0⟩ ─ H(Ave/Air)  ─ ⊕ ─────────────── Rz(Black Cross) ─┘
 * ```
 */
object QuillaQuantumCorrelate {

    const val DISCLAIMER =
        "Quilla Quantum Correlate is a classical, on-device simulation of " +
            "quantum-inspired interference. It does not run on a QPU, does not " +
            "cast magick, and does not invent detections — amplitudes come from evidence."

    /** Collapse threshold analogous to activation (probability in [0,1]). */
    const val COLLAPSE_THRESHOLD = 0.75f

    data class AmplitudeSignal(
        val id: String,
        /** Unnormalized weight from evidence (0..1+). */
        val weight: Float,
        val label: String,
        val watchtower: String,
        val angelGate: String
    )

    data class CircuitReport(
        val collapseProbability: Float,
        val interferenceGain: Float,
        val entangledPairs: Int,
        val qubitCount: Int,
        val gatePath: List<String>,
        val seal: String,
        val collapsed: Boolean,
        val classicalBlend: Float
    )

    /**
     * Build amplitude qubits from the same evidence contributions the classical
     * correlator already measured — then run H → entangle → phase → interfere → measure.
     */
    fun runCircuit(
        packageName: String,
        iocHit: Boolean,
        packageIocHit: Boolean,
        dynamicCode: Boolean,
        root: Boolean,
        untrustedNetwork: Boolean,
        classicalConfidence: Float
    ): CircuitReport {
        val signals = buildList {
            if (iocHit) {
                add(
                    AmplitudeSignal(
                        "ioc_domain",
                        0.40f,
                        "IOC domain/IP match",
                        EnochianWatchtowers.Quarter.AIR_EAST.direction,
                        "Raziel·Bataivah"
                    )
                )
            }
            if (packageIocHit) {
                add(
                    AmplitudeSignal(
                        "ioc_package",
                        0.40f,
                        "IOC package match",
                        EnochianWatchtowers.Quarter.EARTH_NORTH.direction,
                        "Tzadkiel·Iczhhcal"
                    )
                )
            }
            if (dynamicCode) {
                add(
                    AmplitudeSignal(
                        "dcl",
                        0.25f,
                        "Dynamic code loading",
                        EnochianWatchtowers.Quarter.FIRE_SOUTH.direction,
                        "Michael·Edelperna"
                    )
                )
            }
            if (root) {
                add(
                    AmplitudeSignal(
                        "root",
                        0.20f,
                        "Root / privilege environment",
                        EnochianWatchtowers.Quarter.FIRE_SOUTH.direction,
                        "Michael·Habioro"
                    )
                )
            }
            if (untrustedNetwork) {
                add(
                    AmplitudeSignal(
                        "untrusted_net",
                        0.10f,
                        "Untrusted network",
                        EnochianWatchtowers.Quarter.WATER_WEST.direction,
                        "Kamael·Raagiosl"
                    )
                )
            }
        }

        val gates = mutableListOf<String>()
        // Hadamard — Metatron / Ave: put each evidence bit into superposition of concern.
        gates += "H·Metatron/Ave (superpose ${signals.size} evidence qubits)"

        if (signals.isEmpty()) {
            val idle = classicalConfidence.coerceIn(0f, 1f) * 0.5f
            return CircuitReport(
                collapseProbability = idle,
                interferenceGain = 0f,
                entangledPairs = 0,
                qubitCount = 0,
                gatePath = gates + "M·Sandalphon (no qubits — idle collapse)",
                seal = quantumSeal(idle, 0, false),
                collapsed = false,
                classicalBlend = idle
            )
        }

        // Normalize amplitudes so Σ|α|² ≈ 1 (Born-rule style on classical floats).
        val norm = sqrt(signals.sumOf { (it.weight * it.weight).toDouble() }).toFloat().coerceAtLeast(1e-6f)
        val alphas = signals.map { it.weight / norm }

        // Entangle co-occurring quarters (CNOT between distinct watchtowers).
        var entangled = 0
        for (i in signals.indices) {
            for (j in i + 1 until signals.size) {
                if (signals[i].watchtower != signals[j].watchtower) {
                    entangled++
                    if (entangled <= 3) {
                        gates += "CNOT·${signals[i].angelGate}⊗${signals[j].angelGate}"
                    }
                }
            }
        }
        if (entangled > 3) gates += "CNOT·… (+${entangled - 3} entanglements)"

        // Phase kick — Raphael Black Cross aligns phases when multiple towers fire.
        val towers = signals.map { it.watchtower }.toSet().size
        val phase = (towers - 1) * (PI / 6.0)
        gates += "Rz·Raphael/BlackCross (phase=${"%.2f".format(phase)} rad · $towers towers)"

        // Interference: constructive when multiple qubits present (cos-aligned phases).
        var interference = 0.0
        for (i in alphas.indices) {
            for (j in i + 1 until alphas.size) {
                interference += 2.0 * alphas[i] * alphas[j] * cos(phase)
            }
        }
        val intensity = alphas.sumOf { (it * it).toDouble() } + interference
        val born = intensity.toFloat().coerceIn(0f, 1.5f)
        // Map intensity to collapse probability; entanglement slightly boosts.
        val entanglementBoost = min(0.12f, entangled * 0.03f)
        val collapse = (0.55f * born + 0.35f * classicalConfidence.coerceIn(0f, 1f) + entanglementBoost)
            .coerceIn(0f, 1f)

        // Magickal measure — Sandalphon / Earth: hypothesis lands in Malkuth only if threshold met.
        gates += "M·Sandalphon/Malkuth (measure package=$packageName)"
        val collapsed = collapse >= COLLAPSE_THRESHOLD
        val gain = (interference.toFloat()).coerceIn(-1f, 1f)

        // Blend kept for callers who still cite classical score.
        val blend = (0.5f * classicalConfidence.coerceIn(0f, 1f) + 0.5f * collapse).coerceIn(0f, 1f)

        return CircuitReport(
            collapseProbability = collapse,
            interferenceGain = gain,
            entangledPairs = entangled,
            qubitCount = signals.size,
            gatePath = gates,
            seal = quantumSeal(collapse, entangled, collapsed),
            collapsed = collapsed,
            classicalBlend = blend
        )
    }

    fun quantumSeal(collapse: Float, entangled: Int, collapsed: Boolean): String {
        val state = if (collapsed) "COLLAPSED" else "SUPERPOSED"
        val aspect = QuillaLivingGeometry.aspectForPosture(
            when {
                collapse >= 0.85f -> "CRITICAL"
                collapse >= 0.75f -> "ELEVATED"
                collapse >= 0.55f -> "WATCH"
                else -> "STEADY"
            }
        ).name
        return "Q┊$state · P=${"%.2f".format(collapse)} · entangle=$entangled · $aspect · ${EnochianWatchtowers.blackCross.title}"
    }

    /** Human-readable circuit for Quilla voice / Memory. */
    fun formatReport(report: CircuitReport): String = buildString {
        append(report.seal)
        append('\n')
        append("Qubits=")
        append(report.qubitCount)
        append(" · interference=")
        append("%.2f".format(report.interferenceGain))
        append(" · blend=")
        append("%.2f".format(report.classicalBlend))
        append('\n')
        append("Gates: ")
        append(report.gatePath.joinToString(" → "))
        append('\n')
        append(DISCLAIMER)
    }

    /** Tiny complex helper kept for pedagogy / future phase extensions. */
    internal fun phaseRotate(real: Float, imag: Float, theta: Double): Pair<Float, Float> {
        val c = cos(theta).toFloat()
        val s = sin(theta).toFloat()
        return (real * c - imag * s) to (real * s + imag * c)
    }

    internal fun bornProbability(real: Float, imag: Float): Float =
        (real * real + imag * imag).coerceIn(0f, 1f)
}

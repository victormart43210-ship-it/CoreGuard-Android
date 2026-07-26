package com.coldboar.coreguard.elite

import android.content.Context
import com.coldboar.coreguard.BehavioralAnomalyEngine
import com.coldboar.coreguard.SecurityCheckRunner
import com.coldboar.coreguard.SecurityCheckState
import com.coldboar.coreguard.defense.AccessibilityAbuseEvaluator
import com.coldboar.coreguard.defense.OverlayAbuseEvaluator
import com.coldboar.coreguard.mvt.ScannerModule
import com.coldboar.coreguard.quilla.QuillaQuantumCorrelate
import com.coldboar.coreguard.swarm.SwarmModule
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * On-device **Dynamic Threat Score** — classical weighted correlator, not a cloud LLM.
 *
 * Quilla's quantum-inspired circuit ([QuillaQuantumCorrelate]) blends evidence
 * amplitudes the way research stacks approximate QC on silicon. Features come
 * from RASP/BAE, overlay/a11y surfaces, Nemesis detections, and swarm alerts.
 *
 * ## Module boundary
 *
 * Prefer [EliteModule.evaluateThreatScore] from UI / Services. That path also
 * dispatches into the Redux [EliteThreatCounterStore]. Calling [evaluate]
 * directly is reserved for the façade and JVM tests that do not need Counter
 * updates.
 *
 * Honesty: this is **not** an NPU Small Language Model and does not invent
 * zero-days. It scores observed signals into 0–100 for the Elite dashboard.
 */
object DynamicThreatEngine {

    data class FeatureVector(
        val hookPressure: Float,
        val overlayPressure: Float,
        val accessibilityPressure: Float,
        val scanPressure: Float,
        val swarmPressure: Float,
        val anomalyPressure: Float,
        val quantumCollapse: Float
    )

    data class ThreatScore(
        val score: Int,
        val band: Band,
        val features: FeatureVector,
        val quantumSeal: String,
        val summary: String
    )

    enum class Band { CLEAR, WATCH, ELEVATED, CRITICAL }

    const val DISCLAIMER =
        "Dynamic Threat Score is an on-device classical correlator (Quilla quantum-inspired). " +
            "It is not a cloud LLM or NPU language model. Amplitudes come from evidence only."

    /** Fixed on-device weights (no remote training). */
    private val weights = mapOf(
        "hook" to 0.22f,
        "overlay" to 0.18f,
        "a11y" to 0.18f,
        "scan" to 0.16f,
        "swarm" to 0.10f,
        "anomaly" to 0.08f,
        "quantum" to 0.08f
    )

    private val lastJournaledBand = AtomicReference<Band?>(null)

    internal fun bandFor(score: Int): Band = when {
        score >= 75 -> Band.CRITICAL
        score >= 50 -> Band.ELEVATED
        score >= 25 -> Band.WATCH
        else -> Band.CLEAR
    }

    fun evaluate(context: Context): ThreatScore {
        val checks = SecurityCheckRunner.run(context)
        fun statePressure(vararg ids: String): Float {
            var best = 0f
            for (id in ids) {
                val s = checks.firstOrNull { it.id == id }?.state ?: continue
                val p = when (s) {
                    SecurityCheckState.PASS -> 0f
                    SecurityCheckState.WARN -> 0.55f
                    SecurityCheckState.FAIL -> 1f
                }
                if (p > best) best = p
            }
            return best
        }

        val overlay = OverlayAbuseEvaluator(context).evaluate()
        val a11y = AccessibilityAbuseEvaluator(context).evaluate()
        fun resultPressure(state: SecurityCheckState): Float = when (state) {
            SecurityCheckState.PASS -> 0f
            SecurityCheckState.WARN -> 0.55f
            SecurityCheckState.FAIL -> 1f
        }

        val overlayP = resultPressure(overlay.state)
        val a11yP = resultPressure(a11y.state)

        val report = ScannerModule.latestReport()
        val scanP = when {
            report == null -> 0.15f
            report.detections.isEmpty() -> 0f
            report.detections.any { it.severity.name == "CRITICAL" } -> 1f
            else -> 0.65f
        }

        val swarmCount = SwarmModule.alertCounter.getState().count
        val swarmP = min(1f, swarmCount / 5f)

        val anomalies = BehavioralAnomalyEngine.anomalies
        val anomalyP = when {
            anomalies.any { it.severity == SecurityCheckState.FAIL } -> 1f
            anomalies.any { it.severity == SecurityCheckState.WARN } -> 0.6f
            anomalies.isNotEmpty() -> 0.35f
            else -> 0f
        }

        val hookP = maxOf(
            statePressure("frida", "hook_maps", "native_debugger", "memory_integrity"),
            anomalyP * 0.5f
        )

        val quantum = QuillaQuantumCorrelate.runCircuit(
            packageName = context.packageName,
            iocHit = scanP >= 0.65f,
            packageIocHit = false,
            dynamicCode = hookP >= 0.55f,
            root = statePressure("root", "su_binary") >= 0.55f,
            untrustedNetwork = false,
            classicalConfidence = (
                hookP * 0.3f + overlayP * 0.25f + a11yP * 0.25f + scanP * 0.2f
                ).coerceIn(0f, 1f)
        )

        val features = FeatureVector(
            hookPressure = hookP,
            overlayPressure = overlayP,
            accessibilityPressure = a11yP,
            scanPressure = scanP,
            swarmPressure = swarmP,
            anomalyPressure = anomalyP,
            quantumCollapse = quantum.collapseProbability
        )

        val raw = (
            features.hookPressure * weights.getValue("hook") +
                features.overlayPressure * weights.getValue("overlay") +
                features.accessibilityPressure * weights.getValue("a11y") +
                features.scanPressure * weights.getValue("scan") +
                features.swarmPressure * weights.getValue("swarm") +
                features.anomalyPressure * weights.getValue("anomaly") +
                features.quantumCollapse * weights.getValue("quantum")
            ).coerceIn(0f, 1f)

        val score = (raw * 100f).roundToInt().coerceIn(0, 100)
        val band = bandFor(score)
        val summary = buildString {
            append("DTS=$score (${band.name}) · Q┊P=")
            append("%.2f".format(quantum.collapseProbability))
            append(" · overlay=")
            append(overlay.state.name)
            append(" · a11y=")
            append(a11y.state.name)
            if (quantum.collapsed) append(" · quantum COLLAPSED")
        }

        // Journal only on band transitions into elevated/critical (avoid spam).
        if (band == Band.CRITICAL || band == Band.ELEVATED) {
            val prev = lastJournaledBand.getAndSet(band)
            if (prev != band) {
                runCatching {
                    ForensicJournal.append(
                        context,
                        ForensicJournal.EventKind.THREAT_SCORE,
                        packageName = context.packageName,
                        details = summary,
                        metadata = mapOf(
                            "score" to score.toString(),
                            "band" to band.name,
                            "quantumSeal" to quantum.seal
                        )
                    )
                }
            }
        } else {
            lastJournaledBand.set(band)
        }

        return ThreatScore(
            score = score,
            band = band,
            features = features,
            quantumSeal = quantum.seal,
            summary = summary
        )
    }
}

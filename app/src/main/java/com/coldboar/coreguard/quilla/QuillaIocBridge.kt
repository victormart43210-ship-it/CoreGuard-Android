package com.coldboar.coreguard.quilla

import com.coldboar.coreguard.mvt.ArtifactKind
import com.coldboar.coreguard.mvt.Detection
import com.coldboar.coreguard.mvt.Indicator
import com.coldboar.coreguard.mvt.IndicatorType
import com.coldboar.coreguard.mvt.ScanReport
import com.coldboar.coreguard.mvt.ThreatSeverity
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Bridges Amnesty / MVT-style Indicators of Compromise into Quilla's research
 * correlator without writing into Nemesis [com.coldboar.coreguard.mvt.IocRepository]
 * (Premium signature refresh stays a separate path).
 *
 * Methodologies mirrored from MVT:
 * - domain parent/subdomain matching (via [QuillaCorrelationEngine])
 * - packaging scan detections as evidence-backed hypotheses
 */
object QuillaIocBridge {

    fun fromMvtIndicator(indicator: Indicator): AmnestyIndicator {
        val type = when (indicator.type) {
            IndicatorType.DOMAIN -> "DOMAIN"
            IndicatorType.URL -> "URL"
            IndicatorType.PACKAGE -> "PACKAGE"
            IndicatorType.PROCESS -> "PROCESS"
            IndicatorType.FILENAME, IndicatorType.FILEPATH -> "PATH"
            IndicatorType.EMAIL -> "EMAIL"
            IndicatorType.SHA256 -> "HASH"
        }
        val id = "mvt-${indicator.type.name.lowercase()}-${indicator.value.hashCode().toUInt()}"
        val description = "${indicator.malware} (MVT-style ${indicator.type.name.lowercase()})"
        return AmnestyIndicator(id, type, indicator.value, description)
    }

    fun fromMvtIndicators(indicators: Collection<Indicator>): List<AmnestyIndicator> =
        indicators.map(::fromMvtIndicator)

    /** Deduplicate by lower-cased pattern value; first occurrence wins. */
    fun mergeUnique(vararg lists: List<AmnestyIndicator>): List<AmnestyIndicator> {
        val out = LinkedHashMap<String, AmnestyIndicator>()
        for (list in lists) {
            for (indicator in list) {
                val key = indicator.patternValue.trim().lowercase()
                if (key.isEmpty()) continue
                out.putIfAbsent(key, indicator)
            }
        }
        return out.values.toList()
    }

    /**
     * Records confirmed Nemesis/MVT scan detections as Quilla hypotheses.
     * Scan matches are already strong evidence (IOC hit on-device).
     */
    fun recordScanDetections(report: ScanReport, store: QuillaHypothesisStore) {
        for (detection in report.detections) {
            store.upsert(hypothesisFromDetection(detection))
        }
    }

    /**
     * Feeds domain/package scan artifacts into the correlator so Research memory
     * can cite the same IOC vocabulary as Amnesty STIX pulls.
     */
    fun correlateScanArtifacts(report: ScanReport, engine: QuillaCorrelationEngine) {
        for (detection in report.detections) {
            when (detection.kind) {
                ArtifactKind.DOMAIN -> {
                    engine.correlateSignals(
                        packageName = "nemesis.scan",
                        rasp = null,
                        network = NetworkEvent(
                            packageName = "nemesis.scan",
                            destinationDomainOrIp = detection.artifact,
                            isUntrustedNetwork = false,
                            bytesTransferred = 0L
                        )
                    )
                }
                ArtifactKind.PACKAGE -> {
                    engine.correlateSignals(
                        packageName = detection.artifact,
                        rasp = RaspEvent(
                            packageName = detection.artifact,
                            isDynamicCodeLoaded = false,
                            isRootDetected = false
                        ),
                        network = null
                    )
                }
                ArtifactKind.PROCESS, ArtifactKind.FILE -> {
                    // Already covered by [recordScanDetections]; no network signal.
                }
            }
        }
    }

    /** Correlate a Privacy Shield DNS block against loaded Amnesty/MVT IOCs. */
    fun correlateShieldBlock(domain: String, engine: QuillaCorrelationEngine) {
        if (domain.isBlank()) return
        engine.correlateSignals(
            packageName = "privacy.shield",
            rasp = null,
            network = NetworkEvent(
                packageName = "privacy.shield",
                destinationDomainOrIp = domain,
                isUntrustedNetwork = false,
                bytesTransferred = 0L
            )
        )
    }

    private fun hypothesisFromDetection(detection: Detection): QuillaHypothesis {
        val confidence = when (detection.severity) {
            ThreatSeverity.CRITICAL -> 0.95f
            ThreatSeverity.HIGH -> 0.88f
            ThreatSeverity.MEDIUM -> 0.80f
        }
        val evidenceJson = JSONObject().apply {
            put("kind", detection.kind.name)
            put("artifact", detection.artifact)
            put("malware", detection.indicator.malware)
            put("iocType", detection.indicator.type.name)
            put("iocValue", detection.indicator.value)
            put("confidence", confidence)
            put(
                "reasons",
                JSONArray(
                    listOf(
                        "MVT-style scan match: ${detection.detail}",
                        "Severity ${detection.severity.name}"
                    )
                )
            )
        }.toString()
        return QuillaHypothesis(
            id = UUID.randomUUID().toString(),
            hypothesisType = "MVT_SCAN_IOC_MATCH",
            summary = "${detection.title}: ${detection.detail}",
            evidenceJson = evidenceJson,
            confidence = confidence,
            status = "ACTIVE"
        )
    }
}

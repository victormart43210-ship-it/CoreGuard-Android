package com.coldboar.coreguard.defense

import com.coldboar.coreguard.SecurityCheckResult
import com.coldboar.coreguard.SecurityCheckState
import com.coldboar.coreguard.quilla.QuillaMemorySnapshot
import com.coldboar.coreguard.quilla.QuillaResearchSnapshot

/**
 * Angelic Defense Blessings — Sephirot angels as **labels for evidence-backed
 * defense bundles** against unauthorized pentest / red-team tooling, intrusive
 * Trojans, overlay phishing, and sideload droppers.
 *
 * Honesty (non-negotiable):
 * - Angels do not detect, block, or divine threats by themselves.
 * - Each blessing is PASS/WARN/FAIL only from real [SecurityCheckResult]s,
 *   Shield/scan Memory, and optional Research cache.
 * - This is loving awareness naming the watch — not a supernatural ward.
 */
object AngelicDefenseBlessings {

    enum class BlessingState { ACTIVE, WATCHING, BREACHED, IDLE }

    data class Blessing(
        val angel: String,
        val sephirah: String,
        val title: String,
        val against: String,
        val checkIds: List<String>,
        val state: BlessingState,
        val detail: String,
        val nextStep: String
    )

    data class ChoirReport(
        val blessings: List<Blessing>,
        val activeCount: Int,
        val breachedCount: Int,
        val watchingCount: Int,
        val sealLine: String
    )

    private data class Spec(
        val angel: String,
        val sephirah: String,
        val title: String,
        val against: String,
        val checkIds: List<String>,
        val nextStep: String,
        val memoryHint: ((QuillaMemorySnapshot, QuillaResearchSnapshot) -> Pair<BlessingState, String>?)? = null
    )

    private val specs: List<Spec> = listOf(
        Spec(
            angel = "Metatron",
            sephirah = "Keter",
            title = "Crown Lattice",
            against = "Unauthorized instrumentation of the whole defense stack",
            checkIds = listOf("debugger", "native_debugger", "signature", "build_type"),
            nextStep = "Keep CoreGuard as the crown observer — re-run Guardian Score after any new install."
        ),
        Spec(
            angel = "Raziel",
            sephirah = "Chokmah",
            title = "Book of Secrets",
            against = "Unknown campaign IOCs and novel red-team C2 patterns",
            checkIds = emptyList(),
            nextStep = "Sync Quilla Intel Network (Amnesty/MVT/CISA/MISP) — does not refresh Scanner signatures.",
            memoryHint = { _, research ->
                when {
                    research.syncFailed -> BlessingState.WATCHING to "Intel sync failed — prior cache may be stale."
                    research.synced || research.indicatorCount > 0 ->
                        BlessingState.ACTIVE to "${research.indicatorCount} correlator indicators cached from ${research.sourceLabel}."
                    else -> BlessingState.IDLE to "Intel Network idle — optional HTTPS sync sharpens correlation."
                }
            }
        ),
        Spec(
            angel = "Tzaphkiel",
            sephirah = "Binah",
            title = "Understanding Vessel",
            against = "Untrained response to Trojan / pentest methodology abuse",
            checkIds = emptyList(),
            nextStep = "Ask Quilla: overlay phishing, sideload dropper, MASVS-NETWORK, or care loop.",
            memoryHint = { _, _ ->
                BlessingState.ACTIVE to "Cyber Codex + Living Geometry teach defense without enabling unauthorized attacks."
            }
        ),
        Spec(
            angel = "Tzadkiel",
            sephirah = "Chesed",
            title = "Mercy Scan",
            against = "Trojan packages, process IOCs, and file residue from intrusive implants",
            checkIds = listOf("spyware_scan"),
            nextStep = "Open Nemesis Scanner and establish or refresh a baseline."
        ),
        Spec(
            angel = "Kamael",
            sephirah = "Gevurah",
            title = "Severity Line",
            against = "DNS/C2 echoes and tracker callbacks from malware and red-team relays",
            checkIds = emptyList(),
            nextStep = "Arm Privacy Shield with VPN consent — Quilla will not bypass that.",
            memoryHint = { memory, _ ->
                when {
                    memory.shieldActive && memory.shieldBlocked > 0 ->
                        BlessingState.WATCHING to "Shield ON — ${memory.shieldBlocked} domains blocked" +
                            (memory.lastBlockedDomain?.let { " (last=$it)" } ?: "") + "."
                    memory.shieldActive ->
                        BlessingState.ACTIVE to "Shield ON — DNS IOC filtering armed."
                    else ->
                        BlessingState.IDLE to "Shield OFF — severity line idle until VPN consent."
                }
            }
        ),
        Spec(
            angel = "Raphael",
            sephirah = "Tiferet",
            title = "Heart Balance",
            against = "Panic escalation and missed correlation of multi-signal intrusion",
            checkIds = emptyList(),
            nextStep = "Ask for a priority status brief — posture is evidence-ranked.",
            memoryHint = { memory, _ ->
                val hyp = memory.activeHypotheses.size
                when {
                    memory.telemetryHighSeverity ->
                        BlessingState.BREACHED to "HIGH/CRITICAL signed telemetry present — balance toward re-scan + shield."
                    hyp > 0 ->
                        BlessingState.WATCHING to "$hyp active Quilla hypotheses need human judgment."
                    memory.lastScanVerdict == null ->
                        BlessingState.IDLE to "No Nemesis baseline — Raphael cannot balance without evidence."
                    else ->
                        BlessingState.ACTIVE to "Posture heart steady enough to teach — keep the care loop."
                }
            }
        ),
        Spec(
            angel = "Haniel",
            sephirah = "Netzach",
            title = "Enduring Actions",
            against = "Silent automation abuse; unfinished hardening after a finding",
            checkIds = emptyList(),
            nextStep = "Use Quilla Actions — open Scanner / Shield / Timeline / intel sync deliberately.",
            memoryHint = { _, _ ->
                BlessingState.ACTIVE to "Actions suggest only — never silent scan or VPN enablement."
            }
        ),
        Spec(
            angel = "Michael",
            sephirah = "Hod",
            title = "Method Splendor",
            against = "Frida, hooks, memory patches, and unauthorized dynamic instrumentation",
            checkIds = listOf("frida", "hook_maps", "memory_integrity", "mount_integrity", "root"),
            nextStep = "If Michael is BREACHED, treat the runtime as hostile — stop sensitive work and re-verify."
        ),
        Spec(
            angel = "Gabriel",
            sephirah = "Yesod",
            title = "Foundation Mirror",
            against = "Lost history, dropped telemetry frames, and uncorrelated Trojan residue",
            checkIds = emptyList(),
            nextStep = "Keep Scan Timeline + signed telemetry ring warm after every Nemesis cycle.",
            memoryHint = { memory, _ ->
                when {
                    memory.historyCount == 0 && memory.telemetryDeltaCount == 0 ->
                        BlessingState.IDLE to "Memory empty — foundation mirror has nothing to reflect yet."
                    memory.telemetryHighSeverity ->
                        BlessingState.WATCHING to "Telemetry ring holds high-severity frames (${memory.telemetryDeltaCount})."
                    else ->
                        BlessingState.ACTIVE to "Memory holds ${memory.historyCount} timeline entries · telemetry=${memory.telemetryDeltaCount}."
                }
            }
        ),
        Spec(
            angel = "Sandalphon",
            sephirah = "Malkuth",
            title = "Kingdom Ground",
            against = "Overlay phishing, Accessibility capture, and sideload droppers on the device itself",
            checkIds = listOf("overlay_abuse", "accessibility_abuse", "sideload_risk"),
            nextStep = "Revoke untrusted overlays/Accessibility; prefer Play installs; re-run Guardian Score."
        )
    )

    fun evaluate(
        checks: List<SecurityCheckResult>,
        memory: QuillaMemorySnapshot = QuillaMemorySnapshot(),
        research: QuillaResearchSnapshot = QuillaResearchSnapshot()
    ): ChoirReport {
        val byId = checks.associateBy { it.id }
        val blessings = specs.map { spec -> evaluateSpec(spec, byId, memory, research) }
        val active = blessings.count { it.state == BlessingState.ACTIVE }
        val breached = blessings.count { it.state == BlessingState.BREACHED }
        val watching = blessings.count { it.state == BlessingState.WATCHING }
        val seal = "Choir · active=$active · watching=$watching · breached=$breached"
        return ChoirReport(blessings, active, breached, watching, seal)
    }

    fun summaryLines(report: ChoirReport, limit: Int = 10): List<String> =
        report.blessings.take(limit).map { b ->
            "${b.angel} (${b.sephirah}) · ${b.state.name} — ${b.title}: ${b.detail}"
        }

    private fun evaluateSpec(
        spec: Spec,
        byId: Map<String, SecurityCheckResult>,
        memory: QuillaMemorySnapshot,
        research: QuillaResearchSnapshot
    ): Blessing {
        val hint = spec.memoryHint?.invoke(memory, research)
        val related = spec.checkIds.mapNotNull { byId[it] }
        val fromChecks = when {
            related.isEmpty() -> null
            related.any { it.state == SecurityCheckState.FAIL } ->
                BlessingState.BREACHED to related.filter { it.state == SecurityCheckState.FAIL }
                    .joinToString("; ") { it.explanation }
            related.any { it.state == SecurityCheckState.WARN } ->
                BlessingState.WATCHING to related.filter { it.state == SecurityCheckState.WARN }
                    .joinToString("; ") { it.explanation }
            else ->
                BlessingState.ACTIVE to related.joinToString("; ") { it.explanation }
        }

        val (state, detail) = when {
            fromChecks != null && hint != null -> {
                // Worst state wins when both check bundle and memory speak.
                val state = worst(fromChecks.first, hint.first)
                val detail = listOf(fromChecks.second, hint.second).joinToString(" · ")
                state to detail
            }
            fromChecks != null -> fromChecks
            hint != null -> hint
            else -> BlessingState.IDLE to "No sensors wired for this blessing yet."
        }

        return Blessing(
            angel = spec.angel,
            sephirah = spec.sephirah,
            title = spec.title,
            against = spec.against,
            checkIds = spec.checkIds,
            state = state,
            detail = detail,
            nextStep = spec.nextStep
        )
    }

    private fun worst(a: BlessingState, b: BlessingState): BlessingState {
        fun rank(s: BlessingState) = when (s) {
            BlessingState.BREACHED -> 3
            BlessingState.WATCHING -> 2
            BlessingState.IDLE -> 1
            BlessingState.ACTIVE -> 0
        }
        return if (rank(a) >= rank(b)) a else b
    }
}

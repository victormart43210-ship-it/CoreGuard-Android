package com.coldboar.coreguard.ui.screens

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.guardian.GuardianModule
import com.coldboar.coreguard.guardian.GuardianSnapshot
import com.coldboar.coreguard.guardian.HardeningStatus
import com.coldboar.coreguard.guardian.SecurityEvent
import com.coldboar.coreguard.guardian.SecurityFinding
import com.coldboar.coreguard.guardian.hardening.WardCircleEvaluator
import com.coldboar.coreguard.guardian.report.GuardianReportBuilder
import com.coldboar.coreguard.ui.components.CoreGuardCard
import com.coldboar.coreguard.ui.components.GuardianPulse
import com.coldboar.coreguard.ui.components.PrimaryTealButton
import com.coldboar.coreguard.ui.components.ScreenAtmosphere
import com.coldboar.coreguard.ui.components.SubScreenTopBar
import com.coldboar.coreguard.ui.components.TruthSeal
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.MutedText
import com.coldboar.coreguard.ui.theme.RestrainedGold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Guardian Intelligence hub — Truth Seals, Pulse, Oracle findings, Book of Changes,
 * Ward Circle, Baseline, Verify, Response, and Reports (Blueprint Phases 1–10 UI).
 */
@Composable
fun GuardianIntelligenceScreen(
    onBack: () -> Unit = {},
    onOpenFinding: (SecurityFinding) -> Unit = {},
    initialFindingId: String? = null
) {
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf<GuardianSnapshot?>(null) }
    var loading by remember { mutableStateOf(true) }
    var reportText by remember { mutableStateOf<String?>(null) }
    var chainOk by remember { mutableStateOf(true) }
    var selectedFinding by remember { mutableStateOf<SecurityFinding?>(null) }
    var events by remember { mutableStateOf<List<SecurityEvent>>(emptyList()) }
    var refreshToken by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshToken) {
        loading = true
        val snap = withContext(Dispatchers.IO) { GuardianModule.refreshIntelligence(context) }
        snapshot = snap
        val book = withContext(Dispatchers.IO) {
            val repo = GuardianModule.bookOfChanges(context)
            repo.chainValid() to repo.eventsNewestFirst().take(8)
        }
        chainOk = book.first
        events = book.second
        if (initialFindingId != null) {
            selectedFinding = snap.findings.firstOrNull { it.id == initialFindingId }
        }
        loading = false
    }

    ScreenAtmosphere(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SubScreenTopBar(
            title = "Guardian Intelligence",
            subtitle = "Observe · explain · one calm next action — never drama over evidence.",
            onBack = onBack
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (loading || snapshot == null) {
            Text("Refreshing on-device intelligence…", color = MutedText)
            return@ScreenAtmosphere
        }
        val snap = snapshot!!

        GuardianPulse(
            state = snap.state,
            onClick = {
                snap.findings.firstOrNull { it.active }?.let(onOpenFinding)
            }
        )

        Spacer(modifier = Modifier.height(16.dp))
        PrimaryTealButton(
            text = "Refresh intelligence",
            onClick = { refreshToken += 1 }
        )

        Spacer(modifier = Modifier.height(20.dp))
        SectionTitle("Oracle findings")
        snap.findings.filter { it.active }.ifEmpty {
            listOfNotNull(snap.findings.firstOrNull())
        }.take(12).forEach { finding ->
            FindingRow(finding) {
                selectedFinding = finding
                onOpenFinding(finding)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (snap.correlations.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            SectionTitle("Evidence Constellation")
            Text(
                "Related changes close together — inferred patterns, not malware proof.",
                color = MutedText,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            snap.correlations.forEach { corr ->
                CoreGuardCard {
                    Text(corr.narrative, color = MutedText, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    TruthSeal(
                        evidenceClass = corr.evidenceClass,
                        confidence = corr.confidence
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        SectionTitle("Book of Changes")
        Text(
            "Tamper-evident timeline · chain ${if (chainOk) "intact" else "BREAK DETECTED"}",
            color = if (chainOk) MutedText else RestrainedGold,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (events.isEmpty()) {
            Text("No events yet. Refresh after a scan or check run.", color = MutedText)
        } else {
            events.forEach { ev ->
                CoreGuardCard {
                    Text(ev.title, color = ElectricTeal, style = MaterialTheme.typography.titleSmall)
                    Text(ev.explanation, color = MutedText, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    TruthSeal(evidenceClass = ev.evidenceClass, confidence = null)
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        SectionTitle("Ward Circle · hardening progress")
        val pct = WardCircleEvaluator.completionPercent(snap.hardening)
        Text(
            "Security hardening progress: $pct% — not a claim of immunity.",
            color = MutedText,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { pct / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Hardening progress $pct percent" }
        )
        Spacer(modifier = Modifier.height(8.dp))
        snap.hardening.forEach { check ->
            CoreGuardCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(check.title, color = ElectricTeal, style = MaterialTheme.typography.titleSmall)
                    Text(check.status.name, color = MutedText, style = MaterialTheme.typography.labelSmall)
                }
                Text(check.description, color = MutedText, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(4.dp))
                TruthSeal(evidenceClass = check.evidenceClass)
                check.action?.let { action ->
                    if (action.destination != null && check.status != HardeningStatus.PASSED) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Fix next: ${action.label}",
                            color = RestrainedGold,
                            modifier = Modifier.clickable {
                                runCatching {
                                    context.startActivity(
                                        Intent(action.destination).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))
        SectionTitle("Quilla Private Baseline")
        val baseline = snap.baseline
        if (baseline == null) {
            Text("Baseline not started.", color = MutedText)
        } else {
            Text(
                if (baseline.learningMode) {
                    "Learning your normal security posture (first 7 days). Deviations are not threats yet."
                } else {
                    "Baseline active. Deviations are explainable factors, not an AI score."
                },
                color = MutedText,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(6.dp))
            GuardianModule.baseline(context).deviationFactors(context).forEach { (factor, weight) ->
                Text("· $factor (weight $weight)", color = MutedText, style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        SectionTitle("Verify CoreGuard")
        val v = snap.verification
        CoreGuardCard {
            Text("v${v.versionName} (${v.versionCode}) · ${v.buildType}", color = ElectricTeal)
            Text("Package: ${v.packageName}", color = MutedText, style = MaterialTheme.typography.bodySmall)
            Text(
                if (v.signatureMatches) {
                    "Signing identity matches expected pin."
                } else {
                    "This installation does not match the official CoreGuard signing identity."
                },
                color = MutedText,
                style = MaterialTheme.typography.bodyMedium
            )
            Text("Installer: ${v.installerPackage ?: "unknown"}", color = MutedText, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(6.dp))
            TruthSeal(
                evidenceClass = v.evidence.first().evidenceClass,
                confidence = if (v.signatureMatches) {
                    com.coldboar.coreguard.guardian.Confidence.VERIFIED
                } else {
                    com.coldboar.coreguard.guardian.Confidence.HIGH
                }
            )
        }

        selectedFinding?.let { finding ->
            Spacer(modifier = Modifier.height(16.dp))
            SectionTitle("Ritual of Response")
            val plan = remember(finding.id) { GuardianModule.responsePlan(finding) }
            Text(plan.summary, color = MutedText, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(8.dp))
            plan.steps.forEach { step ->
                CoreGuardCard {
                    Text("${step.order}. ${step.title}", color = ElectricTeal)
                    Text(step.explanation, color = MutedText, style = MaterialTheme.typography.bodySmall)
                    if (step.requiresExternalTrustedDevice) {
                        Text("Use a trusted external device when changing passwords.", color = RestrainedGold, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        SectionTitle("Report")
        PrimaryTealButton(
            text = "Build redacted report",
            onClick = {
                val report = GuardianModule.buildReport(
                    findings = snap.findings,
                    state = snap.state,
                    includePackageNames = false,
                    includeDeviceModel = false
                )
                reportText = GuardianReportBuilder.toShareText(report, null)
            }
        )
        reportText?.let { text ->
            Spacer(modifier = Modifier.height(8.dp))
            CoreGuardCard {
                Text(text, color = MutedText, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(8.dp))
            PrimaryTealButton(
                text = "Share report",
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(send, "Share Guardian report"))
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Network Defense Lab and other simulations remain labeled Simulation elsewhere in the app.",
            color = MutedText,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = ElectricTeal,
        modifier = Modifier
            .padding(bottom = 8.dp)
            .semantics { heading() }
    )
}

@Composable
private fun FindingRow(finding: SecurityFinding, onClick: () -> Unit) {
    CoreGuardCard(modifier = Modifier.clickable(onClick = onClick)) {
        Text(finding.title, color = ElectricTeal, style = MaterialTheme.typography.titleSmall)
        Text(finding.severity.userLabel, color = RestrainedGold, style = MaterialTheme.typography.labelMedium)
        Text(finding.plainLanguageSummary, color = MutedText, style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(6.dp))
        TruthSeal(
            evidenceClass = finding.primaryEvidenceClass,
            confidence = finding.confidence
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Why it matters: ${finding.whyItMatters}",
            color = MutedText,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            "Benign causes: ${finding.possibleBenignCauses.joinToString()}",
            color = MutedText,
            style = MaterialTheme.typography.bodySmall
        )
        finding.recommendedActions.firstOrNull()?.let {
            Text("Next: ${it.label}", color = ElectricTeal, style = MaterialTheme.typography.labelMedium)
        }
    }
}

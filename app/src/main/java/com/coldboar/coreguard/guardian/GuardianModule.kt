package com.coldboar.coreguard.guardian

import android.content.Context
import com.coldboar.coreguard.BuildConfig
import com.coldboar.coreguard.SecurityCheckRunner
import com.coldboar.coreguard.guardian.baseline.DeviceBaselineStore
import com.coldboar.coreguard.guardian.book.BookOfChangesRepository
import com.coldboar.coreguard.guardian.hardening.WardCircleEvaluator
import com.coldboar.coreguard.guardian.report.GuardianReportBuilder
import com.coldboar.coreguard.guardian.response.ResponsePlanFactory
import com.coldboar.coreguard.guardian.verify.InstallationVerifier

/**
 * Module-pattern façade for Guardian Intelligence features (Blueprint Phases 1–10).
 *
 * UI should call **this** object — not Room DAOs, Oracle internals, or baseline prefs —
 * so screens stay presentation-focused.
 */
object GuardianModule {

    fun explainChecks(context: Context): List<SecurityFinding> {
        val now = System.currentTimeMillis()
        return SecurityCheckRunner.run(context).map { result ->
            DeterministicOracleEngine.explain(
                RawSecuritySignal(
                    checkId = result.id,
                    displayName = result.displayName,
                    stateName = result.state.name,
                    explanation = result.explanation,
                    collectedAtEpochMillis = now
                )
            )
        }
    }

    fun resolvePulse(
        findings: List<SecurityFinding>,
        scanning: Boolean,
        dataAvailability: DataAvailability
    ): GuardianState =
        GuardianStateResolver.resolve(
            findings = findings,
            scanState = if (scanning) ScanState.RUNNING else ScanState.IDLE,
            dataAvailability = dataAvailability
        )

    fun correlate(findings: List<SecurityFinding>): List<CorrelatedFinding> =
        EvidenceConstellation.correlate(findings)

    fun bookOfChanges(context: Context): BookOfChangesRepository =
        BookOfChangesRepository.get(context)

    fun baseline(context: Context): DeviceBaselineStore =
        DeviceBaselineStore.get(context)

    fun wardCircle(context: Context): List<HardeningCheck> =
        WardCircleEvaluator.evaluate(context)

    fun responsePlan(finding: SecurityFinding): ResponsePlan =
        ResponsePlanFactory.forFinding(finding)

    fun verifyInstallation(context: Context): InstallationVerification =
        InstallationVerifier.verify(context)

    fun buildReport(
        findings: List<SecurityFinding>,
        state: GuardianState,
        includePackageNames: Boolean = false,
        includeDeviceModel: Boolean = false
    ): GuardianReport =
        GuardianReportBuilder.build(
            findings = findings,
            state = state,
            versionName = BuildConfig.VERSION_NAME,
            includePackageNames = includePackageNames,
            includeDeviceModel = includeDeviceModel
        )

    /**
     * Run checks → Oracle → constellation → append Book of Changes events for
     * newly active findings. Safe to call from a background dispatcher.
     */
    fun refreshIntelligence(context: Context): GuardianSnapshot {
        val findings = explainChecks(context)
        val correlations = correlate(findings)
        val state = resolvePulse(
            findings = findings,
            scanning = false,
            dataAvailability = if (findings.isEmpty()) DataAvailability.NONE else DataAvailability.COMPLETE
        )
        val repo = bookOfChanges(context)
        findings.filter { it.active }.forEach { finding ->
            repo.recordFinding(finding)
        }
        baseline(context).observeFromFindings(context, findings)
        return GuardianSnapshot(
            findings = findings,
            correlations = correlations,
            state = state,
            hardening = wardCircle(context),
            verification = verifyInstallation(context),
            baseline = baseline(context).current()
        )
    }
}

data class GuardianSnapshot(
    val findings: List<SecurityFinding>,
    val correlations: List<CorrelatedFinding>,
    val state: GuardianState,
    val hardening: List<HardeningCheck>,
    val verification: InstallationVerification,
    val baseline: DeviceBaseline?
)

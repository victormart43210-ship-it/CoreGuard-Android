package com.coldboar.coreguard

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.coldboar.coreguard.databinding.ActivityThreatScannerBinding
import com.coldboar.coreguard.mvt.Detection
import com.coldboar.coreguard.mvt.DeviceScanner
import com.coldboar.coreguard.mvt.IocFeedFetcher
import com.coldboar.coreguard.mvt.LastScan
import com.coldboar.coreguard.mvt.NemesisShield
import com.coldboar.coreguard.mvt.ScanReport
import com.coldboar.coreguard.mvt.ScanVerdict
import com.coldboar.coreguard.mvt.ShieldState
import com.coldboar.coreguard.mvt.ThreatSeverity
import com.google.android.material.card.MaterialCardView
import java.util.concurrent.Executors

/**
 * The Nemesis Scanner screen.
 *
 * Runs a [DeviceScanner] forensic scan against Amnesty MVT-style indicators and
 * lets the user toggle the [GuardVpnService] spyware-domain blocker.
 */
class ThreatScannerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityThreatScannerBinding
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    private val shieldListener = ShieldState.Listener { mainHandler.post { renderShield() } }

    // Launches the system VPN consent dialog; starts the shield if granted.
    private val vpnConsent = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            NemesisShield.start(this)
        } else {
            Toast.makeText(this, R.string.shield_consent_denied, Toast.LENGTH_SHORT).show()
            binding.switchShield.isChecked = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityThreatScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.scanner_title)

        binding.btnRunScan.setOnClickListener { runScan() }
        binding.btnRefreshSignatures.setOnClickListener { refreshSignatures() }
        binding.switchShield.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !ShieldState.isActive) enableShield()
            else if (!isChecked && ShieldState.isActive) NemesisShield.stop(this)
        }

        LastScan.report?.let { renderReport(it) }
    }

    override fun onResume() {
        super.onResume()
        ShieldState.addListener(shieldListener)
        renderShield()
    }

    override fun onPause() {
        super.onPause()
        ShieldState.removeListener(shieldListener)
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun enableShield() {
        val prepare = VpnService.prepare(this)
        if (prepare != null) vpnConsent.launch(prepare) else NemesisShield.start(this)
    }

    private fun renderShield() {
        binding.switchShield.isChecked = ShieldState.isActive
        binding.tvShieldStatus.text = if (ShieldState.isActive) {
            getString(R.string.shield_active, ShieldState.totalBlocked)
        } else {
            getString(R.string.shield_off)
        }
    }

    private fun refreshSignatures() {
        binding.btnRefreshSignatures.isEnabled = false
        binding.tvFeedStatus.visibility = android.view.View.VISIBLE
        binding.tvFeedStatus.text = getString(R.string.feed_refreshing)
        binding.tvFeedStatus.setTextColor(getColor(R.color.text_secondary))

        IocFeedFetcher.fetchAsync(
            context = this,
            executor = executor,
            onResult = { result ->
                mainHandler.post {
                    binding.btnRefreshSignatures.isEnabled = true
                    when (result) {
                        is IocFeedFetcher.FetchResult.Success -> {
                            binding.tvFeedStatus.text =
                                getString(R.string.feed_refresh_success, result.indicatorsLoaded)
                            binding.tvFeedStatus.setTextColor(getColor(R.color.ward_teal))
                        }
                        is IocFeedFetcher.FetchResult.Failure -> {
                            binding.tvFeedStatus.text =
                                getString(R.string.feed_refresh_failed, result.message)
                            binding.tvFeedStatus.setTextColor(getColor(R.color.status_warn))
                        }
                    }
                }
            }
        )
    }

    private fun runScan() {
        binding.btnRunScan.isEnabled = false
        binding.progressScan.visibility = android.view.View.VISIBLE
        executor.execute {
            val report = DeviceScanner.scan(this)
            LastScan.report = report
            mainHandler.post {
                binding.progressScan.visibility = android.view.View.GONE
                binding.btnRunScan.isEnabled = true
                renderReport(report)
            }
        }
    }

    private fun renderReport(report: ScanReport) {
        binding.tvVerdict.visibility = android.view.View.VISIBLE
        binding.tvSummary.visibility = android.view.View.VISIBLE

        val (label, color) = when (report.verdict) {
            ScanVerdict.CLEAN -> R.string.scanner_verdict_clean to R.color.status_pass
            ScanVerdict.SUSPICIOUS -> R.string.scanner_verdict_suspicious to R.color.status_warn
            ScanVerdict.INFECTED -> R.string.scanner_verdict_infected to R.color.status_fail
        }
        binding.tvVerdict.setText(label)
        binding.tvVerdict.setTextColor(getColor(color))
        binding.tvSummary.text = getString(
            R.string.scanner_summary,
            report.scannedArtifacts, report.indicatorCount, report.durationMillis
        )

        val container = binding.detectionsContainer
        container.removeAllViews()
        if (report.detections.isEmpty()) {
            binding.tvDetectionsHeader.visibility = android.view.View.GONE
            val tv = TextView(this).apply {
                text = getString(R.string.scanner_no_detections)
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            }
            container.addView(tv)
            return
        }

        binding.tvDetectionsHeader.visibility = android.view.View.VISIBLE
        val inflater = LayoutInflater.from(this)
        report.detections.sortedBy { it.severity.ordinal }.forEach { d ->
            container.addView(buildDetectionCard(inflater, container, d))
        }
    }

    private fun buildDetectionCard(
        inflater: LayoutInflater,
        parent: android.view.ViewGroup,
        d: Detection
    ): MaterialCardView {
        val card = inflater.inflate(R.layout.item_detection, parent, false) as MaterialCardView
        card.findViewById<TextView>(R.id.tvDetectionTitle).text = d.title
        card.findViewById<TextView>(R.id.tvDetectionDetail).text = d.detail

        val severityColor = when (d.severity) {
            ThreatSeverity.CRITICAL -> R.color.status_fail
            ThreatSeverity.HIGH -> R.color.status_warn
            ThreatSeverity.MEDIUM -> R.color.status_warn
        }
        card.setStrokeColor(getColor(severityColor))
        card.findViewById<TextView>(R.id.tvDetectionSeverity).apply {
            text = d.severity.name
            setBackgroundColor(getColor(severityColor))
        }

        val refView = card.findViewById<TextView>(R.id.tvDetectionRef)
        val ref = d.indicator.reference
        if (!ref.isNullOrBlank()) {
            refView.visibility = android.view.View.VISIBLE
            refView.text = ref
        }
        return card
    }
}

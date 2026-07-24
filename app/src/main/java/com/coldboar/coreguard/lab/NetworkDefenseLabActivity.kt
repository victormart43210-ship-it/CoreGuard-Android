package com.coldboar.coreguard.lab

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.coldboar.coreguard.R
import com.coldboar.coreguard.databinding.ActivityNetworkDefenseLabBinding

/**
 * Interactive Network Defense Lab — educational simulation only.
 * Not a live network monitor or intrusion-prevention product.
 */
class NetworkDefenseLabActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNetworkDefenseLabBinding
    private val engine = SimulationEngine()
    private var visionType = ColorVisionType.STANDARD
    private var showMst = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNetworkDefenseLabBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.lab_title)

        binding.topologyView.engine = engine
        binding.topologyView.palette = LabPalette.forType(visionType)
        binding.topologyView.onNodeSelected = { refreshHud() }

        binding.btnAttackBfs.setOnClickListener {
            engine.attack(AttackMode.BFS)
            refreshAll()
        }
        binding.btnAttackDfs.setOnClickListener {
            engine.attack(AttackMode.DFS)
            refreshAll()
        }
        binding.btnDefendDirect.setOnClickListener {
            engine.defend(DefenseMode.DIRECT)
            refreshAll()
        }
        binding.btnDefendCascade.setOnClickListener {
            engine.defend(DefenseMode.CASCADE)
            refreshAll()
        }
        binding.btnRollback.setOnClickListener {
            if (!engine.rollback()) {
                Toast.makeText(this, R.string.lab_rollback_empty, Toast.LENGTH_SHORT).show()
            }
            refreshAll()
        }
        binding.btnToggleMst.setOnClickListener {
            showMst = !showMst
            binding.topologyView.showMst = showMst
            refreshHud()
        }
        binding.btnToggleProtanopia.setOnClickListener {
            visionType = if (visionType == ColorVisionType.STANDARD) {
                ColorVisionType.PROTANOPIA
            } else {
                ColorVisionType.STANDARD
            }
            binding.topologyView.palette = LabPalette.forType(visionType)
            refreshHud()
        }

        refreshAll()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun refreshAll() {
        binding.topologyView.invalidate()
        refreshHud()
    }

    private fun refreshHud() {
        val status = engine.status()
        val m = status.metrics
        binding.tvLabMetrics.text = getString(
            R.string.lab_metrics_fmt,
            m.healthyCount,
            m.compromisedCount,
            m.defendedCount,
            m.stepsTaken
        )
        binding.tvSelectedNode.text = getString(
            R.string.lab_selected_fmt,
            engine.selectedNode(),
            visionType.name,
            if (showMst) "on" else "off"
        )
        val last = status.events.lastOrNull()
        binding.tvLabEvent.text = if (last == null) {
            getString(R.string.lab_events_empty)
        } else {
            getString(R.string.lab_event_fmt, last.kind, last.detail)
        }
    }
}

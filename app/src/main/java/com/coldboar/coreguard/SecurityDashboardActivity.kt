package com.coldboar.coreguard

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationSet
import android.view.animation.DecelerateInterpolator
import android.view.animation.TranslateAnimation
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.coldboar.coreguard.databinding.ActivitySecurityDashboardBinding
import com.google.android.material.card.MaterialCardView

/**
 * The Sanctum – security dashboard screen.
 *
 * Shows a PASS / WARN / FAIL card for each security check, revealed with a
 * staggered entrance animation. All checks are evaluated synchronously on the
 * main thread – each check is designed to be fast (no I/O) so this is
 * acceptable.
 */
class SecurityDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySecurityDashboardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySecurityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val results = SecurityCheckRunner.run(this)
        renderResults(results)
    }

    private fun renderResults(results: List<SecurityCheckResult>) {
        binding.checksContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)

        results.forEachIndexed { index, result ->
            val card = inflater.inflate(
                R.layout.item_security_check, binding.checksContainer, false
            ) as MaterialCardView

            val (stateLabel, stateColor, stateDim) = when (result.state) {
                SecurityCheckState.PASS -> Triple(
                    R.string.check_state_pass, getColor(R.color.status_pass), getColor(R.color.status_pass_dim)
                )
                SecurityCheckState.WARN -> Triple(
                    R.string.check_state_warn, getColor(R.color.status_warn), getColor(R.color.status_warn_dim)
                )
                SecurityCheckState.FAIL -> Triple(
                    R.string.check_state_fail, getColor(R.color.status_fail), getColor(R.color.status_fail_dim)
                )
            }

            card.findViewById<TextView>(R.id.tvCheckName).text = result.displayName
            card.findViewById<TextView>(R.id.tvCheckExplanation).text = result.explanation
            card.findViewById<ImageView>(R.id.ivCheckRune).imageTintList =
                ColorStateList.valueOf(stateColor)
            card.findViewById<TextView>(R.id.tvCheckState).apply {
                text = getString(stateLabel)
                setTextColor(stateColor)
                setBackgroundResource(R.drawable.bg_state_chip)
                backgroundTintList = ColorStateList.valueOf(stateDim)
            }

            binding.checksContainer.addView(card)
            card.startAnimation(staggeredEntrance(index))
        }

        val overallState = when {
            results.any { it.state == SecurityCheckState.FAIL } -> SecurityCheckState.FAIL
            results.any { it.state == SecurityCheckState.WARN } -> SecurityCheckState.WARN
            else -> SecurityCheckState.PASS
        }

        binding.tvOverallStatus.text = when (overallState) {
            SecurityCheckState.PASS -> getString(R.string.status_overall_pass)
            SecurityCheckState.WARN -> getString(R.string.status_overall_warn)
            SecurityCheckState.FAIL -> getString(R.string.status_overall_fail)
        }

        binding.tvOverallStatus.setTextColor(
            getColor(
                when (overallState) {
                    SecurityCheckState.PASS -> R.color.status_pass
                    SecurityCheckState.WARN -> R.color.status_warn
                    SecurityCheckState.FAIL -> R.color.status_fail
                }
            )
        )
    }

    /** Fade + slide entrance, staggered by card position. */
    private fun staggeredEntrance(index: Int) = AnimationSet(true).apply {
        interpolator = DecelerateInterpolator(1.5f)
        startOffset = 90L * index
        duration = 450
        addAnimation(AlphaAnimation(0f, 1f))
        addAnimation(TranslateAnimation(0f, 0f, 60f, 0f))
    }
}

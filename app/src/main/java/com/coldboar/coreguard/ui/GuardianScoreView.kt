package com.coldboar.coreguard.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.coldboar.coreguard.R
import kotlin.math.min

/**
 * Animated circular Guardian Score gauge.
 *
 * Draws a dark track ring, a glowing gradient arc that sweeps up to the score,
 * and the score number engraved in the centre. Call [setScore] to animate from
 * the current value to a new one.
 */
class GuardianScoreView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        letterSpacing = 0.28f
    }

    private val arcRect = RectF()

    private var displayedScore = 0f
    private var targetScore = 0
    private var animator: ValueAnimator? = null

    var scoreColor: Int = context.getColor(R.color.gold)
        private set

    init {
        context.theme.obtainStyledAttributes(attrs, R.styleable.GuardianScoreView, 0, 0).apply {
            try {
                trackPaint.color = getColor(
                    R.styleable.GuardianScoreView_trackColor,
                    context.getColor(R.color.pewter_card)
                )
                textPaint.color = getColor(
                    R.styleable.GuardianScoreView_scoreTextColor,
                    context.getColor(R.color.text_primary)
                )
            } finally {
                recycle()
            }
        }
        labelPaint.color = context.getColor(R.color.text_secondary)
        contentDescription = context.getString(R.string.cd_guardian_shield)
    }

    /** Sets the score (0–100) and the accent colour used for the arc + glow. */
    fun setScore(score: Int, color: Int, animate: Boolean = true) {
        targetScore = score.coerceIn(0, 100)
        scoreColor = color
        animator?.cancel()
        if (!animate) {
            displayedScore = targetScore.toFloat()
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(displayedScore, targetScore.toFloat()).apply {
            duration = 1400
            interpolator = DecelerateInterpolator(2f)
            addUpdateListener {
                displayedScore = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat()
        if (size <= 0f) return
        val cx = width / 2f
        val cy = height / 2f
        val stroke = size * 0.055f
        val radius = size / 2f - stroke * 1.8f

        trackPaint.strokeWidth = stroke
        arcPaint.strokeWidth = stroke
        glowPaint.strokeWidth = stroke * 2.2f
        glowPaint.maskFilter = BlurMaskFilter(stroke * 1.6f, BlurMaskFilter.Blur.NORMAL)

        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius)

        canvas.drawArc(arcRect, 0f, 360f, false, trackPaint)

        val sweep = 360f * (displayedScore / 100f)
        if (sweep > 0f) {
            val dimmed = (scoreColor and 0x00FFFFFF) or 0x55000000
            arcPaint.shader = SweepGradient(
                cx, cy,
                intArrayOf(dimmed, scoreColor, scoreColor),
                floatArrayOf(0f, 0.6f, 1f)
            )
            glowPaint.color = (scoreColor and 0x00FFFFFF) or 0x66000000

            canvas.save()
            canvas.rotate(-90f, cx, cy)
            canvas.drawArc(arcRect, 0f, sweep, false, glowPaint)
            canvas.drawArc(arcRect, 0f, sweep, false, arcPaint)
            canvas.restore()
        }

        textPaint.textSize = size * 0.30f
        labelPaint.textSize = size * 0.065f
        val textY = cy - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(displayedScore.toInt().toString(), cx, textY, textPaint)
        canvas.drawText(
            context.getString(R.string.guardian_score_title).uppercase(),
            cx,
            textY + size * 0.13f,
            labelPaint
        )
    }
}

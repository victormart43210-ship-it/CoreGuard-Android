package com.coldboar.coreguard.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.coldboar.coreguard.R
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Draws the Ægishjálmur (Helm of Awe) – the ancient Norse sigil of protection –
 * surrounded by a ring of rune-like tick glyphs.
 *
 * Rendered entirely with Canvas geometry (no bitmap assets) so it stays crisp
 * at any size. Intended as a low-alpha decorative background element; set
 * [sigilColor] / alpha via the `app:sigilColor` attribute or programmatically.
 *
 * The sigil is composed of 8 radial staves. Each stave carries three
 * perpendicular cross-bars and terminates in a trident fork – the shape of the
 * Algiz ᛉ rune, itself the elder-futhark rune of protection.
 */
class AegishjalmurView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    var sigilColor: Int = context.getColor(R.color.gold_sigil)
        set(value) {
            field = value
            invalidate()
        }

    /** When true the sigil slowly rotates (one revolution per [ROTATION_PERIOD_MS]). */
    var slowRotate: Boolean = false
        set(value) {
            field = value
            if (value) startRotation() else stopRotation()
        }

    private var rotationDeg = 0f
    private var rotationAnimator: ValueAnimator? = null

    init {
        context.theme.obtainStyledAttributes(attrs, R.styleable.AegishjalmurView, 0, 0).apply {
            try {
                sigilColor = getColor(R.styleable.AegishjalmurView_sigilColor, sigilColor)
                slowRotate = getBoolean(R.styleable.AegishjalmurView_slowRotate, false)
            } finally {
                recycle()
            }
        }
        contentDescription = context.getString(R.string.cd_sigil_background)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun startRotation() {
        if (rotationAnimator != null) return
        rotationAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = ROTATION_PERIOD_MS
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                rotationDeg = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopRotation() {
        rotationAnimator?.cancel()
        rotationAnimator = null
        rotationDeg = 0f
        invalidate()
    }

    override fun onDetachedFromWindow() {
        rotationAnimator?.cancel()
        rotationAnimator = null
        super.onDetachedFromWindow()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (slowRotate && rotationAnimator == null) startRotation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f * 0.96f
        if (radius <= 0f) return

        paint.color = sigilColor
        paint.strokeWidth = radius * 0.022f

        canvas.save()
        canvas.rotate(rotationDeg, cx, cy)

        drawHelmOfAwe(canvas, cx, cy, radius * 0.78f)
        drawRuneRing(canvas, cx, cy, radius)

        canvas.restore()
    }

    private fun drawHelmOfAwe(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        // Central eye: two concentric circles
        canvas.drawCircle(cx, cy, r * 0.10f, paint)
        canvas.drawCircle(cx, cy, r * 0.045f, paint)

        for (k in 0 until STAVE_COUNT) {
            val angle = Math.toRadians(k * 360.0 / STAVE_COUNT - 90.0)
            val dx = cos(angle).toFloat()
            val dy = sin(angle).toFloat()
            // perpendicular direction for cross-bars
            val px = -dy
            val py = dx

            // Main stave
            line(canvas, cx + dx * r * 0.12f, cy + dy * r * 0.12f, cx + dx * r, cy + dy * r)

            // Three cross-bars along the stave
            floatArrayOf(0.34f, 0.52f, 0.70f).forEachIndexed { i, t ->
                val barHalf = r * (0.10f - i * 0.012f)
                val bx = cx + dx * r * t
                val by = cy + dy * r * t
                line(canvas, bx - px * barHalf, by - py * barHalf, bx + px * barHalf, by + py * barHalf)
            }

            // Trident fork at the tip (Algiz rune shape)
            val forkBaseT = 0.82f
            val fx = cx + dx * r * forkBaseT
            val fy = cy + dy * r * forkBaseT
            val forkLen = r * 0.26f
            for (sign in intArrayOf(-1, 1)) {
                val fa = angle + sign * Math.toRadians(28.0)
                line(
                    canvas, fx, fy,
                    fx + cos(fa).toFloat() * forkLen,
                    fy + sin(fa).toFloat() * forkLen
                )
            }
        }
    }

    /**
     * Ring of deterministic rune-like glyphs. Each glyph is a vertical stroke
     * plus a diagonal branch whose direction varies with position, evoking an
     * inscribed rune band without depending on runic font glyph coverage.
     */
    private fun drawRuneRing(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        canvas.drawCircle(cx, cy, r * 0.90f, paint)
        canvas.drawCircle(cx, cy, r, paint)

        val glyphs = 24
        val inner = r * 0.915f
        val outer = r * 0.985f
        for (i in 0 until glyphs) {
            val angle = Math.toRadians(i * 360.0 / glyphs - 90.0 + 7.5)
            val dx = cos(angle).toFloat()
            val dy = sin(angle).toFloat()
            val px = -dy
            val py = dx
            val x1 = cx + dx * inner
            val y1 = cy + dy * inner
            val x2 = cx + dx * outer
            val y2 = cy + dy * outer
            line(canvas, x1, y1, x2, y2)
            // Branch pattern cycles through 3 shapes for variety
            val mid = 0.45f + (i % 3) * 0.15f
            val mx = x1 + (x2 - x1) * mid
            val my = y1 + (y2 - y1) * mid
            val branch = (outer - inner) * 0.6f
            when (i % 3) {
                0 -> line(canvas, mx, my, mx + (px + dx) * branch * 0.5f, my + (py + dy) * branch * 0.5f)
                1 -> line(canvas, mx, my, mx - (px - dx) * branch * 0.5f, my - (py - dy) * branch * 0.5f)
                else -> {
                    line(canvas, mx, my, mx + (px + dx) * branch * 0.4f, my + (py + dy) * branch * 0.4f)
                    line(canvas, mx, my, mx - (px - dx) * branch * 0.4f, my - (py - dy) * branch * 0.4f)
                }
            }
        }
    }

    private fun line(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float) {
        canvas.drawLine(x1, y1, x2, y2, paint)
    }

    private companion object {
        const val STAVE_COUNT = 8
        const val ROTATION_PERIOD_MS = 120_000L
    }
}

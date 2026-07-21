package com.coldboar.coreguard.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.coldboar.coreguard.R
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * A slowly-rotating occult seal rendered entirely with Canvas geometry (no
 * bitmap assets), used as a low-alpha decorative background element.
 *
 * Draws, from the outside in:
 *  - a double ring inscribed with Enochian-style glyph ticks (the letter band
 *    of John Dee's *Sigillum Dei Aemeth*),
 *  - a heptagram {7/3} – the seven-pointed "faery star" / Babalon star,
 *  - a heptagon connecting the star's points,
 *  - and a central unicursal hexagram, the emblem of Thelema.
 *
 * Set [sigilColor] / alpha via the `app:sigilColor` attribute or programmatically.
 */
class EnochianSealView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()

    var sigilColor: Int = context.getColor(R.color.gold_sigil)
        set(value) {
            field = value
            invalidate()
        }

    /** When true the seal slowly rotates (one revolution per [ROTATION_PERIOD_MS]). */
    var slowRotate: Boolean = false
        set(value) {
            field = value
            if (value) startRotation() else stopRotation()
        }

    private var rotationDeg = 0f
    private var rotationAnimator: ValueAnimator? = null

    init {
        context.theme.obtainStyledAttributes(attrs, R.styleable.EnochianSealView, 0, 0).apply {
            try {
                sigilColor = getColor(R.styleable.EnochianSealView_sigilColor, sigilColor)
                slowRotate = getBoolean(R.styleable.EnochianSealView_slowRotate, false)
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
                // Very slow rotation – only redraw on a visible angle change to
                // keep this decorative layer nearly free.
                val next = it.animatedValue as Float
                if (kotlin.math.abs(next - rotationDeg) >= 0.5f) {
                    rotationDeg = next
                    invalidate()
                }
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
        paint.strokeWidth = radius * 0.014f

        canvas.save()
        canvas.rotate(rotationDeg, cx, cy)

        drawLetterBand(canvas, cx, cy, radius)
        drawStarPolygon(canvas, cx, cy, radius * 0.80f, points = 7, step = 3)
        drawHeptagon(canvas, cx, cy, radius * 0.80f)
        drawUnicursalHexagram(canvas, cx, cy, radius * 0.34f)

        canvas.restore()
    }

    /** Double ring with radial glyph ticks evoking the Enochian letter band. */
    private fun drawLetterBand(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        canvas.drawCircle(cx, cy, r, paint)
        canvas.drawCircle(cx, cy, r * 0.90f, paint)

        val glyphs = 21
        val inner = r * 0.905f
        val outer = r * 0.995f
        for (i in 0 until glyphs) {
            val angle = Math.toRadians(i * 360.0 / glyphs - 90.0)
            val dx = cos(angle).toFloat()
            val dy = sin(angle).toFloat()
            val px = -dy
            val py = dx
            val x1 = cx + dx * inner
            val y1 = cy + dy * inner
            val x2 = cx + dx * outer
            val y2 = cy + dy * outer
            line(canvas, x1, y1, x2, y2)
            // Small serif crossbars – cycle through a few glyph shapes.
            val mx = (x1 + x2) / 2f
            val my = (y1 + y2) / 2f
            val bar = (outer - inner) * 0.55f
            when (i % 3) {
                0 -> line(canvas, mx - px * bar, my - py * bar, mx + px * bar, my + py * bar)
                1 -> line(canvas, mx, my, mx + (px + dx) * bar, my + (py + dy) * bar)
                else -> line(canvas, x1, y1, x1 + px * bar, y1 + py * bar)
            }
        }
    }

    /** Draws a {points/step} star polygon (e.g. 7/3 heptagram). */
    private fun drawStarPolygon(canvas: Canvas, cx: Float, cy: Float, r: Float, points: Int, step: Int) {
        path.reset()
        for (i in 0..points) {
            val idx = (i * step) % points
            val angle = Math.toRadians(idx * 360.0 / points - 90.0)
            val x = cx + cos(angle).toFloat() * r
            val y = cy + sin(angle).toFloat() * r
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawHeptagon(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        path.reset()
        for (i in 0..7) {
            val angle = Math.toRadians(i * 360.0 / 7 - 90.0)
            val x = cx + cos(angle).toFloat() * r
            val y = cy + sin(angle).toFloat() * r
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    /**
     * Central unicursal hexagram – a hexagram traced as one continuous line, so
     * consecutive vertices are separated by two steps around six points.
     */
    private fun drawUnicursalHexagram(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        // Two interleaved triangles offset to form the classic unicursal path.
        val order = intArrayOf(0, 3, 1, 4, 2, 5)
        path.reset()
        order.forEachIndexed { i, v ->
            val angle = Math.toRadians(v * 60.0 - 90.0)
            // Alternate radius slightly to get the elongated unicursal look.
            val rr = if (v % 2 == 0) r else r * 0.86f
            val x = cx + cos(angle).toFloat() * rr
            val y = cy + sin(angle).toFloat() * rr
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, paint)
        canvas.drawCircle(cx, cy, r * 0.16f, paint)
    }

    private fun line(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float) {
        canvas.drawLine(x1, y1, x2, y2, paint)
    }

    private companion object {
        const val ROTATION_PERIOD_MS = 150_000L
    }
}

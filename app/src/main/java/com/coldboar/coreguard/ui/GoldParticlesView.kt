package com.coldboar.coreguard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import com.coldboar.coreguard.R
import kotlin.random.Random

/**
 * A field of slowly drifting, twinkling gold particles rendered on a Canvas.
 *
 * Purely decorative: particles rise gently, drift sideways on a slow sine sway,
 * and pulse in brightness so the background reads as "alive". Driven by the
 * [Choreographer] and paused automatically while detached from the window.
 */
class GoldParticlesView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private data class Particle(
        var x: Float,
        var y: Float,
        var radius: Float,
        var speedY: Float,
        var swayAmp: Float,
        var swayFreq: Float,
        var phase: Float,
        var baseAlpha: Float,
        var twinkleFreq: Float
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val particleColor = context.getColor(R.color.gold_particle)

    private val random = Random(System.nanoTime())
    private val particles = ArrayList<Particle>()
    private var lastFrameNanos = 0L
    private var elapsedSec = 0f
    private var running = false

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            val dt = if (lastFrameNanos == 0L) 0f else (frameTimeNanos - lastFrameNanos) / 1_000_000_000f
            lastFrameNanos = frameTimeNanos
            elapsedSec += dt
            step(dt.coerceAtMost(0.05f))
            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        seedParticles(w, h)
    }

    private fun seedParticles(w: Int, h: Int) {
        particles.clear()
        if (w == 0 || h == 0) return
        val count = (w * h / 26_000).coerceIn(24, 60)
        repeat(count) { particles.add(newParticle(w, h, spawnAnywhere = true)) }
    }

    private fun newParticle(w: Int, h: Int, spawnAnywhere: Boolean): Particle {
        val density = resources.displayMetrics.density
        return Particle(
            x = random.nextFloat() * w,
            y = if (spawnAnywhere) random.nextFloat() * h else h + random.nextFloat() * 40f,
            radius = (1.1f + random.nextFloat() * 2.4f) * density,
            speedY = (10f + random.nextFloat() * 26f) * density,
            swayAmp = (4f + random.nextFloat() * 14f) * density,
            swayFreq = 0.15f + random.nextFloat() * 0.5f,
            phase = random.nextFloat() * (2f * Math.PI.toFloat()),
            baseAlpha = 0.25f + random.nextFloat() * 0.55f,
            twinkleFreq = 0.4f + random.nextFloat() * 1.4f
        )
    }

    private fun step(dt: Float) {
        val w = width
        val h = height
        for (p in particles) {
            p.y -= p.speedY * dt
            if (p.y + p.radius < 0f) {
                // Recycle from the bottom with fresh randomised traits.
                val np = newParticle(w, h, spawnAnywhere = false)
                p.x = np.x; p.y = np.y; p.radius = np.radius; p.speedY = np.speedY
                p.swayAmp = np.swayAmp; p.swayFreq = np.swayFreq; p.phase = np.phase
                p.baseAlpha = np.baseAlpha; p.twinkleFreq = np.twinkleFreq
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (p in particles) {
            val sway = p.swayAmp * kotlin.math.sin(elapsedSec * p.swayFreq * 2f * Math.PI.toFloat() + p.phase)
            val cx = p.x + sway
            val twinkle = 0.55f + 0.45f * kotlin.math.sin(elapsedSec * p.twinkleFreq * 2f * Math.PI.toFloat() + p.phase)
            val alpha = (p.baseAlpha * twinkle).coerceIn(0f, 1f)

            glowPaint.color = particleColor
            glowPaint.alpha = (alpha * 60).toInt()
            canvas.drawCircle(cx, p.y, p.radius * 2.6f, glowPaint)

            paint.color = particleColor
            paint.alpha = (alpha * 255).toInt()
            canvas.drawCircle(cx, p.y, p.radius, paint)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        running = true
        lastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    override fun onDetachedFromWindow() {
        running = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        super.onDetachedFromWindow()
    }
}

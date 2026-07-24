package com.coldboar.coreguard.lab

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Interactive 16-node topology canvas for the Network Defense Lab.
 * Shape + color encode node state (accessibility).
 */
class TopologyView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var engine: SimulationEngine? = null
        set(value) {
            field = value
            invalidate()
        }

    var palette: LabPalette = LabPalette.forType(ColorVisionType.STANDARD)
        set(value) {
            field = value
            invalidate()
        }

    var showMst: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var onNodeSelected: ((String) -> Unit)? = null

    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val mstPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF111111.toInt()
        textAlign = Paint.Align.CENTER
        textSize = 28f
        isFakeBoldText = true
    }

    private val nodeCenters = mutableMapOf<String, Pair<Float, Float>>()
    private val nodeRadius = 36f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(palette.background)
        layoutNodes()
        val eng = engine ?: return
        val status = eng.status()

        edgePaint.color = palette.edge
        for (edge in LabGraph.edges) {
            val a = nodeCenters[edge.from] ?: continue
            val b = nodeCenters[edge.to] ?: continue
            canvas.drawLine(a.first, a.second, b.first, b.second, edgePaint)
        }

        if (showMst) {
            mstPaint.color = palette.mstEdge
            for (edge in eng.mstEdges()) {
                val a = nodeCenters[edge.from] ?: continue
                val b = nodeCenters[edge.to] ?: continue
                canvas.drawLine(a.first, a.second, b.first, b.second, mstPaint)
            }
        }

        for (node in LabGraph.nodes) {
            val center = nodeCenters[node] ?: continue
            val state = status.states[node] ?: NodeState.HEALTHY
            fillPaint.color = palette.colorFor(state)
            drawShape(canvas, center.first, center.second, palette.shapeFor(state))
            if (node == eng.selectedNode()) {
                strokePaint.color = palette.selectedStroke
                canvas.drawCircle(center.first, center.second, nodeRadius + 8f, strokePaint)
            }
            canvas.drawText(node, center.first, center.second + textPaint.textSize / 3f, textPaint)
        }
    }

    private fun layoutNodes() {
        nodeCenters.clear()
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) * 0.38f
        LabGraph.nodes.forEachIndexed { index, id ->
            val angle = (2.0 * Math.PI * index / LabGraph.NODE_COUNT) - Math.PI / 2
            val x = cx + (radius * cos(angle)).toFloat()
            val y = cy + (radius * sin(angle)).toFloat()
            nodeCenters[id] = x to y
        }
    }

    private fun drawShape(canvas: Canvas, x: Float, y: Float, shape: NodeShape) {
        val r = nodeRadius
        when (shape) {
            NodeShape.CIRCLE -> canvas.drawCircle(x, y, r, fillPaint)
            NodeShape.SQUARE -> canvas.drawRect(x - r, y - r, x + r, y + r, fillPaint)
            NodeShape.DIAMOND -> {
                val path = Path()
                path.moveTo(x, y - r)
                path.lineTo(x + r, y)
                path.lineTo(x, y + r)
                path.lineTo(x - r, y)
                path.close()
                canvas.drawPath(path, fillPaint)
            }
            NodeShape.TRIANGLE -> {
                val path = Path()
                path.moveTo(x, y - r)
                path.lineTo(x + r, y + r)
                path.lineTo(x - r, y + r)
                path.close()
                canvas.drawPath(path, fillPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
        val hit = nodeCenters.entries.minByOrNull { (_, c) ->
            hypot(event.x - c.first, event.y - c.second)
        } ?: return true
        val dist = hypot(event.x - hit.value.first, event.y - hit.value.second)
        if (dist <= nodeRadius * 1.6f) {
            engine?.selectNode(hit.key)
            onNodeSelected?.invoke(hit.key)
            invalidate()
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}

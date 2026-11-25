package com.example.canvas.core

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path

/**
 * Handles rendering strokes onto an Android Canvas with smooth Bézier curves
 * and multi-style studio brushes (solid ink/marker and feathered soft airbrush).
 */
class StrokeRenderer {

    private val strokePaint = Paint().apply {
        isAntiAlias = true
        isDither = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val pointPaint = Paint().apply {
        isAntiAlias = true
        isDither = true
        style = Paint.Style.FILL
    }

    /**
     * Renders a [Stroke] onto the specified [Canvas] (used for committing to backing bitmap).
     */
    fun renderStroke(canvas: Canvas, stroke: Stroke) {
        val points = stroke.points
        if (points.isEmpty()) return

        if (points.size == 1) {
            renderPoint(canvas, points[0], stroke)
            return
        }

        val path = buildSmoothPath(points)
        drawStyledPath(canvas, path, stroke)
    }

    /**
     * Renders the stroke being drawn exactly as it will be committed (what you see is what is saved).
     */
    fun renderLiveStroke(canvas: Canvas, stroke: Stroke, @Suppress("UNUSED_PARAMETER") unused: Int = 0) {
        val points = stroke.points
        if (points.isEmpty()) return

        if (points.size == 1) {
            renderPoint(canvas, points[0], stroke)
            return
        }
        drawStyledPath(canvas, buildLivePath(stroke), stroke)
    }

    private fun renderPoint(canvas: Canvas, pt: StrokePoint, stroke: Stroke) {
        when (stroke.style) {
            BrushStyle.SOLID -> {
                pointPaint.color = stroke.color
                canvas.drawCircle(pt.x, pt.y, stroke.width / 2f, pointPaint)
            }
            BrushStyle.SOFT -> {
                // Concentric feathered circles strictly inside stroke.width / 2f
                val baseAlpha = Color.alpha(stroke.color)
                val r = Color.red(stroke.color)
                val g = Color.green(stroke.color)
                val b = Color.blue(stroke.color)
                val layers = floatArrayOf(1.0f, 0.72f, 0.44f, 0.22f)
                val alphas = floatArrayOf(0.16f, 0.22f, 0.30f, 0.42f)
                for (i in layers.indices) {
                    pointPaint.color = Color.argb((baseAlpha * alphas[i]).toInt().coerceIn(1, 255), r, g, b)
                    canvas.drawCircle(pt.x, pt.y, (stroke.width / 2f) * layers[i], pointPaint)
                }
            }
        }
    }

    private fun drawStyledPath(canvas: Canvas, path: Path, stroke: Stroke) {
        when (stroke.style) {
            BrushStyle.SOLID -> {
                strokePaint.color = stroke.color
                strokePaint.strokeWidth = stroke.width
                canvas.drawPath(path, strokePaint)
            }
            BrushStyle.SOFT -> {
                // Multi-pass soft brush strictly bounded by stroke.width so dirty tiles are 100% lossless
                val baseAlpha = Color.alpha(stroke.color)
                val r = Color.red(stroke.color)
                val g = Color.green(stroke.color)
                val b = Color.blue(stroke.color)
                val widthFracs = floatArrayOf(1.0f, 0.72f, 0.46f, 0.24f)
                val alphaFracs = floatArrayOf(0.14f, 0.20f, 0.28f, 0.40f)
                for (i in widthFracs.indices) {
                    strokePaint.color = Color.argb((baseAlpha * alphaFracs[i]).toInt().coerceIn(1, 255), r, g, b)
                    strokePaint.strokeWidth = (stroke.width * widthFracs[i]).coerceAtLeast(1f)
                    canvas.drawPath(path, strokePaint)
                }
            }
        }
    }

    // The in-flight stroke only ever grows by appending points, so its smooth path is extended, not rebuilt:
    // O(new points) per frame instead of O(all points) (O(n^2) over a stroke) and no per-frame Path garbage.
    private var liveRef: Stroke? = null
    private var liveFolded = 0
    private var livePrevX = 0f
    private var livePrevY = 0f
    private val committedLivePath = Path()
    private val liveFramePath = Path()

    private fun buildLivePath(stroke: Stroke): Path {
        val points = stroke.points
        if (stroke !== liveRef || points.size < liveFolded) {
            committedLivePath.reset()
            liveRef = stroke
            liveFolded = 0
        }
        if (liveFolded == 0) {
            val first = points[0]
            committedLivePath.moveTo(first.x, first.y)
            livePrevX = first.x
            livePrevY = first.y
            liveFolded = 1
        }
        for (i in liveFolded until points.size) {
            val curr = points[i]
            committedLivePath.quadTo(livePrevX, livePrevY, (livePrevX + curr.x) / 2f, (livePrevY + curr.y) / 2f)
            livePrevX = curr.x
            livePrevY = curr.y
        }
        liveFolded = points.size
        // native copy of the folded part + the open tail to the newest point
        liveFramePath.set(committedLivePath)
        liveFramePath.lineTo(livePrevX, livePrevY)
        return liveFramePath
    }

    private fun buildSmoothPath(points: List<StrokePoint>): Path {
        val path = Path()
        val first = points[0]
        path.moveTo(first.x, first.y)

        var prevX = first.x
        var prevY = first.y

        for (i in 1 until points.size) {
            val curr = points[i]
            val midX = (prevX + curr.x) / 2f
            val midY = (prevY + curr.y) / 2f
            path.quadTo(prevX, prevY, midX, midY)
            prevX = curr.x
            prevY = curr.y
        }
        path.lineTo(prevX, prevY)
        return path
    }
}

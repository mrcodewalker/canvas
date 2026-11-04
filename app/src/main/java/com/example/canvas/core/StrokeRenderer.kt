package com.example.canvas.core

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path

/**
 * Handles rendering strokes onto an Android Canvas with smooth Bézier curves.
 */
class StrokeRenderer {

    private val strokePaint = Paint().apply {
        isAntiAlias = true
        isDither = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val liveAuraPaint = Paint().apply {
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

    private val cursorRingPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
    }

    /**
     * Renders a [Stroke] onto the specified [Canvas] (used for committing to backing bitmap).
     */
    fun renderStroke(canvas: Canvas, stroke: Stroke) {
        val points = stroke.points
        if (points.isEmpty()) return

        if (points.size == 1) {
            val pt = points[0]
            pointPaint.color = stroke.color
            canvas.drawCircle(pt.x, pt.y, stroke.width / 2f, pointPaint)
            return
        }

        strokePaint.color = stroke.color
        strokePaint.strokeWidth = stroke.width

        val path = buildSmoothPath(points)
        canvas.drawPath(path, strokePaint)
    }

    /**
     * Renders the active in-flight stroke on the live view canvas with a soft luminous ink aura
     * and a precision brush-tip ring for a flagship studio feel.
     */
    fun renderLiveStroke(canvas: Canvas, stroke: Stroke, accentGlowColor: Int) {
        val points = stroke.points
        if (points.isEmpty()) return

        val lastPt = points.last()

        if (points.size == 1) {
            liveAuraPaint.color = accentGlowColor
            liveAuraPaint.alpha = 55
            pointPaint.color = stroke.color
            canvas.drawCircle(lastPt.x, lastPt.y, stroke.width * 0.85f, liveAuraPaint)
            canvas.drawCircle(lastPt.x, lastPt.y, stroke.width / 2f, pointPaint)
        } else {
            val path = buildSmoothPath(points)
            // 1. Soft outer luminous halo
            liveAuraPaint.color = accentGlowColor
            liveAuraPaint.alpha = 50
            liveAuraPaint.strokeWidth = stroke.width + 14f
            canvas.drawPath(path, liveAuraPaint)

            // 2. Crisp core stroke
            strokePaint.color = stroke.color
            strokePaint.strokeWidth = stroke.width
            canvas.drawPath(path, strokePaint)
        }

        // 3. Precision brush tip ring + center dot
        val ringRadius = (stroke.width / 2f + 10f).coerceAtLeast(18f)
        cursorRingPaint.color = accentGlowColor
        cursorRingPaint.alpha = 210
        canvas.drawCircle(lastPt.x, lastPt.y, ringRadius, cursorRingPaint)
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

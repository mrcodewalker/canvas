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
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val pointPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    /**
     * Renders a [Stroke] onto the specified [Canvas].
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

        canvas.drawPath(path, strokePaint)
    }
}

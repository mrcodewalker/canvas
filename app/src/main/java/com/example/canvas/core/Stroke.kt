package com.example.canvas.core

import android.graphics.Rect
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Represents a single point in a drawing stroke.
 */
data class StrokePoint(
    val x: Float,
    val y: Float
)

/**
 * Represents a continuous stroke drawn by the user.
 *
 * @param color ARGB color int.
 * @param width Stroke width in canvas pixels.
 * @param points Ordered list of points along the stroke.
 */
data class Stroke(
    val color: Int,
    val width: Float,
    val points: MutableList<StrokePoint> = mutableListOf()
) {
    fun addPoint(x: Float, y: Float) {
        points.add(StrokePoint(x, y))
    }

    /**
     * Computes the bounding rectangle enclosing this stroke.
     * Expands by half the stroke width + 2 pixels, and clips to [0, 0, canvasWidth, canvasHeight].
     */
    fun computeBounds(canvasWidth: Int, canvasHeight: Int): Rect {
        if (points.isEmpty()) {
            return Rect(0, 0, 0, 0)
        }

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        for (pt in points) {
            if (pt.x < minX) minX = pt.x
            if (pt.y < minY) minY = pt.y
            if (pt.x > maxX) maxX = pt.x
            if (pt.y > maxY) maxY = pt.y
        }

        val padding = (width / 2f) + 2f

        val left = max(0, floor(minX - padding).toInt())
        val top = max(0, floor(minY - padding).toInt())
        val right = min(canvasWidth, ceil(maxX + padding).toInt())
        val bottom = min(canvasHeight, ceil(maxY + padding).toInt())

        val rect = Rect()
        rect.left = left
        rect.top = top
        rect.right = right
        rect.bottom = bottom
        return rect
    }
}


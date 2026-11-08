package com.example.canvas.core

import android.graphics.Rect
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

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
    fun computeBounds(canvasWidth: Int, canvasHeight: Int): Rect = toRect(boundsInts(canvasWidth, canvasHeight))

    /** Pure-int version of [computeBounds]: [left, top, right, bottom]. */
    fun boundsInts(canvasWidth: Int, canvasHeight: Int): IntArray {
        if (points.isEmpty()) return intArrayOf(0, 0, 0, 0)

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        for (pt in points) {
            if (pt.x < minX) minX = pt.x
            if (pt.y < minY) minY = pt.y
            if (pt.x > maxX) maxX = pt.x
            if (pt.y > maxY) maxY = pt.y
        }

        val padding = (width / 2f) + 2f
        return intArrayOf(
            max(0, floor(minX - padding).toInt()),
            max(0, floor(minY - padding).toInt()),
            min(canvasWidth, ceil(maxX + padding).toInt()),
            min(canvasHeight, ceil(maxY + padding).toInt())
        )
    }

    /**
     * Tile-based dirty region: instead of one big bounding box, returns only the pieces of the
     * bounding box that the stroke can actually touch. Everything is chosen from this stroke and from
     * measurements, nothing is a preset:
     *  - tile size: every power of two between the brush width and the stroke size is tried, and the
     *    cheapest wins, where cost = bytes saved + [patchOverheadBytes] for every extra patch
     *    (the fixed cost of one more read/write, measured by the history; 4 KiB = one memory page
     *    until something was measured). The single bounding box is one of the candidates.
     *  - sampling step along the stroke: half a tile, which bounds the over-coverage by a quarter tile.
     */
    fun computeDirtyRegions(
        canvasWidth: Int,
        canvasHeight: Int,
        patchOverheadBytes: Long = PAGE_BYTES,
        tileSize: Int? = null
    ): List<Rect> = dirtyRegionInts(canvasWidth, canvasHeight, patchOverheadBytes, tileSize).map { toRect(it) }

    /** Pure-int version of [computeDirtyRegions]: list of [left, top, right, bottom]. */
    fun dirtyRegionInts(
        canvasWidth: Int,
        canvasHeight: Int,
        patchOverheadBytes: Long = PAGE_BYTES,
        tileSize: Int? = null
    ): List<IntArray> {
        val bounds = boundsInts(canvasWidth, canvasHeight)
        val bw = bounds[2] - bounds[0]
        val bh = bounds[3] - bounds[1]
        if (bw <= 0 || bh <= 0) return listOf(bounds)

        var best: List<IntArray> = listOf(bounds)
        var bestCost = bw.toLong() * bh * 4L + patchOverheadBytes

        val sizes: List<Int> = if (tileSize != null) {
            listOf(tileSize)
        } else {
            val smallest = nextPow2(max(1, ceil(width).toInt()))
            val largest = nextPow2(max(bw, bh))
            generateSequence(smallest) { it * 2 }.takeWhile { it <= largest }.toList()
        }
        for (t in sizes) {
            val regions = regionsForTile(bounds, canvasWidth, canvasHeight, t)
            if (regions.isEmpty()) continue
            var area = 0L
            for (r in regions) area += (r[2] - r[0]).toLong() * (r[3] - r[1])
            val cost = area * 4L + patchOverheadBytes * regions.size
            if (cost < bestCost) {
                bestCost = cost
                best = regions
            }
        }
        return best
    }

    private fun nextPow2(v: Int): Int {
        var p = 1
        while (p < v) p = p shl 1
        return p
    }

    private fun regionsForTile(bounds: IntArray, canvasWidth: Int, canvasHeight: Int, tileSize: Int): List<IntArray> {
        val cols = (canvasWidth + tileSize - 1) / tileSize
        val rows = (canvasHeight + tileSize - 1) / tileSize
        val touched = BooleanArray(cols * rows)

        // Samples every tileSize/2 px; a square of half-side r + step/2 around each sample covers
        // the whole capsule (segment swept by the round brush).
        val step = max(1f, tileSize / 2f)
        val reach = (width / 2f) + 2f + step / 2f

        fun mark(cx: Float, cy: Float) {
            val c0 = max(0, floor((cx - reach) / tileSize).toInt())
            val c1 = min(cols - 1, floor((cx + reach) / tileSize).toInt())
            val r0 = max(0, floor((cy - reach) / tileSize).toInt())
            val r1 = min(rows - 1, floor((cy + reach) / tileSize).toInt())
            for (r in r0..r1) for (c in c0..c1) touched[r * cols + c] = true
        }

        if (points.size == 1) {
            mark(points[0].x, points[0].y)
        } else {
            for (i in 1 until points.size) {
                val a = points[i - 1]
                val b = points[i]
                val dx = b.x - a.x
                val dy = b.y - a.y
                val len = sqrt(dx * dx + dy * dy)
                val n = max(1, ceil(len / step).toInt())
                for (k in 0..n) {
                    val t = k.toFloat() / n
                    mark(a.x + dx * t, a.y + dy * t)
                }
            }
        }

        val result = ArrayList<IntArray>()
        for (r in 0 until rows) {
            var c = 0
            while (c < cols) {
                if (!touched[r * cols + c]) { c++; continue }
                val start = c
                while (c < cols && touched[r * cols + c]) c++
                // clip the tile run to the stroke bounding box
                val l = max(start * tileSize, bounds[0])
                val t = max(r * tileSize, bounds[1])
                val rt = min(c * tileSize, bounds[2])
                val bt = min((r + 1) * tileSize, bounds[3])
                if (rt > l && bt > t) result.add(intArrayOf(l, t, rt, bt))
            }
        }
        return result
    }

    private fun toRect(a: IntArray): Rect {
        val rect = Rect()
        rect.left = a[0]
        rect.top = a[1]
        rect.right = a[2]
        rect.bottom = a[3]
        return rect
    }

    companion object {
        /** One memory page: the cost of an extra patch before anything has been measured. */
        const val PAGE_BYTES = 4096L
    }
}

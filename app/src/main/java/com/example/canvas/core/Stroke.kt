package com.example.canvas.core

import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Brush rendering style stored with each [Stroke] so both live drawing and merged redo replay
 * produce identical pixels.
 */
enum class BrushStyle(val code: Int) {
    SOLID(0),
    SOFT(1);

    companion object {
        fun fromCode(code: Int): BrushStyle = entries.firstOrNull { it.code == code } ?: SOLID
    }
}

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
 * @param style Optional brush rendering style (defaults to [BrushStyle.SOLID]).
 */
data class Stroke(
    val color: Int,
    val width: Float,
    val points: MutableList<StrokePoint> = mutableListOf(),
    val style: BrushStyle = BrushStyle.SOLID
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
     * Adaptive exact-capsule dirty-region decomposition:
     *  1. Evaluates power-of-two tile sizes all the way down to [MIN_TILE_SIZE] (8 px) even for thick
     *     brushes (`nét to`), so wide strokes and round caps are sliced finely along their curved/angled
     *     profile instead of being locked to a coarse `>= width` grid.
     *  2. Tests exact Euclidean capsule-to-tile intersection (eliminating sample-step and square-corner
     *     bloat on diagonal strokes).
     *  3. Tight-clips every horizontal or vertical tile run to the exact capsule cross-section inside
     *     that band (following diagonal slopes and circular brush caps to the pixel).
     *  4. Runs a cost-optimal adjacent strip coalesce pass that merges neighboring strips whenever the
     *     extra corner pixels cost less than [patchOverheadBytes], keeping straight middle sections compact
     *     while preserving fine staircase subdivisions along diagonals and thick curves.
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
        if (bw <= 0 || bh <= 0 || points.isEmpty()) return listOf(bounds)

        var best: List<IntArray> = listOf(bounds)
        var bestCost = bw.toLong() * bh * 4L + patchOverheadBytes

        val sizes: List<Int> = if (tileSize != null) {
            listOf(max(1, tileSize))
        } else {
            val largest = nextPow2(max(bw, bh))
            generateSequence(MIN_TILE_SIZE) { it * 2 }.takeWhile { it <= largest }.toList()
        }

        for (t in sizes) {
            val (rowRegions, colRegions) = regionsForTileBothOrientations(bounds, t, patchOverheadBytes)
            for (candidate in listOf(rowRegions, colRegions)) {
                if (candidate.isEmpty()) continue
                val cost = costOf(candidate, patchOverheadBytes)
                if (cost < bestCost) {
                    bestCost = cost
                    best = candidate
                }
            }
        }
        return best
    }

    private fun costOf(regions: List<IntArray>, patchOverheadBytes: Long): Long {
        var area = 0L
        for (r in regions) area += (r[2] - r[0]).toLong() * (r[3] - r[1])
        return area * 4L + patchOverheadBytes * regions.size
    }

    private fun nextPow2(v: Int): Int {
        var p = 1
        while (p < v) p = p shl 1
        return p
    }

    private fun regionsForTileBothOrientations(
        bounds: IntArray,
        tileSize: Int,
        patchOverheadBytes: Long
    ): Pair<List<IntArray>, List<IntArray>> {
        val ox = bounds[0]
        val oy = bounds[1]
        val bw = bounds[2] - ox
        val bh = bounds[3] - oy
        val cols = (bw + tileSize - 1) / tileSize
        val rows = (bh + tileSize - 1) / tileSize
        if (cols <= 0 || rows <= 0) return emptyList<IntArray>() to emptyList()
        // Guard against pathological tiny tiles on full-canvas strokes
        if (cols.toLong() * rows > MAX_GRID_CELLS) return emptyList<IntArray>() to emptyList()

        val touched = BooleanArray(cols * rows)
        val radius = (width / 2f) + 2f
        val rSq = radius * radius

        fun markSegment(ax: Float, ay: Float, bx: Float, by: Float) {
            val minX = min(ax, bx) - radius
            val maxX = max(ax, bx) + radius
            val minY = min(ay, by) - radius
            val maxY = max(ay, by) + radius
            val c0 = ((floor(minX).toInt() - ox) / tileSize).coerceIn(0, cols - 1)
            val c1 = ((ceil(maxX).toInt() - ox) / tileSize).coerceIn(0, cols - 1)
            val r0 = ((floor(minY).toInt() - oy) / tileSize).coerceIn(0, rows - 1)
            val r1 = ((ceil(maxY).toInt() - oy) / tileSize).coerceIn(0, rows - 1)
            for (r in r0..r1) {
                val ty0 = (oy + r * tileSize).toFloat()
                val ty1 = min(bounds[3], oy + (r + 1) * tileSize).toFloat()
                val rowOff = r * cols
                for (c in c0..c1) {
                    if (touched[rowOff + c]) continue
                    val tx0 = (ox + c * tileSize).toFloat()
                    val tx1 = min(bounds[2], ox + (c + 1) * tileSize).toFloat()
                    if (segmentToRectDistSq(ax, ay, bx, by, tx0, ty0, tx1, ty1) <= rSq + 1e-3f) {
                        touched[rowOff + c] = true
                    }
                }
            }
        }

        if (points.size == 1) {
            markSegment(points[0].x, points[0].y, points[0].x, points[0].y)
        } else {
            for (i in 1 until points.size) {
                val a = points[i - 1]
                val b = points[i]
                markSegment(a.x, a.y, b.x, b.y)
            }
        }

        // 1. Horizontal runs (row slices), each tight-clipped to exact capsule bounds inside the cell run
        val rowRuns = ArrayList<IntArray>()
        for (r in 0 until rows) {
            val wy0 = oy + r * tileSize
            val wy1 = min(bounds[3], oy + (r + 1) * tileSize)
            var c = 0
            while (c < cols) {
                if (!touched[r * cols + c]) {
                    c++
                    continue
                }
                val start = c
                while (c < cols && touched[r * cols + c]) c++
                val wx0 = ox + start * tileSize
                val wx1 = min(bounds[2], ox + c * tileSize)
                tightClipWindow(wx0, wy0, wx1, wy1, radius)?.let { rowRuns.add(it) }
            }
        }

        // 2. Vertical runs (column slices), ideal for shallow diagonals and wide horizontal curves
        val colRuns = ArrayList<IntArray>()
        for (c in 0 until cols) {
            val wx0 = ox + c * tileSize
            val wx1 = min(bounds[2], ox + (c + 1) * tileSize)
            var r = 0
            while (r < rows) {
                if (!touched[r * cols + c]) {
                    r++
                    continue
                }
                val start = r
                while (r < rows && touched[r * cols + c]) r++
                val wy0 = oy + start * tileSize
                val wy1 = min(bounds[3], oy + r * tileSize)
                tightClipWindow(wx0, wy0, wx1, wy1, radius)?.let { colRuns.add(it) }
            }
        }

        return coalesceStrips(rowRuns, patchOverheadBytes) to coalesceStrips(colRuns, patchOverheadBytes)
    }

    /**
     * Computes the tightest integer sub-rectangle inside `[wx0, wy0, wx1, wy1]` enclosing the stroke's
     * radius-[r] capsule intersection with that window.
     */
    private fun tightClipWindow(wx0: Int, wy0: Int, wx1: Int, wy1: Int, r: Float): IntArray? {
        if (wx1 <= wx0 || wy1 <= wy0) return null
        val fx0 = wx0.toFloat()
        val fy0 = wy0.toFloat()
        val fx1 = wx1.toFloat()
        val fy1 = wy1.toFloat()
        val rSq = r * r

        var minX = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY

        fun accumulateSegment(ax: Float, ay: Float, bx: Float, by: Float) {
            if (max(ax, bx) + r < fx0 || min(ax, bx) - r > fx1 ||
                max(ay, by) + r < fy0 || min(ay, by) - r > fy1
            ) return
            if (segmentToRectDistSq(ax, ay, bx, by, fx0, fy0, fx1, fy1) > rSq + 1e-3f) return

            // Exact X projection of Capsule(A, B, r) ∩ (R × [fy0, fy1]), then clamped to [fx0, fx1]
            val xSpan = capsuleSpanAlongPrimary(ax, ay, bx, by, r, fy0, fy1)
            if (xSpan != null) {
                val lx = max(fx0, xSpan.first)
                val rx = min(fx1, xSpan.second)
                if (rx >= lx) {
                    if (lx < minX) minX = lx
                    if (rx > maxX) maxX = rx
                }
            }
            // Exact Y projection of Capsule(A, B, r) ∩ ([fx0, fx1] × R), then clamped to [fy0, fy1]
            val ySpan = capsuleSpanAlongPrimary(ay, ax, by, bx, r, fx0, fx1)
            if (ySpan != null) {
                val ly = max(fy0, ySpan.first)
                val ry = min(fy1, ySpan.second)
                if (ry >= ly) {
                    if (ly < minY) minY = ly
                    if (ry > maxY) maxY = ry
                }
            }
        }

        if (points.size == 1) {
            accumulateSegment(points[0].x, points[0].y, points[0].x, points[0].y)
        } else {
            for (i in 1 until points.size) {
                val a = points[i - 1]
                val b = points[i]
                accumulateSegment(a.x, a.y, b.x, b.y)
            }
        }

        if (!minX.isFinite() || !minY.isFinite()) return null
        val l = max(wx0, floor(minX - 1e-3f).toInt())
        val t = max(wy0, floor(minY - 1e-3f).toInt())
        val rt = min(wx1, ceil(maxX + 1e-3f).toInt())
        val bt = min(wy1, ceil(maxY + 1e-3f).toInt())
        return if (rt > l && bt > t) intArrayOf(l, t, rt, bt) else null
    }

    /**
     * Exact projection `[minP, maxP]` onto the primary axis `p` of the intersection between
     * `Capsule((ap, aq) -> (bp, bq), r)` and the strip `q in [q0, q1]`.
     */
    private fun capsuleSpanAlongPrimary(
        ap: Float,
        aq: Float,
        bp: Float,
        bq: Float,
        r: Float,
        q0: Float,
        q1: Float
    ): Pair<Float, Float>? {
        val dp = bp - ap
        val dq = bq - aq
        var lo = Float.POSITIVE_INFINITY
        var hi = Float.NEGATIVE_INFINITY
        val rSq = r * r

        fun evalAt(sRaw: Float) {
            val s = sRaw.coerceIn(0f, 1f)
            val q = aq + dq * s
            val d = when {
                q < q0 -> q0 - q
                q > q1 -> q - q1
                else -> 0f
            }
            if (d <= r + 1e-3f) {
                val hw = sqrt(max(0f, rSq - d * d))
                val p = ap + dp * s
                if (p - hw < lo) lo = p - hw
                if (p + hw > hi) hi = p + hw
            }
        }

        evalAt(0f)
        evalAt(1f)
        if (abs(dq) > 1e-6f) {
            val invDq = 1f / dq
            evalAt((q0 - r - aq) * invDq)
            evalAt((q0 - aq) * invDq)
            evalAt((q1 - aq) * invDq)
            evalAt((q1 + r - aq) * invDq)
            val len = hypot(dp, dq)
            if (len > 1e-6f) {
                val qStar = r * abs(dp) / len
                evalAt((q0 - qStar - aq) * invDq)
                evalAt((q1 + qStar - aq) * invDq)
            }
        }
        return if (lo.isFinite() && hi >= lo) lo to hi else null
    }

    /**
     * Greedily merges neighboring strips in sequence when replacing two strips with their bounding union
     * saves more patch-overhead bytes than the extra background pixel bytes it introduces, without
     * overlapping any other strip.
     */
    private fun coalesceStrips(strips: List<IntArray>, patchOverheadBytes: Long): List<IntArray> {
        if (strips.size <= 1 || patchOverheadBytes <= 0L) return strips
        val list = ArrayList<IntArray>(strips.size)
        for (s in strips) list.add(s)

        var changed = true
        while (changed && list.size > 1) {
            changed = false
            var i = 0
            while (i < list.size - 1) {
                val a = list[i]
                val b = list[i + 1]
                val ul = min(a[0], b[0])
                val ut = min(a[1], b[1])
                val ur = max(a[2], b[2])
                val ub = max(a[3], b[3])
                val areaA = (a[2] - a[0]).toLong() * (a[3] - a[1])
                val areaB = (b[2] - b[0]).toLong() * (b[3] - b[1])
                val areaU = (ur - ul).toLong() * (ub - ut)
                val extraBytes = (areaU - areaA - areaB) * 4L
                if (extraBytes < patchOverheadBytes && !overlapsAnyExcept(list, i, i + 1, ul, ut, ur, ub)) {
                    list[i] = intArrayOf(ul, ut, ur, ub)
                    list.removeAt(i + 1)
                    changed = true
                } else {
                    i++
                }
            }
        }
        return list
    }

    private fun overlapsAnyExcept(
        list: List<IntArray>,
        skipA: Int,
        skipB: Int,
        l: Int,
        t: Int,
        r: Int,
        b: Int
    ): Boolean {
        for (idx in list.indices) {
            if (idx == skipA || idx == skipB) continue
            val o = list[idx]
            if (l < o[2] && o[0] < r && t < o[3] && o[1] < b) return true
        }
        return false
    }

    /** Exact squared Euclidean distance between line segment `(ax, ay)-(bx, by)` and rectangle `[rx0, ry0, rx1, ry1]`. */
    private fun segmentToRectDistSq(
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float,
        rx0: Float,
        ry0: Float,
        rx1: Float,
        ry1: Float
    ): Float {
        if (segmentIntersectsRect(ax, ay, bx, by, rx0, ry0, rx1, ry1)) return 0f
        var best = pointToRectDistSq(ax, ay, rx0, ry0, rx1, ry1)
        best = min(best, pointToRectDistSq(bx, by, rx0, ry0, rx1, ry1))
        best = min(best, pointToSegmentDistSq(rx0, ry0, ax, ay, bx, by))
        best = min(best, pointToSegmentDistSq(rx1, ry0, ax, ay, bx, by))
        best = min(best, pointToSegmentDistSq(rx0, ry1, ax, ay, bx, by))
        best = min(best, pointToSegmentDistSq(rx1, ry1, ax, ay, bx, by))
        return best
    }

    /** Liang-Barsky segment-rectangle intersection check. */
    private fun segmentIntersectsRect(
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float,
        rx0: Float,
        ry0: Float,
        rx1: Float,
        ry1: Float
    ): Boolean {
        var t0 = 0f
        var t1 = 1f
        val dx = bx - ax
        val dy = by - ay

        fun clip(p: Float, q: Float): Boolean {
            if (abs(p) < 1e-8f) return q >= 0f
            val r = q / p
            if (p < 0f) {
                if (r > t1) return false
                if (r > t0) t0 = r
            } else {
                if (r < t0) return false
                if (r < t1) t1 = r
            }
            return true
        }

        return clip(-dx, ax - rx0) &&
            clip(dx, rx1 - ax) &&
            clip(-dy, ay - ry0) &&
            clip(dy, ry1 - ay)
    }

    private fun pointToRectDistSq(px: Float, py: Float, rx0: Float, ry0: Float, rx1: Float, ry1: Float): Float {
        val dx = when {
            px < rx0 -> rx0 - px
            px > rx1 -> px - rx1
            else -> 0f
        }
        val dy = when {
            py < ry0 -> ry0 - py
            py > ry1 -> py - ry1
            else -> 0f
        }
        return dx * dx + dy * dy
    }

    private fun pointToSegmentDistSq(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val lenSq = dx * dx + dy * dy
        if (lenSq <= 1e-8f) {
            val ex = px - ax
            val ey = py - ay
            return ex * ex + ey * ey
        }
        val t = (((px - ax) * dx + (py - ay) * dy) / lenSq).coerceIn(0f, 1f)
        val cx = ax + t * dx
        val cy = ay + t * dy
        val ex = px - cx
        val ey = py - cy
        return ex * ex + ey * ey
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
        /** One memory page: the default cost of an extra patch before anything has been measured. */
        const val PAGE_BYTES = 4096L
        /** Practical RAM/group tile overhead when subdividing live drawing strokes into grouped patches. */
        const val GROUPED_TILE_OVERHEAD_BYTES = 1536L
        /** Smallest power-of-two slice granularity (8 px) for fine diagonal and thick-stroke subdivision. */
        const val MIN_TILE_SIZE = 8
        private const val MAX_GRID_CELLS = 65_536L
    }
}

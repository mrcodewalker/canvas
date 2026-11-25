package com.example.canvas.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class StrokeDirtyRegionTest {

    private val size = 2048

    private fun area(rs: List<IntArray>) = rs.sumOf { (it[2] - it[0]).toLong() * (it[3] - it[1]) }

    private fun hollowRect(x0: Float, y0: Float, x1: Float, y1: Float, w: Float) = Stroke(
        color = 0, width = w,
        points = mutableListOf(
            StrokePoint(x0, y0), StrokePoint(x1, y0), StrokePoint(x1, y1),
            StrokePoint(x0, y1), StrokePoint(x0, y0)
        )
    )

    @Test
    fun hollowRectangleStoresFarLessThanBoundingBox() {
        val s = hollowRect(200f, 200f, 1400f, 1400f, 8f)
        val b = s.boundsInts(size, size)
        val boxArea = (b[2] - b[0]).toLong() * (b[3] - b[1])
        val tiles = s.dirtyRegionInts(size, size)
        assertTrue("tiles=${area(tiles)} box=$boxArea", area(tiles) < boxArea / 10)
    }

    @Test
    fun diagonalLineStoresTightSubdividedStaircase() {
        val s = Stroke(0, 6f, mutableListOf(StrokePoint(100f, 100f), StrokePoint(1500f, 1500f)))
        val b = s.boundsInts(size, size)
        val boxArea = (b[2] - b[0]).toLong() * (b[3] - b[1])
        val tiles = s.dirtyRegionInts(size, size)
        assertTrue("diagonal should split into many tight slices, got ${tiles.size}", tiles.size >= 16)
        // Tight capsule-clipped bands use < 6% of the 1410x1410 bounding box
        assertTrue("tiles=${area(tiles)} box=$boxArea", area(tiles) < boxArea / 16)
    }

    @Test
    fun thickDiagonalStrokeSubdividesFinelyInsteadOfCoarseBlock() {
        // Thick brush (width = 120px) drawn diagonally across the canvas
        val s = Stroke(0, 120f, mutableListOf(StrokePoint(200f, 200f), StrokePoint(1400f, 1400f)))
        val b = s.boundsInts(size, size)
        val boxArea = (b[2] - b[0]).toLong() * (b[3] - b[1])
        val regions = s.dirtyRegionInts(size, size, Stroke.GROUPED_TILE_OVERHEAD_BYTES)
        assertTrue("thick diagonal must subdivide into multiple bands, got ${regions.size}", regions.size >= 8)
        assertTrue("thick diagonal area=${area(regions)} vs box=$boxArea", area(regions) < (boxArea * 30) / 100)
        assertAllPaintedCapsulePixelsCovered(s, regions)
    }

    @Test
    fun shallowDiagonalThickStrokeUsesColumnSlicingToSaveArea() {
        // Gentle slope (dx = 1500, dy = 220) with thick 80px brush
        val s = Stroke(0, 80f, mutableListOf(StrokePoint(150f, 500f), StrokePoint(1650f, 720f)))
        val b = s.boundsInts(size, size)
        val boxArea = (b[2] - b[0]).toLong() * (b[3] - b[1])
        val regions = s.dirtyRegionInts(size, size, Stroke.GROUPED_TILE_OVERHEAD_BYTES)
        assertTrue("shallow thick diagonal should split, got ${regions.size}", regions.size >= 4)
        assertTrue("area=${area(regions)} vs box=$boxArea", area(regions) < (boxArea * 55) / 100)
        assertAllPaintedCapsulePixelsCovered(s, regions)
    }

    @Test
    fun thickCurvedArcSubdividesCleanly() {
        val pts = mutableListOf<StrokePoint>()
        for (i in 0..24) {
            val a = (Math.PI * 0.5 * i) / 24.0
            pts.add(StrokePoint(300f + 900f * cos(a).toFloat(), 300f + 900f * sin(a).toFloat()))
        }
        val s = Stroke(0, 110f, pts)
        val b = s.boundsInts(size, size)
        val boxArea = (b[2] - b[0]).toLong() * (b[3] - b[1])
        val regions = s.dirtyRegionInts(size, size, Stroke.GROUPED_TILE_OVERHEAD_BYTES)
        assertTrue("thick arc should split into tight strips, got ${regions.size}", regions.size >= 6)
        assertTrue("thick arc area=${area(regions)} vs box=$boxArea", area(regions) < (boxArea * 38) / 100)
        assertAllPaintedCapsulePixelsCovered(s, regions)
    }

    @Test
    fun denseSmallStrokeFallsBackToSingleBox() {
        val s = Stroke(0, 40f, mutableListOf(StrokePoint(500f, 500f), StrokePoint(540f, 520f)))
        val r = s.dirtyRegionInts(size, size)
        assertEquals(1, r.size)
        assertEquals(s.boundsInts(size, size).toList(), r[0].toList())
    }

    @Test
    fun regionsAreDisjointInsideBoundsAndCoverEveryPaintedPoint() {
        val s = hollowRect(150f, 300f, 900f, 1200f, 10f)
        val regions = s.dirtyRegionInts(size, size)
        assertAllPaintedCapsulePixelsCovered(s, regions)
    }

    private fun assertAllPaintedCapsulePixelsCovered(s: Stroke, regions: List<IntArray>) {
        val b = s.boundsInts(size, size)
        for (r in regions) {
            assertTrue(r[0] >= b[0] && r[1] >= b[1] && r[2] <= b[2] && r[3] <= b[3])
        }
        for (i in regions.indices) for (j in i + 1 until regions.size) {
            val a = regions[i]
            val c = regions[j]
            val overlap = a[0] < c[2] && c[0] < a[2] && a[1] < c[3] && c[1] < a[3]
            assertTrue("regions $i and $j overlap", !overlap)
        }
        val radius = s.width / 2f
        val pts = s.points
        for (i in 1 until pts.size) {
            val a = pts[i - 1]
            val c = pts[i]
            for (k in 0..200) {
                val t = k / 200f
                val x = a.x + (c.x - a.x) * t
                val y = a.y + (c.y - a.y) * t
                val diag = radius * 0.7071f
                val offsets = listOf(
                    radius to 0f, -radius to 0f, 0f to radius, 0f to -radius,
                    diag to diag, -diag to diag, diag to -diag, -diag to -diag, 0f to 0f
                )
                for ((ox, oy) in offsets) {
                    val px = (x + ox).toInt().coerceIn(0, size - 1)
                    val py = (y + oy).toInt().coerceIn(0, size - 1)
                    assertTrue(
                        "pixel ($px,$py) not covered",
                        regions.any { px >= it[0] && px < it[2] && py >= it[1] && py < it[3] }
                    )
                }
            }
        }
    }

    @Test
    fun singlePointStrokeWorks() {
        val s = Stroke(0, 12f, mutableListOf(StrokePoint(300f, 300f)))
        assertEquals(1, s.dirtyRegionInts(size, size).size)
    }
}

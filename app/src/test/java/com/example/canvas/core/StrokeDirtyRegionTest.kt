package com.example.canvas.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
        assertTrue("tiles=${area(tiles)} box=$boxArea", area(tiles) < boxArea / 5)
    }

    @Test
    fun diagonalLineStoresFarLessThanBoundingBox() {
        val s = Stroke(0, 6f, mutableListOf(StrokePoint(100f, 100f), StrokePoint(1500f, 1500f)))
        val b = s.boundsInts(size, size)
        val boxArea = (b[2] - b[0]).toLong() * (b[3] - b[1])
        assertTrue(area(s.dirtyRegionInts(size, size)) < boxArea / 4)
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
        val b = s.boundsInts(size, size)
        val regions = s.dirtyRegionInts(size, size)
        for (r in regions) {
            assertTrue(r[0] >= b[0] && r[1] >= b[1] && r[2] <= b[2] && r[3] <= b[3])
        }
        for (i in regions.indices) for (j in i + 1 until regions.size) {
            val a = regions[i]
            val c = regions[j]
            val overlap = a[0] < c[2] && c[0] < a[2] && a[1] < c[3] && c[1] < a[3]
            assertTrue("regions $i and $j overlap", !overlap)
        }
        // every pixel within the brush radius of the polyline must be inside some region
        val radius = s.width / 2f
        val pts = s.points
        for (i in 1 until pts.size) {
            val a = pts[i - 1]
            val c = pts[i]
            for (k in 0..200) {
                val t = k / 200f
                val x = a.x + (c.x - a.x) * t
                val y = a.y + (c.y - a.y) * t
                for ((ox, oy) in listOf(radius to 0f, -radius to 0f, 0f to radius, 0f to -radius, 0f to 0f)) {
                    val px = (x + ox).toInt()
                    val py = (y + oy).toInt()
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

package com.example.canvas.bench

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptedDrawerTest {

    @Test
    fun testSameSeedGeneratesIdenticalStrokes() {
        val seed = 12345L
        val drawer1 = ScriptedDrawer(seed = seed)
        val drawer2 = ScriptedDrawer(seed = seed)

        val strokes1 = drawer1.generateStrokes(30, StrokeType.LARGE)
        val strokes2 = drawer2.generateStrokes(30, StrokeType.LARGE)

        assertEquals(strokes1.size, strokes2.size)

        for (i in strokes1.indices) {
            val s1 = strokes1[i]
            val s2 = strokes2[i]

            assertEquals("Color mismatch at stroke $i", s1.color, s2.color)
            assertEquals("Width mismatch at stroke $i", s1.width, s2.width, 0.0001f)
            assertEquals("Points count mismatch at stroke $i", s1.points.size, s2.points.size)

            for (j in s1.points.indices) {
                val p1 = s1.points[j]
                val p2 = s2.points[j]
                assertEquals("Point X mismatch at stroke $i point $j", p1.x, p2.x, 0.0001f)
                assertEquals("Point Y mismatch at stroke $i point $j", p1.y, p2.y, 0.0001f)
            }
        }
    }

    @Test
    fun testSmallStrokesAreaCoverage() {
        val drawer = ScriptedDrawer(seed = 999L)
        val strokes = drawer.generateStrokes(20, StrokeType.SMALL)
        val canvasArea = 2048.0 * 2048.0

        for ((index, stroke) in strokes.withIndex()) {
            val bounds = stroke.computeBounds(2048, 2048)
            val area = (bounds.right - bounds.left).toDouble() * (bounds.bottom - bounds.top).toDouble()
            val ratio = area / canvasArea

            // Allow reasonable tolerance: 1.5% to 15% (target ~2-10%)
            assertTrue(
                "Stroke $index area ratio $ratio should be >= 0.015",
                ratio >= 0.015
            )
            assertTrue(
                "Stroke $index area ratio $ratio should be <= 0.15",
                ratio <= 0.15
            )
        }
    }

    @Test
    fun testLargeStrokesAreaCoverage() {
        val drawer = ScriptedDrawer(seed = 888L)
        val strokes = drawer.generateStrokes(20, StrokeType.LARGE)
        val canvasArea = 2048.0 * 2048.0

        for ((index, stroke) in strokes.withIndex()) {
            val bounds = stroke.computeBounds(2048, 2048)
            val area = (bounds.right - bounds.left).toDouble() * (bounds.bottom - bounds.top).toDouble()
            val ratio = area / canvasArea

            // Target 40% to 100%
            assertTrue(
                "Stroke $index large ratio $ratio should be >= 0.35",
                ratio >= 0.35
            )
            assertTrue(
                "Stroke $index large ratio $ratio should be <= 1.0",
                ratio <= 1.0
            )
        }
    }
}

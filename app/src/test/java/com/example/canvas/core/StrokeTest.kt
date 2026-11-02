package com.example.canvas.core

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeTest {

    @Test
    fun testEmptyStrokeBounds() {
        val stroke = Stroke(color = Color.BLACK, width = 10f)
        val bounds = stroke.computeBounds(2048, 2048)
        assertEquals(0, bounds.left)
        assertEquals(0, bounds.top)
        assertEquals(0, bounds.right)
        assertEquals(0, bounds.bottom)
    }

    @Test
    fun testSinglePointStrokeBounds() {
        val stroke = Stroke(color = Color.RED, width = 20f)
        stroke.addPoint(100f, 100f)
        // width = 20, padding = 10 + 2 = 12
        val bounds = stroke.computeBounds(2048, 2048)
        assertEquals(88, bounds.left)
        assertEquals(88, bounds.top)
        assertEquals(112, bounds.right)
        assertEquals(112, bounds.bottom)
    }

    @Test
    fun testStrokeBoundsClipping() {
        val stroke = Stroke(color = Color.BLUE, width = 30f)
        // padding = 15 + 2 = 17
        stroke.addPoint(5f, 5f)
        stroke.addPoint(2045f, 2045f)

        val bounds = stroke.computeBounds(2048, 2048)
        // 5 - 17 = -12 -> clamped to 0
        assertEquals(0, bounds.left)
        assertEquals(0, bounds.top)
        // 2045 + 17 = 2062 -> clamped to 2048
        assertEquals(2048, bounds.right)
        assertEquals(2048, bounds.bottom)
    }

    @Test
    fun testMultiPointStrokeEnclosure() {
        val stroke = Stroke(color = Color.GREEN, width = 10f)
        // padding = 5 + 2 = 7
        stroke.addPoint(50f, 150f)
        stroke.addPoint(200f, 80f)
        stroke.addPoint(120f, 300f)

        val bounds = stroke.computeBounds(2048, 2048)
        // minX=50 -> 43, minY=80 -> 73, maxX=200 -> 207, maxY=300 -> 307
        assertEquals(43, bounds.left)
        assertEquals(73, bounds.top)
        assertEquals(207, bounds.right)
        assertEquals(307, bounds.bottom)
    }
}

package com.example.canvas.bench

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiverseStrokesTest {

    @Test
    fun sameSeedGivesTheSameDiverseStrokes() {
        val a = ScriptedDrawer(seed = 5).generateStrokes(60, StrokeType.DIVERSE)
        val b = ScriptedDrawer(seed = 5).generateStrokes(60, StrokeType.DIVERSE)
        assertEquals(a.map { it.points.size to it.width }, b.map { it.points.size to it.width })
    }

    @Test
    fun diverseSessionIsNotOneRepeatedShape() {
        val strokes = ScriptedDrawer(seed = 11).generateStrokes(300, StrokeType.DIVERSE)
        var horizontal = 0
        var vertical = 0
        var small = 0
        var large = 0
        var thin = 0
        var thick = 0
        var white = 0
        for (s in strokes) {
            val b = s.boundsInts(2048, 2048)
            val w = b[2] - b[0]
            val h = b[3] - b[1]
            if (w > 3 * h) horizontal++
            if (h > 3 * w) vertical++
            val area = w.toLong() * h
            if (area < 2048L * 2048 / 10) small++
            if (area > 2048L * 2048 * 4 / 10) large++
            if (s.width <= 12f) thin++
            if (s.width >= 100f) thick++
            if (s.color == Color.WHITE) white++
            assertTrue("stroke must stay on the canvas", w in 1..2048 && h in 1..2048)
            assertTrue("a stroke needs at least two points", s.points.size >= 2)
        }
        assertTrue("horizontal lines: $horizontal", horizontal >= 15)
        assertTrue("vertical lines: $vertical", vertical >= 15)
        assertTrue("small strokes: $small", small >= 30)
        assertTrue("large strokes: $large", large >= 15)
        assertTrue("thin brushes: $thin", thin >= 15)
        assertTrue("broad brushes: $thick", thick >= 15)
        assertTrue("erase strokes: $white", white >= 5)
    }
}

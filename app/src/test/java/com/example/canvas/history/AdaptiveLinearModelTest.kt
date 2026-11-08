package com.example.canvas.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveLinearModelTest {

    @Test
    fun learnsFixedPlusPerByteCost() {
        val m = AdaptiveLinearModel()
        // time = 2 ms + 1 ms per KiB
        for (kb in listOf(4, 16, 64, 8, 32, 128, 2, 48)) {
            m.update(kb * 1024L, ((2.0 + kb) * 1_000_000).toLong())
        }
        assertEquals(2.0 + 100.0, m.predict(100 * 1024L)!! / 1_000_000.0, 1.0)
        assertEquals(2.0 + 1.0, m.predict(1024L)!! / 1_000_000.0, 1.0)
    }

    @Test
    fun adaptsQuicklyWhenTheDeviceGetsSlower() {
        val m = AdaptiveLinearModel()
        val rnd = java.util.Random(3)
        fun sample(factor: Double) {
            val kb = 8 + rnd.nextInt(56)
            val noise = 1.0 + (rnd.nextDouble() - 0.5) * 0.1
            m.update(kb * 1024L, (kb * 1_000_000.0 * factor * noise).toLong())
        }
        repeat(200) { sample(1.0) }
        repeat(30) { sample(5.0) }      // disk suddenly 5x slower
        val predicted = m.predict(32 * 1024L)!! / 1_000_000.0
        // A plain cumulative average would still say ~1.2 ms/KiB * 32 = ~40 ms; the adaptive one has moved on
        assertTrue("predicted=$predicted", predicted > 120.0)
    }

    @Test
    fun tailEstimateIsAtLeastTheMean() {
        val m = AdaptiveLinearModel()
        val rnd = java.util.Random(5)
        repeat(100) {
            val kb = 8 + rnd.nextInt(56)
            m.update(kb * 1024L, (kb * 1_000_000.0 * (0.5 + rnd.nextDouble())).toLong())
        }
        val mean = m.predict(32 * 1024L)!!
        val tail = m.predictTail(32 * 1024L)!!
        assertTrue(tail >= mean)
        assertTrue(tail > mean * 1.05)
    }

    @Test
    fun unknownBeforeFirstSample() {
        assertNull(AdaptiveLinearModel().predict(1024))
    }
}

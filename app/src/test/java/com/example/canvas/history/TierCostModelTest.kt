package com.example.canvas.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TierCostModelTest {

    @Test
    fun exploresUntilCostAndSavingAreKnown() {
        val m = TierCostModel()
        assertTrue(m.shouldRecompress(warmBytes = 100, rawLength = 1000))
    }

    @Test
    fun skipsHighLevelRecompressionForSmallPatchesWhenSavingIsTiny() {
        val m = TierCostModel()
        // Recompress costs 1 ns per raw byte and saves 5% of the bytes; disk costs 0.5 ns per byte each way
        m.recordRecompress(rawBytes = 1_000_000, warmBytes = 100_000, coldBytes = 95_000, ns = 1_000_000)
        m.recordWrite(100_000, 50_000)
        m.recordRead(100_000, 50_000)

        // 200 B patch: saves ~10 B => worth ~10 ns, costs ~4000 ns of CPU
        assertFalse(m.shouldRecompress(warmBytes = 200, rawLength = 4_000))
        // 1 MiB raw / 100 KB warm patch: saves ~5 KB => worth ~5000 ns, costs ~1,000,000 ns
        assertFalse(m.shouldRecompress(warmBytes = 100_000, rawLength = 1_000_000))
    }

    @Test
    fun recompressesWhenSavingIsLargeComparedToCpuCost() {
        val m = TierCostModel()
        // Saves 60% of bytes, CPU is cheap (0.01 ns per raw byte), disk is slow (10 ns per byte)
        m.recordRecompress(rawBytes = 1_000_000, warmBytes = 200_000, coldBytes = 80_000, ns = 10_000)
        m.recordWrite(100_000, 1_000_000)
        m.recordRead(100_000, 1_000_000)
        assertTrue(m.shouldRecompress(warmBytes = 200_000, rawLength = 1_000_000))
    }

    @Test
    fun ratesAreSizeWeightedAverages() {
        val m = TierCostModel()
        assertNull(m.decompressNsPerRawByte())
        m.recordDecompress(1000, 2000)
        m.recordDecompress(3000, 2000)
        assertEquals(1.0, m.decompressNsPerRawByte()!!, 1e-9)
    }

    @Test
    fun resetForgetsEverything() {
        val m = TierCostModel()
        m.recordRead(10, 10)
        m.reset()
        assertNull(m.readNsPerByte())
    }
}

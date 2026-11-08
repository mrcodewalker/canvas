package com.example.canvas.history

import com.example.canvas.core.BufferPool
import com.example.canvas.core.PatchCodec
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CodecPoolAndCalibratorTest {

    @After
    fun reset() {
        BufferPool.limit = { 0L }
        BufferPool.clear()
    }

    // ------------------------------------------------------------------ pool

    @Test
    fun noReuseWhenThePoolMayNotKeepAnything() {
        BufferPool.limit = { 0L }
        val a = BufferPool.acquire(1000)
        BufferPool.release(a)
        assertEquals(0L, BufferPool.retainedBytes())
        assertTrue(a !== BufferPool.acquire(1000))
    }

    @Test
    fun reusesASizeMatchedBufferWithinTheLimit() {
        BufferPool.limit = { 10_000L }
        val a = BufferPool.acquire(4000)
        BufferPool.release(a)
        assertEquals(4000L, BufferPool.retainedBytes())
        assertSame(a, BufferPool.acquire(3500))          // close enough in size: reused
        assertEquals(0L, BufferPool.retainedBytes())
    }

    @Test
    fun neverKeepsMoreThanTheLimitAndNeverHandsOutAWastefullyBigBuffer() {
        BufferPool.limit = { 6_000L }
        val big = BufferPool.acquire(5000)
        val small = BufferPool.acquire(3000)
        BufferPool.release(big)
        BufferPool.release(small)                         // would exceed 6000: dropped
        assertEquals(5000L, BufferPool.retainedBytes())
        val tiny = BufferPool.acquire(100)                // the 5000-byte buffer is far too big for this
        assertTrue(tiny !== big)
    }

    // ------------------------------------------------------------------ codec with an explicit length and right-sized output

    @Test
    fun compressRespectsTheLengthAndSurvivesAWrongSizeGuess() {
        val raw = ByteArray(10_000) { (it % 251).toByte() }
        // pretend the previous call compressed extremely well, so the first output guess is tiny
        PatchCodec.compress(ByteArray(100_000), 1)
        val c = PatchCodec.compress(raw, 1, length = 6_000)
        assertEquals(6_000, c.rawLength)
        assertTrue(raw.copyOf(6_000).contentEquals(PatchCodec.decompress(c.compressedBytes, c.rawLength, c.crc32)))
    }

    // ------------------------------------------------------------------ warm level calibration

    @Test
    fun acceptsCheapLevelsAndStopsWhereTheCostJumps() {
        val c = WarmLevelCalibrator()
        // stroke-like measurements: level 2 and 3 nearly free, level 4 doubles the time for +23% ratio
        for ((level, ratio, ns) in listOf(
            Triple(1, 60.0, 1000L), Triple(2, 66.0, 1000L), Triple(3, 82.0, 1010L), Triple(4, 101.0, 2100L)
        )) c.record(level, 1_000_000, (1_000_000 / ratio).toLong(), ns)
        assertEquals(3, c.best())
    }

    @Test
    fun staysOnTheCheapestLevelUntilEveryLevelWasMeasured() {
        val c = WarmLevelCalibrator()
        assertEquals(1, c.best())
        c.record(1, 1_000_000, 20_000, 1000)
        assertEquals(1, c.best())
    }

    @Test
    fun rejectsALevelThatCostsMoreThanItGains() {
        val c = WarmLevelCalibrator()
        c.record(1, 1_000_000, 20_000, 1000)
        c.record(2, 1_000_000, 19_800, 3000)    // +1% ratio for 3x the time
        c.record(3, 1_000_000, 19_000, 3100)
        c.record(4, 1_000_000, 18_500, 3200)
        assertEquals(1, c.best())
    }

    @Test
    fun calibratesOnlyForAFewPatches() {
        val c = WarmLevelCalibrator()
        assertTrue(c.needsCalibration())
        repeat(3) { c.patchDone() }
        assertTrue(!c.needsCalibration())
    }

    @Test
    fun bigPatchesStillRoundTripAndKeepChecksumProtection() {
        for (size in listOf(3_000_000, 5_242_881)) {
            val raw = ByteArray(size) { ((it * 7) % 251 + (it / 4096) % 5).toByte() }
            val c = PatchCodec.compress(raw, 3)
            assertTrue(raw.contentEquals(PatchCodec.decompress(c.compressedBytes, c.rawLength, c.crc32)))
            var failed = false
            try {
                PatchCodec.decompress(c.compressedBytes, c.rawLength, c.crc32 xor 1L)
            } catch (_: IllegalStateException) {
                failed = true
            }
            assertTrue(failed)
        }
    }
}

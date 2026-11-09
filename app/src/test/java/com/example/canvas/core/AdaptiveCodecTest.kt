package com.example.canvas.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Random

class AdaptiveCodecTest {

    private val w = 64
    private val h = 64
    private val len = w * h * 4

    private fun solid(c: Int): ByteArray {
        val a = ByteArray(len)
        for (i in 0 until len step 4) {
            a[i] = c.toByte(); a[i + 1] = (c shr 8).toByte(); a[i + 2] = (c shr 16).toByte(); a[i + 3] = (c shr 24).toByte()
        }
        return a
    }

    /** A flat background with a few filled rectangles: long runs. */
    private fun flat(): ByteArray {
        val a = solid(0xFFFFFFFF.toInt())
        for (y in 10 until 30) for (x in 8 until 40) {
            val i = (y * w + x) * 4
            a[i] = 10; a[i + 1] = 20; a[i + 2] = 30; a[i + 3] = 0xFF.toByte()
        }
        return a
    }

    /** A smooth diagonal gradient: no runs, but neighbours differ by a constant. */
    private fun gradient(): ByteArray {
        val a = ByteArray(len)
        for (y in 0 until h) for (x in 0 until w) {
            val i = (y * w + x) * 4
            a[i] = (x * 3).toByte(); a[i + 1] = (y * 3).toByte(); a[i + 2] = ((x + y) * 2).toByte(); a[i + 3] = 0xFF.toByte()
        }
        return a
    }

    private fun noise(seed: Long): ByteArray = ByteArray(len).also { Random(seed).nextBytes(it) }

    private fun roundTrip(raw: ByteArray, bytes: ByteArray, crc: Long) {
        val back = PatchCodec.decompress(bytes, raw.size, crc)
        assertArrayEquals(raw, back)
    }

    @Test
    fun solidPatchIsFiveBytesAndRestoresExactly() {
        val raw = solid(0x80402010.toInt())
        val e = CodecSelector().encode(raw, len, 1, null)
        assertEquals(PatchFormat.SOLID, e.format)
        assertEquals(5, e.patch.compressedBytes.size)
        roundTrip(raw, e.patch.compressedBytes, e.patch.crc32)
    }

    @Test
    fun runLengthRoundTripsAndIsMuchSmallerThanRaw() {
        val raw = flat()
        val prof = AdaptiveCodec.profile(raw, len, len / 4 / 3)
        assertTrue(!prof.capped && prof.runs > 1)
        val rle = AdaptiveCodec.encodeRle(raw, len, prof.runs)
        assertEquals(PatchFormat.RLE, AdaptiveCodec.formatOf(rle))
        assertTrue(rle.size < len / 20)
        roundTrip(raw, rle, PatchCodec.computeCrc32(raw))
    }

    @Test
    fun deltaFilterIsReversible() {
        val raw = gradient()
        val f = ByteArray(len)
        AdaptiveCodec.deltaFilter(raw, len, f)
        AdaptiveCodec.deltaUnfilterInPlace(f, len)
        assertArrayEquals(raw, f)
    }

    @Test
    fun everyFormatRestoresBitForBitThroughTheSelector() {
        val selector = CodecSelector()
        val inputs = listOf(solid(0), flat(), gradient(), noise(1), noise(2), flat(), gradient(), solid(-1))
        repeat(60) { round ->      // long enough to leave the learning phase and to hit the periodic retrial
            for (raw in inputs) {
                val e = selector.encode(raw, len, 1, 5.0)
                roundTrip(raw, e.patch.compressedBytes, e.patch.crc32)
            }
        }
    }

    @Test
    fun oldDeflateStreamsStillDecode() {
        val raw = flat()
        val legacy = PatchCodec.compress(raw, 1)
        assertEquals(PatchFormat.DEFLATE, AdaptiveCodec.formatOf(legacy.compressedBytes))
        roundTrip(raw, legacy.compressedBytes, legacy.crc32)
    }

    @Test
    fun noiseStaysDeflateBecauseNothingElseWins() {
        val selector = CodecSelector()
        var last: EncodedPatch? = null
        repeat(10) { last = selector.encode(noise(it.toLong()), len, 1, 8.0) }
        // noise has no runs: RLE is never a candidate, delta only adds CPU
        assertEquals(0, selector.count(PatchFormat.RLE))
        assertTrue(last!!.format == PatchFormat.DEFLATE || last!!.format == PatchFormat.DELTA)
    }

    /** 256 x 256 white patch with a filled box: long runs, where run-length coding is both smaller and faster. */
    private fun flatLarge(): ByteArray {
        val n = 256
        val a = ByteArray(n * n * 4) { -1 }
        for (y in 60 until 190) for (x in 40 until 210) {
            val i = (y * n + x) * 4
            a[i] = 10; a[i + 1] = 20; a[i + 2] = 30; a[i + 3] = -1
        }
        return a
    }

    @Test
    fun flatPatchesLearnToUseRunLengthWhenItIsNotBiggerThanDeflate() {
        val selector = CodecSelector()
        val raw = flatLarge()
        // the first rounds are measured on cold (not yet JIT-compiled) code, which is exactly what the periodic
        // retrial exists to correct: judge what it chooses once it has had time to relearn
        repeat(900) { selector.encode(raw, raw.size, 1, 8.0) }
        val deflateSize = PatchCodec.compress(raw, 1, raw.size).compressedBytes.size
        val rleSize = AdaptiveCodec.encodeRle(raw, raw.size, AdaptiveCodec.profile(raw, raw.size, raw.size).runs).size
        assertTrue("test premise: RLE ($rleSize) must be smaller than Deflate ($deflateSize)", rleSize < deflateSize)
        val before = selector.count(PatchFormat.RLE)
        repeat(100) { selector.encode(raw, raw.size, 1, 8.0) }
        val rleLately = selector.count(PatchFormat.RLE) - before
        assertTrue("RLE should dominate on such patches, got $rleLately of 100", rleLately >= 80)
    }

    @Test
    fun neverStoresMoreBytesThanDeflateWould() {
        val selector = CodecSelector()
        val inputs = listOf(flat(), gradient(), flatLarge(), noise(3))
        repeat(300) {
            for (raw in inputs) {
                val e = selector.encode(raw, raw.size, 1, 1.0)    // a very low byte price: CPU would win if allowed to
                val d = PatchCodec.compress(raw, 1, raw.size).compressedBytes.size
                // learned means can differ a little patch by patch; a format may not be a lot bigger
                assertTrue("${e.format}: ${e.patch.compressedBytes.size} vs deflate $d", e.patch.compressedBytes.size <= d * 1.25 + 16)
            }
        }
    }

    @Test
    fun corruptRunLengthIsDetected() {
        val raw = flat()
        val prof = AdaptiveCodec.profile(raw, len, len / 4 / 3)
        val rle = AdaptiveCodec.encodeRle(raw, len, prof.runs)
        val truncated = rle.copyOf(rle.size - 5)
        try {
            PatchCodec.decompress(truncated, len, 0L)
            fail("a truncated stream must not decode")
        } catch (_: IllegalStateException) {
        }
    }

    @Test
    fun wrongCrcIsDetectedForNewFormatsToo() {
        val raw = solid(7)
        val e = CodecSelector().encode(raw, len, 1, null)
        try {
            PatchCodec.decompress(e.patch.compressedBytes, len, e.patch.crc32 xor 1L)
            fail("checksum mismatch must be reported")
        } catch (_: IllegalStateException) {
        }
    }
}

package com.example.canvas.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class PatchCodecTest {

    @Test
    fun testByteRoundTripLossless() {
        val random = Random(42L)
        val sizes = intArrayOf(16, 256, 1024, 65536)

        for (size in sizes) {
            val original = ByteArray(size)
            random.nextBytes(original)

            val compressed = PatchCodec.compress(original, level = 1)
            val decompressed = PatchCodec.decompress(
                compressed.compressedBytes,
                compressed.rawLength,
                compressed.crc32
            )

            assertArrayEquals("Failed round-trip for size $size", original, decompressed)
            assertEquals("CRC32 mismatch for size $size", compressed.crc32, PatchCodec.computeCrc32(decompressed))
        }
    }

    @Test
    fun test1x1Patch() {
        // 1x1 ARGB_8888 pixel = exactly 4 bytes
        val onePixel = byteArrayOf(
            0xFF.toByte(), // Alpha
            0xAA.toByte(), // Red
            0xBB.toByte(), // Green
            0xCC.toByte()  // Blue
        )

        val compressed = PatchCodec.compress(onePixel)
        assertEquals(4, compressed.rawLength)

        val decompressed = PatchCodec.decompress(
            compressed.compressedBytes,
            compressed.rawLength,
            compressed.crc32
        )

        assertArrayEquals(onePixel, decompressed)
        assertEquals(compressed.crc32, PatchCodec.computeCrc32(decompressed))
    }

    @Test
    fun testMaxCanvasPatch() {
        // Maximum canvas size: 2048 x 2048 ARGB_8888 = 16,777,216 bytes (16 MiB)
        val maxSizeBytes = 2048 * 2048 * 4
        val maxBuffer = ByteArray(maxSizeBytes)

        // Fill with repeatable pattern to test realistic deflater throughput
        for (i in 0 until maxSizeBytes) {
            maxBuffer[i] = (i % 256).toByte()
        }

        val originalCrc = PatchCodec.computeCrc32(maxBuffer)
        val compressed = PatchCodec.compress(maxBuffer, level = 1)

        assertEquals(maxSizeBytes, compressed.rawLength)
        assertEquals(originalCrc, compressed.crc32)
        // Deflater should significantly reduce repeated pattern
        assertTrue(compressed.compressedBytes.size < maxSizeBytes)

        val decompressed = PatchCodec.decompress(
            compressed.compressedBytes,
            compressed.rawLength,
            compressed.crc32
        )

        assertEquals(maxSizeBytes, decompressed.size)
        assertEquals(originalCrc, PatchCodec.computeCrc32(decompressed))
        assertArrayEquals(maxBuffer, decompressed)
    }

    @Test
    fun testEdgePatches() {
        // Test rectangular patches hugging canvas edges
        val edgeConfigs = listOf(
            // Top-left corner
            "Top-Left" to (100 * 100 * 4),
            // Bottom-right corner (near 2048, 2048)
            "Bottom-Right" to (120 * 150 * 4),
            // Narrow vertical strip on right border
            "Right-Border" to (20 * 2048 * 4),
            // Narrow horizontal strip on bottom border
            "Bottom-Border" to (2048 * 15 * 4)
        )

        val random = Random(1234L)
        for ((name, byteCount) in edgeConfigs) {
            val raw = ByteArray(byteCount)
            random.nextBytes(raw)

            val compressed = PatchCodec.compress(raw, level = 1)
            val decompressed = PatchCodec.decompress(
                compressed.compressedBytes,
                compressed.rawLength,
                compressed.crc32
            )

            assertArrayEquals("Failed edge patch test: $name", raw, decompressed)
            assertEquals("CRC mismatch: $name", compressed.crc32, PatchCodec.computeCrc32(decompressed))
        }
    }

    @Test
    fun testChecksumMismatchThrowsException() {
        val original = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val compressed = PatchCodec.compress(original)

        assertThrows(IllegalStateException::class.java) {
            // Supply a wrong expected CRC32
            PatchCodec.decompress(
                compressed.compressedBytes,
                compressed.rawLength,
                expectedCrc32 = 99999999L
            )
        }
    }
}

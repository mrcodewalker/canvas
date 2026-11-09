package com.example.canvas.history

import android.graphics.Rect
import com.example.canvas.core.Patch
import com.example.canvas.core.PatchCodec
import com.example.canvas.core.Stroke
import com.example.canvas.core.StrokeOp
import com.example.canvas.core.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Random

class OptimizationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun rect(): Rect {
        val r = Rect()
        r.left = 0; r.top = 0; r.right = 10; r.bottom = 10
        return r
    }

    // ------------------------------------------------------------------ per-patch compression level

    @Test
    fun tunerTriesTheSingleDeepLevelOncePerClassBeforeDeciding() {
        val t = CompressionTuner()
        val cls = t.classOf(rawBytes = 8000, warmBytes = 1000)
        assertEquals(3, cls)
        val first = t.choose(cls, 1000, 8000, baseline = 1, maxLevel = 6, ioNsPerByte = 1.0, inflateNsPerRaw = 0.0, squeeze = false)
        assertEquals(6, first)
        t.record(cls, first, 8000, 1000, 900, 100)
        // learned: 100 bytes saved at 10 ns/byte of disk beats 1 us of CPU; there is no other level to try
        assertEquals(6, t.choose(cls, 1000, 8000, 1, 6, 10.0, 0.0, false))
    }

    @Test
    fun tunerSkipsRecompressionWhenTheSavingDoesNotPayForTheCpu() {
        val t = CompressionTuner()
        val cls = 5
        // level 9 saves 1% but costs 50 ns per raw byte: with disk at 1 ns/byte that is a loss
        t.record(cls, 6, 100_000, 10_000, 9_950, 2_000_000)
        t.record(cls, 9, 100_000, 10_000, 9_900, 5_000_000)
        val level = t.choose(cls, 10_000, 100_000, 1, 9, ioNsPerByte = 1.0, inflateNsPerRaw = 0.0, squeeze = false)
        assertEquals("write the Warm bytes as they are", 1, level)
    }

    @Test
    fun tunerKeepsTheDeepLevelOnlyWhenItsNetBenefitIsPositive() {
        val t = CompressionTuner()
        val cls = 2
        // level 6 saves 30% for little CPU: worth it on a slow disk, not worth it on a very fast one
        t.record(cls, 6, 100_000, 25_000, 17_500, 100_000)
        assertEquals(6, t.choose(cls, 25_000, 100_000, 1, 6, ioNsPerByte = 20.0, inflateNsPerRaw = 0.0, squeeze = false))
        assertEquals(1, t.choose(cls, 25_000, 100_000, 1, 6, ioNsPerByte = 0.0001, inflateNsPerRaw = 0.0, squeeze = false))
    }

    @Test
    fun tunerSqueezesHardestWhenSpaceIsShort() {
        val t = CompressionTuner()
        val cls = 2
        t.record(cls, 6, 100_000, 25_000, 17_500, 100_000)
        t.record(cls, 9, 100_000, 25_000, 17_000, 1_000_000)
        val level = t.choose(cls, 25_000, 100_000, 1, 9, ioNsPerByte = 0.001, inflateNsPerRaw = 0.0, squeeze = true)
        assertEquals(9, level)
    }

    @Test
    fun tunerNeverExceedsTheLevelTheUserAllows() {
        val t = CompressionTuner()
        assertEquals(6, t.choose(1, 1000, 8000, 1, maxLevel = 6, ioNsPerByte = 1.0, inflateNsPerRaw = 0.0, squeeze = false))
        assertEquals(1, t.choose(1, 1000, 8000, 1, maxLevel = 1, ioNsPerByte = 1.0, inflateNsPerRaw = 0.0, squeeze = false))
    }

    // ------------------------------------------------------------------ redo depth from habit

    @Test
    fun redoDepthFollowsTheUsersObservedRuns() {
        val b = RedoBehavior()
        assertEquals(1.0, b.reach(50), 1e-9)               // nothing known: everything possible
        repeat(5) { repeat(3) { b.onRedo() }; b.onOther() }
        assertEquals(3, b.typicalDepth())
        assertEquals(1.0, b.reach(3), 1e-9)
        assertTrue(b.reach(4) < 0.2)                        // nobody ever redid 4 in a row
    }

    // ------------------------------------------------------------------ O(1) stores

    @Test
    fun queueKeepsOrderAndRemovesInConstantTime() {
        val q = PatchQueue()
        val patches = (1..60_000).map { Patch(id = it.toLong(), rect = rect()) }
        val start = System.nanoTime()
        patches.forEach { q.addLast(it) }
        for (p in patches.reversed()) assertTrue(q.remove(p))     // worst case for a scan: remove from the far end
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue(q.isEmpty())
        assertTrue("took $ms ms - looks quadratic", ms < 3_000)
    }

    @Test
    fun queueDistinguishesTwoPatchesWithTheSameFields() {
        val q = PatchQueue()
        val a = Patch(id = 1, rect = rect())
        val b = Patch(id = 1, rect = rect())            // equal by value, different instance
        q.addLast(a)
        assertTrue(q.contains(a))
        assertFalse(q.contains(b))
        assertFalse(q.remove(b))
        assertEquals(a, q.removeFirst())
    }

    @Test
    fun coldFileIsDeletedOnlyWhenTheLastPatchUsingItGoes() {
        val dir = tempFolder.newFolder("refs")
        val store = ColdStore(dir)
        val bytes = PatchCodec.compress(ByteArray(2000) { (it % 13).toByte() })
        val a = Patch(id = 1, rect = rect(), rawLength = 2000, crc32 = bytes.crc32)
        val file = store.writeToFile(a, bytes.compressedBytes)
        store.add(a)
        val b = Patch(id = 2, rect = rect(), rawLength = 2000, crc32 = bytes.crc32, filePath = file.absolutePath, sizeBytes = a.sizeBytes)
        store.add(b)
        store.recalculateTotalBytes()                      // as the OOM cleaner does after sharing a file
        assertTrue(store.isShared(a))

        store.remove(a)
        assertTrue("still used by the other patch", file.exists())
        store.remove(b)
        assertFalse("last user gone", file.exists())
    }

    // ------------------------------------------------------------------ compact stroke memory

    @Test
    fun strokeOpKeepsEveryPointInAFractionOfTheMemory() {
        val points = (0 until 500).map { StrokePoint(it * 1.5f, it * 0.5f) }.toMutableList()
        val op = StrokeOp(stroke = Stroke(0xFF112233.toInt(), 7f, points))
        val back = op.stroke!!
        assertEquals(0xFF112233.toInt(), back.color)
        assertEquals(7f, back.width, 0f)
        assertEquals(points, back.points)
        assertTrue("compact=${op.approxBytes}", op.approxBytes < 500 * 32 / 3)
    }

    // ------------------------------------------------------------------ codec

    @Test
    fun codecRoundTripsEvenWhenDataDoesNotCompress() {
        val rnd = Random(9)
        for (size in listOf(0, 1, 7, 4096, 100_000)) {
            val raw = ByteArray(size).also { rnd.nextBytes(it) }
            val c = PatchCodec.compress(raw, 1)
            assertTrue("never larger than zlib's bound", c.compressedBytes.size <= size + (size shr 12) + (size shr 14) + 64)
            assertTrue(raw.contentEquals(PatchCodec.decompress(c.compressedBytes, c.rawLength, c.crc32)))
            // checksum is optional: RAM data is read without the extra pass
            assertTrue(raw.contentEquals(PatchCodec.decompress(c.compressedBytes, c.rawLength)))
        }
    }

    @Test
    fun codecStillDetectsCorruptionWhenAChecksumIsGiven() {
        val raw = ByteArray(5000) { (it % 7).toByte() }
        val c = PatchCodec.compress(raw, 1)
        var failed = false
        try {
            PatchCodec.decompress(c.compressedBytes, c.rawLength, c.crc32 xor 1L)
        } catch (_: IllegalStateException) {
            failed = true
        }
        assertTrue(failed)
    }

    // ------------------------------------------------------------------ memory signal

    @Test
    fun memorySignalSeesTheScarcerOfJavaHeapAndDeviceMemory() {
        val saved = MemorySignal.systemFree
        try {
            MemorySignal.systemFree = null
            assertEquals(MemorySignal.javaFree() / 1_000_000, MemorySignal.free() / 1_000_000)
            MemorySignal.systemFree = { 123L }
            assertEquals(123L, MemorySignal.free())
        } finally {
            MemorySignal.systemFree = saved
        }
    }

    @Test
    fun tempFolderSanity() {
        assertTrue(File(tempFolder.newFolder("x").path).exists())
    }
}

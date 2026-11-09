package com.example.canvas.history

import android.graphics.Rect
import com.example.canvas.core.Patch
import com.example.canvas.core.PatchCodec
import com.example.canvas.core.PatchState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Random

class EncodingReuseTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun raw(seed: Long, size: Int = 20_000): ByteArray {
        val r = Random(seed)
        return ByteArray(size) { (r.nextInt(6) + (it / 97) % 5).toByte() }
    }

    private fun rect(): Rect {
        val rect = Rect()
        rect.left = 0; rect.top = 0; rect.right = 100; rect.bottom = 50
        return rect
    }

    private fun policy(dir: File, reuse: Boolean = true): Triple<TierPolicy, WarmStore, ColdStore> {
        val warm = WarmStore()
        val cold = ColdStore(dir)
        val config = TierConfig(
            hotBudgetBytes = Long.MAX_VALUE / 4, warmBudgetBytes = 0L, infiniteUndo = true,
            coldMaxBytes = Long.MAX_VALUE / 4, coldMaxSteps = Int.MAX_VALUE,
            compressionLevel = 1, coldCompressionLevel = 6, adaptiveCompEnabled = true, reuseEncodings = reuse
        )
        return Triple(TierPolicy(config, HotStore(), warm, cold), warm, cold)
    }

    @Test
    fun aPatchComingBackWithItsEncodingIsNotCompressedAgain() = runTest {
        val (p, warm, _) = policy(tempFolder.newFolder())
        val comp = PatchCodec.compress(raw(1), 3)
        val patch = Patch(id = 1, rect = rect(), rawLength = raw(1).size)
        patch.reuse = Patch.Encoding(comp.compressedBytes, comp.rawLength, comp.crc32, level = 3, deep = true, coldTrips = 2)

        p.demoteHotToWarm(patch)

        assertEquals(PatchState.WARM, patch.state)
        assertSame("the bytes made before are kept, not recomputed", comp.compressedBytes, patch.compressedBytes)
        assertEquals(3, patch.warmLevel)
        assertEquals(comp.crc32, patch.crc32)
        assertTrue(patch.deepCompressed)
        assertEquals(2, patch.coldTrips)
        assertEquals(1, p.reusedEncodings)
        assertTrue(warm.contains(patch))
        assertNull(patch.reuse)
    }

    @Test
    fun withTheSwitchOffTheEncodingIsIgnored() = runTest {
        val (p, _, _) = policy(tempFolder.newFolder(), reuse = false)
        val comp = PatchCodec.compress(raw(2), 3)
        val patch = Patch(id = 2, rect = rect(), rawLength = raw(2).size)
        patch.reuse = Patch.Encoding(comp.compressedBytes, comp.rawLength, comp.crc32, 3, false, 0)
        p.demoteHotToWarm(patch)
        assertEquals(0, p.reusedEncodings)
    }

    @Test
    fun goingBackToColdAdoptsTheFileThatIsStillOnDisk() = runTest {
        val dir = tempFolder.newFolder()
        val (p, warm, cold) = policy(dir)
        val comp = PatchCodec.compress(raw(3), 1)

        // a patch that was in Cold, read back (file kept) and now sits in Warm
        val first = Patch(id = 3, rect = rect(), state = PatchState.COLD, rawLength = raw(3).size, crc32 = comp.crc32)
        cold.writeToFile(first, comp.compressedBytes)
        cold.add(first)
        val detached = cold.detach(first)
        assertTrue(detached.wasCold)
        val kept = detached.keptFile!!
        assertTrue(File(kept).exists())
        assertEquals("Cold no longer counts it", 0L, cold.totalBytes)

        first.compressedBytes = comp.compressedBytes
        first.sizeBytes = comp.compressedBytes.size.toLong()
        first.state = PatchState.WARM
        first.keptFile = kept
        first.coldTrips = 1
        warm.add(first)

        val before = dir.listFiles { f -> f.name.endsWith(".chrf") }!!.size
        assertTrue(p.demoteWarmToCold(first))

        assertEquals(PatchState.COLD, first.state)
        assertEquals(kept, first.filePath)
        assertEquals(1, p.reusedColdFiles)
        assertEquals("no new file was written", before, dir.listFiles { f -> f.name.endsWith(".chrf") }!!.size)
        assertTrue(cold.totalBytes > 0)
        val back = cold.readPayload(first)
        assertTrue(raw(3).contentEquals(PatchCodec.decompress(back.compressedBytes, back.rawLength, back.crc32)))
    }

    @Test
    fun aSharedColdFileIsNotKept() {
        val (_, _, cold) = policy(tempFolder.newFolder())
        val comp = PatchCodec.compress(raw(4), 1)
        val a = Patch(id = 4, rect = rect(), state = PatchState.COLD, rawLength = raw(4).size, crc32 = comp.crc32)
        cold.writeToFile(a, comp.compressedBytes)
        val b = Patch(id = 5, rect = rect(), state = PatchState.COLD, rawLength = raw(4).size, crc32 = comp.crc32)
        b.filePath = a.filePath
        b.sizeBytes = a.sizeBytes
        cold.add(a)
        cold.add(b)
        assertTrue("shared: stays with the other patch", cold.isShared(a))
        val d = cold.detach(a)
        assertTrue(d.wasCold)
        assertNull(d.keptFile)
        assertTrue(File(b.filePath!!).exists())
    }

    @Test
    fun detachingAPatchThatIsNotInColdChangesNothing() {
        val (_, _, cold) = policy(tempFolder.newFolder())
        val d = cold.detach(Patch(id = 9, rect = rect()))
        assertFalse(d.wasCold)
        assertNull(d.keptFile)
    }

    @Test
    fun recyclingAPatchThrowsAwayItsEncodingAndDeletesItsFile() {
        val f = tempFolder.newFile("x.chrf")
        val patch = Patch(id = 6, rect = rect())
        patch.reuse = Patch.Encoding(ByteArray(4), 4, 0L, 1, false, 0, f.absolutePath)
        patch.recycle()
        assertNull(patch.reuse)
        assertFalse(f.exists())
    }
}

package com.example.canvas.history

import android.graphics.Rect
import com.example.canvas.core.Patch
import com.example.canvas.core.PatchCodec
import com.example.canvas.core.PatchState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Random

class TierEventsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun rect(): Rect {
        val r = Rect()
        r.left = 0; r.top = 0; r.right = 100; r.bottom = 50
        return r
    }

    private fun warmPatch(id: Long, stroke: Int, seed: Long): Patch {
        val raw = ByteArray(8_000).also { Random(seed).nextBytes(it) }
        val comp = PatchCodec.compress(raw, 1)
        return Patch(
            id = id, rect = rect(), state = PatchState.WARM,
            compressedBytes = comp.compressedBytes, sizeBytes = comp.compressedBytes.size.toLong(),
            rawLength = raw.size, crc32 = comp.crc32, strokeNo = stroke
        )
    }

    private fun policy(log: TierEventLog, warm: WarmStore, cold: ColdStore, diskFree: () -> Long, tier: TierConfig = TierConfig(infiniteUndo = true)) =
        TierPolicy(
            tier, HotStore(), warm, cold,
            diskFreeBytes = diskFree,
            events = log
        )

    @Test
    fun nothingIsDeliveredAndNothingBreaksWhileNobodyListens() {
        val log = TierEventLog()
        log.emit(TierEventKind.NEW, 1, 0, 10)     // no listener: must be a harmless no-op
        var seen = 0
        log.listener = { seen++ }
        log.emit(TierEventKind.NEW, 2, 0, 10)
        assertEquals(1, seen)
    }

    @Test
    fun writingToDiskAnnouncesTheStrokeAndBothSizes() = runTest {
        val warm = WarmStore()
        val cold = ColdStore(tempFolder.newFolder("cold"))
        val events = ArrayList<TierEvent>()
        val log = TierEventLog().also { it.listener = { e -> events.add(e) } }
        val p = warmPatch(1, stroke = 7, seed = 1)
        warm.add(p)
        val warmSize = p.sizeBytes

        assertTrue(policy(log, warm, cold, { Long.MAX_VALUE }).demoteWarmToCold(p))

        val e = events.single { it.kind == TierEventKind.WARM_TO_COLD }
        assertEquals(7, e.stroke)
        assertEquals(warmSize, e.bytesBefore)
        assertEquals(p.sizeBytes, e.bytesAfter)       // file size on disk
    }

    @Test
    fun aFullDiskKeepsTheStrokeInWarmAndSaysSo() = runTest {
        val warm = WarmStore()
        val cold = ColdStore(tempFolder.newFolder("cold2"))
        val events = ArrayList<TierEvent>()
        val log = TierEventLog().also { it.listener = { e -> events.add(e) } }
        val p = warmPatch(2, stroke = 3, seed = 2)
        warm.add(p)

        assertFalse(policy(log, warm, cold, { 0L }).demoteWarmToCold(p))

        assertTrue(events.any { it.kind == TierEventKind.HELD_IN_WARM && it.stroke == 3 })
        assertFalse(events.any { it.kind == TierEventKind.WARM_TO_COLD })
    }

    @Test
    fun droppingAStrokeIsAnnouncedWithItsNumberAndSize() = runTest {
        val warm = WarmStore()
        val cold = ColdStore(tempFolder.newFolder("cold3"))
        val events = ArrayList<TierEvent>()
        val log = TierEventLog().also { it.listener = { e -> events.add(e) } }

        // an old stroke already on disk (deep: nothing left to squeeze), then no room for a new one
        val raw = ByteArray(8_000).also { Random(5).nextBytes(it) }
        val comp = PatchCodec.compress(raw, 1)
        val old = Patch(id = 10, rect = rect(), state = PatchState.COLD, rawLength = raw.size, crc32 = comp.crc32,
            strokeNo = 1, deepCompressed = true)
        cold.writeToFile(old, comp.compressedBytes)
        cold.add(old)
        val used = cold.totalBytes

        val incoming = warmPatch(11, stroke = 2, seed = 6)
        warm.add(incoming)
        val pol = policy(log, warm, cold, { used - cold.totalBytes })      // disk exactly full
        pol.demoteWarmToCold(incoming)

        val e = events.first { it.kind == TierEventKind.EVICT }
        assertEquals(1, e.stroke)
        assertTrue(e.bytesBefore > 0)
    }
}

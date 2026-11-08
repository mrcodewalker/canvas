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

class ColdFullPolicyTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun data(seed: Long, size: Int): ByteArray {
        val r = Random(seed)
        return ByteArray(size) { (r.nextInt(9) + (it / 31) % 7).toByte() }
    }

    private fun rect(): Rect {
        val rect = Rect()
        rect.left = 0; rect.top = 0; rect.right = 100; rect.bottom = 50
        return rect
    }

    private class Setup(val dir: java.io.File, val policyKind: ColdFullPolicy) {
        val hot = HotStore()
        val warm = WarmStore()
        val cold = ColdStore(dir)
        val dropped = ArrayList<Patch>()
        var blocked = 0
        val depthById = HashMap<Long, Int>()
        val config = TierConfig(
            hotBudgetBytes = Long.MAX_VALUE / 4, warmBudgetBytes = 0L, infiniteUndo = true,
            coldFullPolicy = policyKind, adaptiveCompEnabled = true
        )
        lateinit var policy: TierPolicy

        fun build(capacityFree: () -> Long) {
            policy = TierPolicy(
                config, hot, warm, cold,
                onColdEvicted = { dropped.add(it) },
                diskFreeBytes = capacityFree,
                undoDepths = { depthById },
                reachFunction = { { d -> if (d <= 1) 1.0 else 0.2 } },
                onColdBlocked = { blocked++ }
            )
        }
    }

    /** Three strokes: oldest+smallest, newest+medium, middle+largest. Returns ids in cold order. */
    private fun fill(s: Setup): Triple<Patch, Patch, Patch> {
        fun coldPatch(id: Long, group: Long, size: Int, depth: Int): Patch {
            val raw = data(id, size)
            val comp = PatchCodec.compress(raw, 1)
            val p = Patch(
                id = id, rect = rect(), state = PatchState.COLD, rawLength = raw.size,
                crc32 = comp.crc32, groupId = group, deepCompressed = true
            )
            s.cold.writeToFile(p, comp.compressedBytes)
            s.cold.add(p)
            s.depthById[id] = depth
            return p
        }
        val oldest = coldPatch(1, 11, 6_000, depth = 2)    // deepest, smallest
        val middle = coldPatch(2, 22, 12_000, depth = 1)   // largest
        val newest = coldPatch(3, 33, 9_000, depth = 0)
        return Triple(oldest, middle, newest)
    }

    private suspend fun dropOne(kind: ColdFullPolicy): Triple<Setup, List<Patch>, Triple<Patch, Patch, Patch>> {
        val s = Setup(tempFolder.newFolder("p_${kind.name}"), kind)
        val strokes = fill(s)
        val used = s.cold.totalBytes
        s.build { used - s.cold.totalBytes }       // disk completely full; freeing any stroke helps
        s.policy.ensureColdSpace(1)
        return Triple(s, s.dropped.toList(), strokes)
    }

    @Test
    fun oldestFirstDropsTheOldestStroke() = runTest {
        val (_, dropped, st) = dropOne(ColdFullPolicy.OLDEST_FIRST)
        assertEquals(listOf(st.first.id), dropped.map { it.id })
    }

    @Test
    fun largestFirstDropsTheHeaviestStroke() = runTest {
        val (_, dropped, st) = dropOne(ColdFullPolicy.LARGEST_FIRST)
        assertEquals(listOf(st.second.id), dropped.map { it.id })
    }

    @Test
    fun smartDropsTheStrokeWithLeastExpectedValuePerByte() = runTest {
        // reach: depth<=1 -> 1.0 else 0.2. Per byte: oldest 0.2/small, middle 0.2/large(depth 1 -> reach(2)),
        // newest 1.0/medium. The middle one is large AND deep enough to be unlikely -> goes first.
        val (_, dropped, st) = dropOne(ColdFullPolicy.SMART)
        assertEquals(listOf(st.second.id), dropped.map { it.id })
    }

    @Test
    fun keepAllNeverDeletesAndKeepsTheStrokeInWarm() = runTest {
        val s = Setup(tempFolder.newFolder("keep"), ColdFullPolicy.KEEP_ALL)
        fill(s)
        val used = s.cold.totalBytes
        s.build { used - s.cold.totalBytes }
        val raw = data(99, 8_000)
        val comp = PatchCodec.compress(raw, 1)
        val incoming = Patch(
            id = 99, rect = rect(), state = PatchState.WARM, compressedBytes = comp.compressedBytes,
            sizeBytes = comp.compressedBytes.size.toLong(), rawLength = raw.size, crc32 = comp.crc32
        )
        s.warm.add(incoming)

        assertFalse(s.policy.demoteWarmToCold(incoming))
        assertFalse(s.policy.demoteWarmToCold(incoming))   // second try: still no deletion
        assertEquals(3, s.cold.count)
        assertTrue(s.dropped.isEmpty())
        assertEquals(PatchState.WARM, incoming.state)
        assertTrue(s.warm.contains(incoming))
        assertEquals("the user is told once, not on every attempt", 1, s.blocked)
    }
}

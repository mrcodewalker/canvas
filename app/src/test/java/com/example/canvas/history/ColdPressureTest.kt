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

class ColdPressureTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class Env(dir: java.io.File, val capacity: Long?, infinite: Boolean = true) {
        val hot = HotStore()
        val warm = WarmStore()
        val cold = ColdStore(dir)
        val evicted = ArrayList<Patch>()
        var trimmedStrokes = 0
        val config = TierConfig(
            hotBudgetBytes = Long.MAX_VALUE / 4,
            warmBudgetBytes = 0L,
            infiniteUndo = infinite,
            coldMaxBytes = Long.MAX_VALUE / 4,
            coldMaxSteps = Int.MAX_VALUE,
            compressionLevel = 1,
            coldCompressionLevel = 9,
            adaptiveCompEnabled = true
        )
        val policy = TierPolicy(
            config, hot, warm, cold,
            onColdEvicted = { evicted.add(it) },
            diskFreeBytes = capacity?.let { cap -> { cap - cold.totalBytes } },
            onTrimmed = { trimmedStrokes += it }
        )
    }

    private fun data(seed: Long, size: Int = 20_000): ByteArray {
        val r = Random(seed)
        return ByteArray(size) { (r.nextInt(6) + (it / 97) % 5).toByte() }
    }

    private fun rect(): Rect {
        val rect = Rect()
        rect.left = 0; rect.top = 0; rect.right = 100; rect.bottom = 50
        return rect
    }

    /** A Cold patch whose file is written at the *fast* level 1 (so it can be squeezed later). */
    private fun coldPatch(env: Env, id: Long, group: Long, raw: ByteArray): Patch {
        val comp = PatchCodec.compress(raw, 1)
        val p = Patch(
            id = id, rect = rect(), state = PatchState.COLD,
            rawLength = raw.size, crc32 = comp.crc32, groupId = group
        )
        env.cold.writeToFile(p, comp.compressedBytes)
        env.cold.add(p)
        return p
    }

    private fun warmPatch(env: Env, id: Long, raw: ByteArray, group: Long = 0L): Patch {
        val comp = PatchCodec.compress(raw, 1)
        val p = Patch(
            id = id, rect = rect(), state = PatchState.WARM,
            compressedBytes = comp.compressedBytes, sizeBytes = comp.compressedBytes.size.toLong(),
            rawLength = raw.size, crc32 = comp.crc32, groupId = group
        )
        env.warm.add(p)
        return p
    }

    @Test
    fun nothingHappensWhileThereIsRoom() = runTest {
        val env = Env(tempFolder.newFolder("a"), capacity = 10_000_000)
        coldPatch(env, 1, 0, data(1))
        val p = warmPatch(env, 2, data(2))
        assertTrue(env.policy.demoteWarmToCold(p))
        assertEquals(PatchState.COLD, p.state)
        assertEquals(0, env.policy.evictedStrokes)
        assertEquals(0, env.policy.coldPressureEvents)
    }

    @Test
    fun recompressionIsTriedBeforeAnyHistoryIsDropped() = runTest {
        val env = Env(tempFolder.newFolder("b"), capacity = null)
        val patches = (1L..6L).map { coldPatch(env, it, 0, data(it)) }
        val used = env.cold.totalBytes

        // what squeezing everything at level 9 would give back
        val possibleSaving = (1L..6L).sumOf { i ->
            val raw = data(i)
            PatchCodec.compress(raw, 1).compressedBytes.size - PatchCodec.compress(raw, 9).compressedBytes.size.toLong()
        }
        assertTrue("test data must be squeezable", possibleSaving > 100)

        val incoming = warmPatch(env, 100, data(100))
        val need = incoming.compressedBytes!!.size.toLong()
        // room is short by half of what recompression can give back, so squeezing is enough and dropping is not needed
        val capacity = used + need - possibleSaving / 2
        val policy = TierPolicy(
            env.config, env.hot, env.warm, env.cold,
            onColdEvicted = { env.evicted.add(it) },
            diskFreeBytes = { capacity - env.cold.totalBytes },
            onTrimmed = { env.trimmedStrokes += it }
        )
        assertTrue(policy.coldFreeBytes() < need)
        assertTrue(policy.ensureColdSpace(need))
        assertEquals("no stroke may be dropped when recompression is enough", 0, policy.evictedStrokes)
        assertTrue(env.cold.totalBytes < used)
        assertTrue(policy.coldRescuedBytes > 0)
        assertEquals(6, env.cold.count)
        // data is intact after the rewrite
        for ((i, p) in patches.withIndex()) {
            val payload = env.cold.readPayload(p)
            val raw = PatchCodec.decompress(payload.compressedBytes, payload.rawLength, payload.crc32)
            assertTrue(raw.contentEquals(data((i + 1).toLong())))
        }
    }

    @Test
    fun oldestWholeStrokeIsDroppedAndTheNewestStaysIntact() = runTest {
        val env = Env(tempFolder.newFolder("c"), capacity = null)
        // already deep: nothing to squeeze, so dropping is the only way
        val strokeA = (1L..3L).map { coldPatch(env, it, 77, data(it)).also { p -> p.deepCompressed = true } }
        val strokeB = (4L..5L).map { coldPatch(env, it, 88, data(it)).also { p -> p.deepCompressed = true } }
        val used = env.cold.totalBytes
        val bytesOfA = strokeA.sumOf { it.sizeBytes }
        val need = bytesOfA / 2   // dropping A frees more than enough

        val capacity = used + need / 2
        val policy = TierPolicy(
            env.config, env.hot, env.warm, env.cold,
            onColdEvicted = { env.evicted.add(it) },
            diskFreeBytes = { capacity - env.cold.totalBytes },
            onTrimmed = { env.trimmedStrokes += it }
        )
        assertTrue(policy.ensureColdSpace(need))

        assertEquals(1, policy.evictedStrokes)
        assertEquals(1, env.trimmedStrokes)
        assertEquals(3, env.evicted.size)
        assertTrue("every tile of the oldest stroke goes together", env.evicted.all { it.groupId == 77L })
        assertEquals(2, env.cold.count)
        assertTrue(strokeB.all { env.cold.getAll().contains(it) })
    }

    @Test
    fun neverWipesHistoryWhenTheDiskIsFullBecauseOfOtherApps() = runTest {
        val env = Env(tempFolder.newFolder("d"), capacity = null)
        repeat(4) { coldPatch(env, it + 1L, 0, data(it + 1L)).deepCompressed = true }
        val before = env.cold.count
        val p = warmPatch(env, 50, data(50))
        val policy = TierPolicy(
            env.config, env.hot, env.warm, env.cold,
            onColdEvicted = { env.evicted.add(it) },
            diskFreeBytes = { 0L }          // someone else ate the whole disk
        )
        assertFalse(policy.demoteWarmToCold(p))
        assertTrue("history must not be wiped for a shortage it cannot fix", env.cold.count >= before - 1)
        assertTrue(env.evicted.size <= 1)
        // the stroke is not lost: the patch stays in Warm and can still be undone
        assertEquals(PatchState.WARM, p.state)
        assertTrue(env.warm.contains(p))
        assertTrue(p.compressedBytes != null)
    }

    @Test
    fun userConfiguredStepLimitStillDropsWholeStrokes() = runTest {
        val env = Env(tempFolder.newFolder("e"), capacity = null, infinite = false)
        val cfg = env.config.copy(coldMaxSteps = 3, infiniteUndo = false)
        val policy = TierPolicy(
            cfg, env.hot, env.warm, env.cold,
            onColdEvicted = { env.evicted.add(it) },
            diskFreeBytes = null
        )
        coldPatch(env, 1, 10, data(1)); coldPatch(env, 2, 10, data(2))      // stroke 10: two tiles
        coldPatch(env, 3, 20, data(3)); coldPatch(env, 4, 20, data(4))      // stroke 20: two tiles
        policy.enforceBudgets()
        assertTrue("limit respected", env.cold.count <= 3)
        assertTrue("never a half stroke left: ${env.cold.getAll().map { it.groupId }}",
            env.cold.getAll().groupBy { it.groupId }.values.all { it.size == 2 })
    }
}

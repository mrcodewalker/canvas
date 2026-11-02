package com.example.canvas.history

import android.graphics.Rect
import com.example.canvas.core.Patch
import com.example.canvas.core.PatchCodec
import com.example.canvas.core.PatchState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class PrefetcherTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testHitAndMissTracking() {
        val coldStore = ColdStore(tempFolder.newFolder("cold_prefetch_stats"))
        val warmStore = WarmStore()
        val prefetcher = Prefetcher(coldStore, warmStore, k = 5)

        assertEquals(0, prefetcher.hits.get())
        assertEquals(0, prefetcher.misses.get())
        assertEquals(0.0, prefetcher.hitRate, 0.0001)

        prefetcher.recordHit()
        prefetcher.recordHit()
        prefetcher.recordHit()
        prefetcher.recordMiss()

        assertEquals(3, prefetcher.hits.get())
        assertEquals(1, prefetcher.misses.get())
        assertEquals(0.75, prefetcher.hitRate, 0.0001)

        prefetcher.resetStats()
        assertEquals(0, prefetcher.hits.get())
        assertEquals(0, prefetcher.misses.get())
    }

    @Test
    fun testPrefetchNextKPromotesColdToWarm() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val historyDir = tempFolder.newFolder("cold_prefetch_k")
        val coldStore = ColdStore(historyDir)
        val warmStore = WarmStore()
        val prefetcher = Prefetcher(coldStore, warmStore, k = 3, ioDispatcher = testDispatcher)

        val raw = ByteArray(100) { it.toByte() }
        val comp = PatchCodec.compress(raw)

        val patches = mutableListOf<Patch>()
        for (i in 1..6) {
            val rect = Rect()
            rect.left = 0
            rect.top = 0
            rect.right = 10
            rect.bottom = 10
            val p = Patch(
                id = i.toLong(),
                rect = rect,
                state = PatchState.COLD,
                rawLength = raw.size,
                crc32 = comp.crc32
            )
            val file = ColdFileCodec.writeColdFile(historyDir, p, comp.compressedBytes)
            p.filePath = file.absolutePath
            p.sizeBytes = file.length()
            coldStore.add(p)
            patches.add(p)
        }

        assertEquals(6, coldStore.count)
        assertEquals(0, warmStore.count)

        // Request prefetch of top K=3 patches in undo order (patches 6, 5, 4)
        prefetcher.prefetchNextK(patches, testScope)
        testScope.advanceUntilIdle()

        // Patches 6, 5, 4 should now be promoted to WARM in warmStore
        assertEquals(3, warmStore.count)
        assertEquals(3, coldStore.count)

        assertEquals(PatchState.WARM, patches[5].state)
        assertEquals(PatchState.WARM, patches[4].state)
        assertEquals(PatchState.WARM, patches[3].state)

        assertNotNull(patches[5].compressedBytes)
        assertNotNull(patches[4].compressedBytes)
        assertNotNull(patches[3].compressedBytes)

        // Patches 1, 2, 3 should remain COLD
        assertEquals(PatchState.COLD, patches[0].state)
        assertEquals(PatchState.COLD, patches[1].state)
        assertEquals(PatchState.COLD, patches[2].state)
    }

    @Test
    fun testPrefetchDisabledDoesNotPromote() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val historyDir = tempFolder.newFolder("cold_prefetch_disabled")
        val coldStore = ColdStore(historyDir)
        val warmStore = WarmStore()
        val prefetcher = Prefetcher(coldStore, warmStore, k = 3, ioDispatcher = testDispatcher)
        prefetcher.isEnabled = false

        val raw = ByteArray(50) { 1 }
        val comp = PatchCodec.compress(raw)

        val rect = Rect()
        rect.left = 0
        rect.top = 0
        rect.right = 5
        rect.bottom = 5
        val p = Patch(id = 1L, rect = rect, state = PatchState.COLD, rawLength = raw.size, crc32 = comp.crc32)
        val file = ColdFileCodec.writeColdFile(historyDir, p, comp.compressedBytes)
        p.filePath = file.absolutePath
        p.sizeBytes = file.length()
        coldStore.add(p)

        prefetcher.prefetchNextK(listOf(p), testScope)
        testScope.advanceUntilIdle()

        assertEquals(0, warmStore.count)
        assertEquals(1, coldStore.count)
        assertEquals(PatchState.COLD, p.state)
    }
}

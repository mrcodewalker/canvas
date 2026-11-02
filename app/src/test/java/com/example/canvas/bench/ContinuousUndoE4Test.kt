package com.example.canvas.bench

import android.graphics.Rect
import com.example.canvas.core.Patch
import com.example.canvas.core.PatchCodec
import com.example.canvas.core.PatchState
import com.example.canvas.history.ColdFileCodec
import com.example.canvas.history.ColdStore
import com.example.canvas.history.Prefetcher
import com.example.canvas.history.WarmStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ContinuousUndoE4Test {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testContinuousUndoWithPrefetchHighHitRate() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val historyDir = tempFolder.newFolder("history_e4")
        val coldStore = ColdStore(historyDir)
        val warmStore = WarmStore()
        val prefetcher = Prefetcher(coldStore, warmStore, k = 5, ioDispatcher = testDispatcher)

        val strokeCount = 50
        val raw = ByteArray(200) { (it % 20).toByte() }
        val comp = PatchCodec.compress(raw)

        val undoStack = ArrayDeque<Patch>()

        // Populate 50 patches initially in ColdStore
        for (i in 1..strokeCount) {
            val rect = Rect()
            rect.left = 0
            rect.top = 0
            rect.right = 10
            rect.bottom = 10

            val patch = Patch(
                id = i.toLong(),
                rect = rect,
                state = PatchState.COLD,
                rawLength = raw.size,
                crc32 = comp.crc32
            )
            val file = ColdFileCodec.writeColdFile(historyDir, patch, comp.compressedBytes)
            patch.filePath = file.absolutePath
            patch.sizeBytes = file.length()
            coldStore.add(patch)
            undoStack.addLast(patch)
        }

        assertEquals(50, coldStore.count)
        assertEquals(0, warmStore.count)

        // Simulate 50 continuous undos
        while (undoStack.isNotEmpty()) {
            val patchToUndo = undoStack.removeLast()

            // Check hit vs miss
            if (patchToUndo.state == PatchState.WARM) {
                prefetcher.recordHit()
                warmStore.remove(patchToUndo)
            } else {
                prefetcher.recordMiss()
                coldStore.remove(patchToUndo)
            }

            // Trigger prefetch for next K=5 patches in undo order
            prefetcher.prefetchNextK(undoStack.toList(), testScope)
            testScope.advanceUntilIdle() // Simulates async prefetch completing before next user undo
        }

        // Out of 50 undos, only the first batch had misses; subsequent batches were prefetched!
        assertTrue("Prefetch hits (${prefetcher.hits.get()}) should be > 35 out of 50", prefetcher.hits.get() >= 35)
        assertTrue("Prefetch hit rate (${prefetcher.hitRate}) should be >= 0.70", prefetcher.hitRate >= 0.70)
    }

    @Test
    fun testContinuousUndoWithoutPrefetchZeroHitRate() {
        val coldStore = ColdStore(tempFolder.newFolder("history_e4_no_prefetch"))
        val warmStore = WarmStore()
        val prefetcher = Prefetcher(coldStore, warmStore, k = 5)
        prefetcher.isEnabled = false

        for (i in 1..20) {
            prefetcher.recordMiss()
        }

        assertEquals(0, prefetcher.hits.get())
        assertEquals(20, prefetcher.misses.get())
        assertEquals(0.0, prefetcher.hitRate, 0.0001)
    }
}

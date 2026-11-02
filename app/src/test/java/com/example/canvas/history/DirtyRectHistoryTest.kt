package com.example.canvas.history

import android.graphics.Rect
import com.example.canvas.core.Patch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DirtyRectHistoryTest {

    private fun createDirtyPatch(
        id: Long,
        left: Int,
        top: Int,
        width: Int,
        height: Int
    ): Patch {
        val rect = Rect()
        rect.left = left
        rect.top = top
        rect.right = left + width
        rect.bottom = top + height
        val sizeBytes = width.toLong() * height.toLong() * 4L
        return Patch(
            id = id,
            rect = rect,
            bitmap = null,
            sizeBytes = sizeBytes
        )
    }

    @Test
    fun testInitialState() {
        val history = DirtyRectHistory()
        assertEquals(HistoryMode.DIRTY_RECT, history.mode)
        val stats = history.stats()
        assertEquals(0, stats.totalSteps)
        assertEquals(0, stats.redoSteps)
        assertEquals(0L, stats.hotBytes)
    }

    @Test
    fun testCommitAndMemorySavingsVersusNaive() = runTest {
        val dirtyHistory = DirtyRectHistory()
        val naiveHistory = NaiveHistory()

        // 5 localized strokes covering 150x150 regions
        val patchWidth = 150
        val patchHeight = 150
        val patchSize = patchWidth.toLong() * patchHeight.toLong() * 4L // 90,000 bytes (~88 KiB)

        for (i in 1..5) {
            dirtyHistory.commit(createDirtyPatch(i.toLong(), 100 * i, 100 * i, patchWidth, patchHeight))
            naiveHistory.commit(
                Patch(
                    id = i.toLong(),
                    rect = Rect(0, 0, 2048, 2048),
                    bitmap = null,
                    sizeBytes = 2048L * 2048L * 4L // 16 MiB
                )
            )
        }

        val dirtyStats = dirtyHistory.stats()
        val naiveStats = naiveHistory.stats()

        assertEquals(5, dirtyStats.totalSteps)
        assertEquals(5 * patchSize, dirtyStats.hotBytes) // 450,000 bytes

        assertEquals(5, naiveStats.totalSteps)
        assertEquals(5 * 16L * 1024L * 1024L, naiveStats.hotBytes) // 83,886,080 bytes

        // DirtyRect uses vastly less memory than Naive (over 180x less)
        assertTrue(dirtyStats.hotBytes * 150 < naiveStats.hotBytes)
    }

    @Test
    fun testClear() = runTest {
        val history = DirtyRectHistory()
        history.commit(createDirtyPatch(1L, 10, 10, 50, 50))
        history.commit(createDirtyPatch(2L, 60, 60, 50, 50))

        assertEquals(2, history.stats().totalSteps)
        history.clear()

        val stats = history.stats()
        assertEquals(0, stats.totalSteps)
        assertEquals(0, stats.redoSteps)
        assertEquals(0L, stats.hotBytes)
    }

    @Test
    fun testRedoClearedOnNewCommit() = runTest {
        val history = DirtyRectHistory(maxRedoSteps = 5)
        history.commit(createDirtyPatch(1L, 0, 0, 100, 100))
        history.commit(createDirtyPatch(2L, 50, 50, 100, 100))
        assertEquals(2, history.stats().totalSteps)

        // Committing step 3 clears redo
        history.commit(createDirtyPatch(3L, 100, 100, 100, 100))
        assertEquals(3, history.stats().totalSteps)
        assertEquals(0, history.stats().redoSteps)
    }
}

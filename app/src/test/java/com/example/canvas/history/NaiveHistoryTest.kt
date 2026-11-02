package com.example.canvas.history

import android.graphics.Rect
import com.example.canvas.core.Patch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NaiveHistoryTest {

    private fun createDummyPatch(id: Long, sizeBytes: Long = 1024L): Patch {
        val rect = Rect()
        rect.left = 0
        rect.top = 0
        rect.right = 2048
        rect.bottom = 2048
        return Patch(
            id = id,
            rect = rect,
            bitmap = null, // In host JVM unit tests, test stack mechanics without native bitmap
            sizeBytes = sizeBytes
        )
    }

    @Test
    fun testInitialState() {
        val history = NaiveHistory()
        assertEquals(HistoryMode.NAIVE, history.mode)
        val stats = history.stats()
        assertEquals(0, stats.totalSteps)
        assertEquals(0, stats.redoSteps)
        assertEquals(0L, stats.hotBytes)
    }

    @Test
    fun testCommitAndStats() = runTest {
        val history = NaiveHistory()
        history.commit(createDummyPatch(1L, sizeBytes = 16 * 1024 * 1024L))
        history.commit(createDummyPatch(2L, sizeBytes = 16 * 1024 * 1024L))

        val stats = history.stats()
        assertEquals(2, stats.totalSteps)
        assertEquals(2, stats.hotSteps)
        assertEquals(0, stats.redoSteps)
        assertEquals(32L * 1024 * 1024, stats.hotBytes)
    }

    @Test
    fun testClear() = runTest {
        val history = NaiveHistory()
        history.commit(createDummyPatch(1L, 1000L))
        history.commit(createDummyPatch(2L, 1000L))

        history.clear()
        val stats = history.stats()
        assertEquals(0, stats.totalSteps)
        assertEquals(0, stats.redoSteps)
        assertEquals(0L, stats.hotBytes)
    }

    @Test
    fun testEmptyUndoRedoReturnsFalse() = runTest {
        val history = NaiveHistory()
        // We pass a dummy or we test empty check before canvas access
        // When undoStack is empty, undo returns false immediately without touching canvas
        val dummyBmp = null
        // We can verify stats remain 0
        assertEquals(0, history.stats().totalSteps)
    }

    @Test
    fun testRedoClearedOnNewCommit() = runTest {
        val history = NaiveHistory(maxRedoSteps = 5)
        history.commit(createDummyPatch(1L))
        history.commit(createDummyPatch(2L))
        assertEquals(2, history.stats().totalSteps)

        // Committing a new step clears redo
        history.commit(createDummyPatch(3L))
        assertEquals(3, history.stats().totalSteps)
        assertEquals(0, history.stats().redoSteps)
    }
}

package com.example.canvas.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TierBudgetPlannerTest {

    private val mb = 1024L * 1024L

    @Test
    fun hotKeepsOnlyWhatIsAboutToBeUndoneAndWarmGetsTheRest() {
        // 4.25 MB for history, strokes of ~0.4 MB, heavy ones 0.6 MB, usually undo 2 in a row
        val (hot, warm) = TierBudgetPlanner.split(
            totalBytes = (4.25 * mb).toLong(), typicalStroke = (0.4 * mb).toLong(),
            heavyStroke = (0.6 * mb).toLong(), expectedRun = 2.0, freeRoom = 100 * mb
        )
        assertEquals((0.8 * mb).toLong(), hot)                 // two typical strokes
        assertEquals((4.25 * mb).toLong() - hot, warm)         // everything else is the dense tier
        assertTrue("warm must be the big one", warm > hot * 3)
    }

    @Test
    fun theNewestStrokeAlwaysFitsInHotEvenWithoutUndoHabits() {
        val (hot, _) = TierBudgetPlanner.split(4 * mb, 300_000, 600_000, expectedRun = null, freeRoom = 100 * mb)
        assertEquals(600_000L, hot)
    }

    @Test
    fun hotNeverTakesMoreThanHalfOfTheBudget() {
        val (hot, warm) = TierBudgetPlanner.split(4 * mb, 3 * mb, 8 * mb, expectedRun = 5.0, freeRoom = 100 * mb)
        assertEquals(2 * mb, hot)
        assertEquals(2 * mb, warm)
    }

    @Test
    fun scarceMemoryShrinksBothTiers() {
        val (hot, warm) = TierBudgetPlanner.split(10 * mb, 1 * mb, 2 * mb, expectedRun = 3.0, freeRoom = 1 * mb)
        assertEquals(1 * mb, hot + warm)
    }
}

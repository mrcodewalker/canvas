package com.example.canvas.history

import android.graphics.Rect
import com.example.canvas.core.Patch
import com.example.canvas.core.PatchState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WarmVictimTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun rect(): Rect {
        val r = Rect()
        r.left = 0; r.top = 0; r.right = 10; r.bottom = 10
        return r
    }

    private fun warm(id: Long, bytes: Int) = Patch(
        id = id, rect = rect(), state = PatchState.WARM,
        compressedBytes = ByteArray(bytes), sizeBytes = bytes.toLong(), rawLength = bytes * 10
    )

    private fun policy(
        warm: WarmStore,
        valueAware: Boolean,
        depths: Map<Long, Int>,
        overhead: Long
    ): TierPolicy {
        val config = TierConfig(
            hotBudgetBytes = Long.MAX_VALUE / 4, warmBudgetBytes = 0L, infiniteUndo = true,
            coldMaxBytes = Long.MAX_VALUE / 4, coldMaxSteps = Int.MAX_VALUE,
            adaptiveCompEnabled = false, valueAwareVictims = valueAware, autoBudgets = false
        )
        return TierPolicy(
            config, HotStore(), warm, ColdStore(tempFolder.newFolder()),
            undoDepths = { depths },
            reachFunction = { { d -> 1.0 / (1 + d) } },        // the deeper, the less likely an undo gets there
            patchOverheadBytes = { overhead }
        )
    }

    @Test
    fun byAgeTheOldestGoesFirst() {
        val w = WarmStore()
        val oldSmall = warm(1, 1_000)
        val newBig = warm(2, 100_000)
        w.add(oldSmall); w.add(newBig)
        val p = policy(w, valueAware = false, depths = mapOf(1L to 10, 2L to 8), overhead = 4096)
        assertSame(oldSmall, p.pickWarmVictim())
    }

    @Test
    fun byValueABigStepGoesBeforeAnOlderSmallOne() {
        // the old step is tiny: bringing it back costs mostly the fixed price of a file, so it is worth keeping;
        // the newer one is big: cheap per byte to read, and it holds a lot of RAM
        val w = WarmStore()
        val oldSmall = warm(1, 1_000)
        val newBig = warm(2, 100_000)
        w.add(oldSmall); w.add(newBig)
        val p = policy(w, valueAware = true, depths = mapOf(1L to 10, 2L to 8), overhead = 4096)
        assertSame(newBig, p.pickWarmVictim())
    }

    @Test
    fun withNoFixedCostItIsTheDeepestStepLikeAge() {
        val w = WarmStore()
        val a = warm(1, 50_000)
        val b = warm(2, 1_000)
        w.add(a); w.add(b)
        val p = policy(w, valueAware = true, depths = mapOf(1L to 9, 2L to 3), overhead = 0)
        assertSame(a, p.pickWarmVictim())      // deeper and same cost per byte
    }

    @Test
    fun equalStepsKeepTheOlderFirst() {
        val w = WarmStore()
        val a = warm(1, 5_000)
        val b = warm(2, 5_000)
        w.add(a); w.add(b)
        val p = policy(w, valueAware = true, depths = mapOf(1L to 4, 2L to 4), overhead = 4096)
        assertSame(a, p.pickWarmVictim())
    }

    @Test
    fun theChosenStepIsWhatMovesDownWhenWarmIsOverBudget() = kotlinx.coroutines.test.runTest {
        val w = WarmStore()
        val oldSmall = warm(1, 1_000)
        val newBig = warm(2, 100_000)
        w.add(oldSmall); w.add(newBig)
        val p = policy(w, valueAware = true, depths = mapOf(1L to 10, 2L to 8), overhead = 4096)
        p.config = p.config.copy(warmBudgetBytes = 50_000)     // only one has to go
        p.enforceBudgets(effectiveHotBudget = Long.MAX_VALUE / 4, effectiveWarmBudget = 50_000)
        assertEquals(PatchState.COLD, newBig.state)
        assertEquals(PatchState.WARM, oldSmall.state)
    }
}

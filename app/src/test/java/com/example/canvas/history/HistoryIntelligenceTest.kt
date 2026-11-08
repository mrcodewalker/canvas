package com.example.canvas.history

import android.graphics.Rect
import com.example.canvas.core.Patch
import com.example.canvas.core.Stroke
import com.example.canvas.core.StrokeOp
import com.example.canvas.core.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HistoryIntelligenceTest {

    private fun rect(l: Int, t: Int, r: Int, b: Int): Rect {
        val x = Rect()
        x.left = l; x.top = t; x.right = r; x.bottom = b
        return x
    }

    private fun patch(id: Long, r: Rect, group: Long = 0L, op: StrokeOp? = null) =
        Patch(id = id, rect = r, groupId = group, op = op)

    // ------------------------------------------------------------------ redo with merge

    @Test
    fun newDrawingKeepsReplayableRedoStepsAndDropsTheRest() {
        val op = StrokeOp(stroke = Stroke(0, 4f, mutableListOf(StrokePoint(1f, 1f))))
        val stack = ArrayDeque<Patch>()
        // group 1: two tiles with a replayable op, group 2: no op (pixels only), group 3: elsewhere, no op
        stack.addLast(patch(1, rect(0, 0, 50, 50), group = 1, op = op))
        stack.addLast(patch(2, rect(50, 0, 100, 50), group = 1, op = op))
        stack.addLast(patch(3, rect(0, 0, 60, 60), group = 2))
        stack.addLast(patch(4, rect(500, 500, 600, 600), group = 3))

        RedoMerge.invalidate(stack, rect(10, 10, 20, 20))   // new drawing overlaps groups 1 and 2

        assertEquals(listOf(1L, 2L, 4L), stack.map { it.id })   // 1+2 kept for replay, 3 dropped, 4 untouched
    }

    @Test
    fun aStepWithoutPixelsMustBeReplayed() {
        val noPixels = listOf(patch(1, rect(0, 0, 10, 10)))
        assertTrue(RedoMerge.needsReplay(noPixels))
    }

    @Test
    fun overlapIsGeometricAndSymmetric() {
        assertTrue(RedoMerge.overlaps(rect(0, 0, 10, 10), rect(9, 9, 20, 20)))
        assertTrue(!RedoMerge.overlaps(rect(0, 0, 10, 10), rect(10, 0, 20, 10)))
    }

    // ------------------------------------------------------------------ adaptive tile size

    private fun hollowSquare(width: Float) = Stroke(
        0, width,
        mutableListOf(
            StrokePoint(100f, 100f), StrokePoint(1100f, 100f), StrokePoint(1100f, 1100f),
            StrokePoint(100f, 1100f), StrokePoint(100f, 100f)
        )
    )

    @Test
    fun costlyPatchesFavourFewerLargerRegions() {
        val s = hollowSquare(8f)
        val cheap = s.dirtyRegionInts(2048, 2048, patchOverheadBytes = 64)
        val costly = s.dirtyRegionInts(2048, 2048, patchOverheadBytes = 4_000_000)
        assertTrue("cheap=${cheap.size} costly=${costly.size}", cheap.size > costly.size)
    }

    @Test
    fun whenPatchesAreVeryExpensiveItFallsBackToOneBox() {
        val s = hollowSquare(8f)
        assertEquals(1, s.dirtyRegionInts(2048, 2048, patchOverheadBytes = 1L shl 40).size)
    }

    @Test
    fun tileChoiceNeverUncoversAPaintedPixel() {
        val s = hollowSquare(12f)
        for (overhead in listOf(0L, 4096L, 200_000L)) {
            val regions = s.dirtyRegionInts(2048, 2048, overhead)
            val pts = s.points
            for (i in 1 until pts.size) {
                for (k in 0..100) {
                    val t = k / 100f
                    val x = (pts[i - 1].x + (pts[i].x - pts[i - 1].x) * t).toInt()
                    val y = (pts[i - 1].y + (pts[i].y - pts[i - 1].y) * t).toInt()
                    assertTrue(
                        "($x,$y) uncovered at overhead $overhead",
                        regions.any { x >= it[0] && x < it[2] && y >= it[1] && y < it[3] }
                    )
                }
            }
        }
    }

    // ------------------------------------------------------------------ budgets from habits

    @Test
    fun learnedBudgetsReplaceConfiguredOnesOnlyWhenAvailableAndEnabled() {
        val dir = File.createTempFile("hist", "").let { it.delete(); it.mkdirs(); it }
        val config = TierConfig(hotBudgetBytes = 111, warmBudgetBytes = 222, autoBudgets = true)
        val learned = TierPolicy(config, HotStore(), WarmStore(), ColdStore(dir), autoBudgets = { 1000L to 2000L })
        assertEquals(1000L, learned.hotBudget())
        assertEquals(2000L, learned.warmBudget())

        val noData = TierPolicy(config, HotStore(), WarmStore(), ColdStore(dir), autoBudgets = { null })
        assertEquals(111L, noData.hotBudget())

        val off = TierPolicy(config.copy(autoBudgets = false), HotStore(), WarmStore(), ColdStore(dir), autoBudgets = { 1000L to 2000L })
        assertEquals(111L, off.hotBudget())
        assertEquals(222L, off.warmBudget())
        dir.deleteRecursively()
    }

    @Test
    fun modelLearnsRunLengthAndStrokeWeightFromTheUser() {
        var now = 1_000_000_000L
        val m = PrefetchModel { now }
        assertNull(m.expectedRun())
        assertNull(m.medianStrokeBytes())
        repeat(10) {
            repeat(3) { m.recordCommit(true, 40_000); m.recordCommit(false, 40_000); now += 300_000_000 }
            repeat(4) { m.recordUndo(); now += 10_000_000 }
            m.recordRunBreak()
        }
        assertEquals(4.0, m.expectedRun()!!, 1e-9)
        assertEquals(4, m.deepRun())
        assertEquals(80_000L, m.medianStrokeBytes())          // two tiles of 40 KB per stroke
        assertNotNull(m.heavyStrokeBytes())
    }
}

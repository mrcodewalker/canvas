package com.example.canvas.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrefetchModelTest {

    private class FakeClock {
        var now = 1_000_000_000L
        fun advanceMs(ms: Double) { now += (ms * 1_000_000).toLong() }
        val fn: () -> Long = { now }
    }

    private fun ms(v: Double) = (v * 1_000_000).toLong()

    private fun undoRun(m: PrefetchModel, c: FakeClock, undos: Int, gapMs: Double) {
        repeat(undos) { m.recordUndo(); c.advanceMs(gapMs) }
    }

    private fun cold(bytes: Long = 10_000) = GroupInput(Stage.COLD, coldBytes = bytes, decodeBytes = bytes * 4)
    private fun warm(raw: Long = 40_000) = GroupInput(Stage.WARM, 0, raw)
    private fun bitmap() = GroupInput(Stage.BITMAP, 0, 0)

    private fun seqOf(vararg g: GroupInput) = g.asSequence()
    private fun colds(n: Int) = (1..n).asSequence().map { cold() }

    @Test
    fun exploresWithOnlyTheNextGroupWhenNothingIsKnown() {
        val m = PrefetchModel(FakeClock().fn)
        val t = m.plan(colds(20), Long.MAX_VALUE, allowBitmap = false)
        assertEquals(listOf(Stage.WARM), t)
        // with RAM to spare it also takes the first decode sample by decompressing that group ahead
        assertEquals(listOf(Stage.BITMAP), PrefetchModel(FakeClock().fn).plan(colds(20), Long.MAX_VALUE))
    }

    @Test
    fun fastDiskSlowUserPreparesOnlyTheNextGroup() {
        val c = FakeClock()
        val m = PrefetchModel(c.fn)
        undoRun(m, c, 8, 200.0)
        m.recordFetch(10_000, ms(2.0)); m.recordFetch(20_000, ms(4.0))
        m.recordDecode(40_000, ms(1.0)); m.recordDecode(80_000, ms(2.0))
        val t = m.plan(colds(30), Long.MAX_VALUE)
        assertEquals(1, t.count { it != Stage.COLD })
    }

    @Test
    fun spareHotRamTurnsTheNextGroupIntoABitmap() {
        val c = FakeClock()
        val m = PrefetchModel(c.fn)
        undoRun(m, c, 8, 200.0)
        m.recordFetch(10_000, ms(2.0)); m.recordFetch(20_000, ms(4.0))
        m.recordDecode(40_000, ms(30.0)); m.recordDecode(80_000, ms(60.0))
        val noRam = m.plan(colds(5), ramBudget = 10_000)          // only room for the compressed bytes
        val withRam = m.plan(colds(5), ramBudget = 10_000_000)
        assertEquals(Stage.WARM, noRam[0])
        assertEquals(Stage.BITMAP, withRam[0])
    }

    @Test
    fun alreadyWarmGroupIsDecodedWhenWorthIt() {
        val c = FakeClock()
        val m = PrefetchModel(c.fn)
        undoRun(m, c, 8, 100.0)
        m.recordDecode(40_000, ms(25.0)); m.recordDecode(80_000, ms(50.0))
        val t = m.plan(seqOf(warm(), bitmap()), 10_000_000)
        assertEquals(Stage.BITMAP, t[0])
    }

    @Test
    fun slowDiskFastUserPipelinesDeeperThanOneGroup() {
        val c = FakeClock()
        val m = PrefetchModel(c.fn)
        undoRun(m, c, 8, 10.0)
        m.recordFetch(10_000, ms(11.0)); m.recordFetch(20_000, ms(22.0))
        val ready = m.plan(colds(30), Long.MAX_VALUE).count { it != Stage.COLD }
        assertTrue("prepared=$ready", ready > 2)
    }

    @Test
    fun hopelesslySlowDiskDoesNotSpeculateDeep() {
        val c = FakeClock()
        val m = PrefetchModel(c.fn)
        undoRun(m, c, 8, 10.0)
        m.recordFetch(10_000, ms(50.0)); m.recordFetch(20_000, ms(100.0))
        val ready = m.plan(colds(30), Long.MAX_VALUE).count { it != Stage.COLD }
        assertEquals(1, ready)
    }

    @Test
    fun ramBudgetCapsHowMuchIsRead() {
        val c = FakeClock()
        val m = PrefetchModel(c.fn)
        undoRun(m, c, 8, 10.0)
        m.recordFetch(10_000, ms(11.0)); m.recordFetch(20_000, ms(22.0))
        val unlimited = m.plan(colds(30), Long.MAX_VALUE).count { it != Stage.COLD }
        val tight = m.plan(colds(30), ramBudget = 15_000).count { it != Stage.COLD }
        assertTrue("unlimited=$unlimited tight=$tight", unlimited > tight)
        assertEquals(1, m.plan(colds(30), 0).count { it != Stage.COLD })
    }

    @Test
    fun drawingBehaviourChangesHowDeepItGoes() {
        // User always undoes exactly what they just drew: 3 strokes -> 3 undos, then draws 3 again.
        fun train(m: PrefetchModel, c: FakeClock) {
            repeat(12) {
                repeat(3) { m.recordCommit(true, 40_000); c.advanceMs(300.0) }
                undoRun(m, c, 3, 10.0)
                m.recordRunBreak()
            }
            m.recordFetch(10_000, ms(11.0)); m.recordFetch(20_000, ms(22.0))
        }
        val trained = FakeClock().let { c ->
            val m = PrefetchModel(c.fn); train(m, c)
            repeat(3) { m.recordCommit(true, 40_000); c.advanceMs(300.0) }
            undoRun(m, c, 1, 10.0)   // first undo of a run that historically lasts 3
            m.plan(colds(30), ramBudget = 30_000).count { it != Stage.COLD }
        }
        val untrained = FakeClock().let { c ->
            val m = PrefetchModel(c.fn)
            undoRun(m, c, 8, 10.0)
            m.recordFetch(10_000, ms(11.0)); m.recordFetch(20_000, ms(22.0))
            m.plan(colds(30), ramBudget = 30_000).count { it != Stage.COLD }
        }
        assertTrue("trained=$trained untrained=$untrained", trained < untrained)
        assertTrue("trained=$trained", trained >= 1)
    }

    @Test
    fun heavyStrokesFewerFitLightStrokesMoreFit() {
        // Same RAM budget and same timing; only the weight of the strokes differs
        fun prepared(strokeBytes: Long): Int {
            val c = FakeClock()
            val m = PrefetchModel(c.fn)
            undoRun(m, c, 8, 10.0)
            // a read takes 1.1 cadences whatever the size, so only the RAM budget limits how many fit
            m.recordFetch(strokeBytes, ms(11.0))
            m.recordFetch(strokeBytes * 2, ms(11.0))
            val groups = (1..40).asSequence().map { GroupInput(Stage.COLD, strokeBytes, strokeBytes * 4) }
            return m.plan(groups, ramBudget = 200_000).count { it != Stage.COLD }
        }
        val light = prepared(5_000)
        val heavy = prepared(50_000)
        assertTrue("light=$light heavy=$heavy", light > heavy)
        assertTrue(heavy >= 1)
    }

    @Test
    fun memoryReserveFollowsHowHeavyTheUsersStrokesAre() {
        val c = FakeClock()
        val m = PrefetchModel(c.fn)
        assertEquals(0L, m.memoryReserveBytes())
        repeat(20) { m.recordCommit(true, 100_000) }
        val light = m.memoryReserveBytes()
        repeat(40) { m.recordCommit(true, 4_000_000) }
        val heavy = m.memoryReserveBytes()
        assertTrue("light=$light heavy=$heavy", heavy > light * 5)
    }

    @Test
    fun noEstimatesBeforeSamples() {
        val m = PrefetchModel(FakeClock().fn)
        assertNull(m.predictFetchNs(1000))
        assertNull(m.cadenceNs())
        m.recordFetch(1000, 5000)
        assertNotNull(m.predictFetchNs(1000))
    }

    @Test
    fun p2QuantileTracksMedian() {
        val q = P2Quantile(0.5)
        val rnd = java.util.Random(1)
        val xs = DoubleArray(5000) { rnd.nextDouble() * 100 }
        xs.forEach { q.add(it) }
        xs.sort()
        assertEquals(xs[2500], q.value()!!, 3.0)
    }
}

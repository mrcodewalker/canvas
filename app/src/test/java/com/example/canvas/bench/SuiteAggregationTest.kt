package com.example.canvas.bench

import com.example.canvas.history.HistoryMode
import com.example.canvas.history.HistoryStats
import com.example.canvas.metrics.BenchmarkStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuiteAggregationTest {

    private val suite = ExperimentSuites.byId("prefetch")!!      // two variants, undo-latency metrics

    private fun run(variant: Int, undoMs: Long, status: BenchmarkStatus = BenchmarkStatus.SUCCESS) = VariantResult(
        variant = suite.variants[variant], status = status, errorMessage = null, drawnSteps = 10, durationMs = undoMs * 20,
        drawLatenciesMs = List(10) { 5L }, undoLatenciesMs = List(20) { undoMs }, stats = HistoryStats(),
        peakHotMb = 1.0, peakWarmMb = 1.0, peakColdMb = 1.0, peakJavaMb = 10.0, peakNativeMb = 5.0
    )

    private fun runs(base: List<Long>, hero: List<Long>, heroStatus: BenchmarkStatus = BenchmarkStatus.SUCCESS) =
        base.indices.map { i -> listOf(run(0, base[i]), run(1, hero[i], if (i == 0) heroStatus else BenchmarkStatus.SUCCESS)) }

    private val p50 = suite.metrics.first { it.label == "Hoàn tác p50" }

    @Test
    fun theMedianIgnoresOneBadRun() {
        val agg = SuiteAggregation.aggregate(suite, runs(listOf(50, 52, 51, 49, 300), listOf(30, 31, 29, 32, 33)))
        assertEquals(51.0, agg[0].valueOf(p50), 1e-9)        // not dragged to ~90 by the 300
        assertEquals(31.0, agg[1].valueOf(p50), 1e-9)
        val st = agg[0].stat.getValue(p50.label)
        assertEquals(49.0, st.min, 1e-9)
        assertEquals(300.0, st.max, 1e-9)
        assertEquals(5, st.n)
    }

    @Test
    fun separatedRunsAreCalledAGain() {
        val agg = SuiteAggregation.aggregate(suite, runs(listOf(50, 52, 51, 49, 53), listOf(30, 31, 29, 32, 33)))
        val v = ExperimentSuites.verdict(suite, agg)
        assertTrue(v.gains.any { it.contains("Hoàn tác p50") })
        assertTrue(v.unclear.none { it.contains("Hoàn tác p50") })
        assertEquals(5, v.runs)
    }

    @Test
    fun overlappingRunsAreNotCalledADifference() {
        // medians 50 vs 44 look 12% apart, but the runs of the two overlap
        val agg = SuiteAggregation.aggregate(suite, runs(listOf(40, 50, 60, 45, 55), listOf(35, 44, 52, 38, 48)))
        val v = ExperimentSuites.verdict(suite, agg)
        assertTrue(v.gains.none { it.contains("Hoàn tác p50") })
        assertTrue(v.unclear.any { it.contains("Hoàn tác p50") })
        assertTrue(ExperimentSuites.rangesOverlap(p50, agg[0], agg[1]))
    }

    @Test
    fun aFailedRunMakesTheVariantFailedWhateverTheMedian() {
        val agg = SuiteAggregation.aggregate(suite, runs(listOf(50, 50, 50), listOf(30, 30, 30), BenchmarkStatus.FAILED))
        assertEquals(BenchmarkStatus.FAILED, agg[1].status)
        assertEquals(BenchmarkStatus.SUCCESS, agg[0].status)
    }

    @Test
    fun withASingleRunTheValueIsTheMeasurement() {
        val r = run(0, 42)
        assertEquals(42.0, r.valueOf(p50), 1e-9)
        assertTrue(r.stat.isEmpty())
        assertTrue(!ExperimentSuites.rangesOverlap(p50, r, run(1, 30)))
    }

    @Test
    fun theModeIsKept() {
        assertEquals(HistoryMode.TIERED, run(0, 10).variant.mode)
    }
}

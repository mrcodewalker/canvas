package com.example.canvas.bench

import com.example.canvas.history.HistoryMode
import com.example.canvas.history.HistoryStats
import com.example.canvas.metrics.BenchmarkStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExperimentSuiteTest {

    private fun result(label: String, undo: List<Long>, hotMb: Double, status: BenchmarkStatus = BenchmarkStatus.SUCCESS, drawn: Int = 10) =
        VariantResult(
            variant = SuiteVariant(label, "", HistoryMode.TIERED),
            status = status, errorMessage = null, drawnSteps = drawn, durationMs = 1000,
            drawLatenciesMs = List(drawn) { 5L }, undoLatenciesMs = undo, stats = HistoryStats(),
            peakHotMb = hotMb, peakWarmMb = 0.0, peakColdMb = 0.0, peakJavaMb = 10.0, peakNativeMb = 5.0
        )

    @Test
    fun hollowFrameHasHugeBoundsButTilesCoverAFractionOfThem() {
        val drawer = ScriptedDrawer(seed = 7)
        repeat(10) {
            val stroke = drawer.nextStroke(StrokeType.HOLLOW)
            val b = stroke.boundsInts(2048, 2048)
            val boxArea = (b[2] - b[0]).toLong() * (b[3] - b[1])
            val tileArea = stroke.dirtyRegionInts(2048, 2048).sumOf { (it[2] - it[0]).toLong() * (it[3] - it[1]) }
            assertTrue("bounding box must be big", boxArea > 1_000_000L)
            assertTrue("tiles must cover far less than the box: $tileArea of $boxArea", tileArea < boxArea / 2)
        }
    }

    @Test
    fun verdictNamesGainsAndCostsFromTheNumbers() {
        val suite = ExperimentSuites.byId("prefetch")!!
        val base = result("A", List(20) { 80L }, 10.0)
        val hero = result("B", List(20) { 40L }, 10.0)
        val v = ExperimentSuites.verdict(suite, listOf(base, hero))
        assertTrue(v.gains.any { it.contains("Hoàn tác p50") })
        assertTrue(v.costs.isEmpty())
    }

    @Test
    fun verdictIgnoresDifferencesUnderFivePercent() {
        val suite = ExperimentSuites.byId("prefetch")!!
        val v = ExperimentSuites.verdict(suite, listOf(result("A", List(20) { 100L }, 10.0), result("B", List(20) { 102L }, 10.0)))
        assertTrue(v.gains.isEmpty() && v.costs.isEmpty())
        assertTrue(v.headline.contains("ngang nhau"))
    }

    @Test
    fun verdictReportsARegressionHonestly() {
        val suite = ExperimentSuites.byId("prefetch")!!
        val v = ExperimentSuites.verdict(suite, listOf(result("A", List(20) { 40L }, 10.0), result("B", List(20) { 90L }, 10.0)))
        assertTrue(v.costs.isNotEmpty())
        assertTrue(v.headline.contains("không tốt hơn"))
    }

    @Test
    fun verdictMentionsVariantsThatRanOutOfMemory() {
        val suite = ExperimentSuites.byId("tiles")!!
        val v = ExperimentSuites.verdict(suite, listOf(
            result("Naive", emptyList(), 96.0, BenchmarkStatus.OOM_CRASH, drawn = 6),
            result("Tiles", emptyList(), 40.0, drawn = 30)
        ))
        assertTrue(v.headline.contains("Naive") && v.headline.contains("30"))
    }

    @Test
    fun everySuiteComparesAtLeastTwoVariantsAndHasMetrics() {
        for (s in ExperimentSuites.all) {
            assertTrue(s.id, s.variants.size >= 2 && s.metrics.isNotEmpty())
            assertEquals(s.variants.size - 1, s.heroIndex)
        }
    }
}

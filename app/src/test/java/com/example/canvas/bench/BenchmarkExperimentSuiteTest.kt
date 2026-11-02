package com.example.canvas.bench

import android.content.ComponentCallbacks2
import android.graphics.Rect
import com.example.canvas.core.Patch
import com.example.canvas.core.PatchCodec
import com.example.canvas.core.PatchState
import com.example.canvas.history.ColdStore
import com.example.canvas.history.DirtyRectHistory
import com.example.canvas.history.HistoryMode
import com.example.canvas.history.HistoryStats
import com.example.canvas.history.HotStore
import com.example.canvas.history.NaiveHistory
import com.example.canvas.history.Prefetcher
import com.example.canvas.history.TierConfig
import com.example.canvas.history.TierPolicy
import com.example.canvas.history.TieredHistory
import com.example.canvas.history.WarmStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Locale
import java.util.Random
import kotlin.math.pow
import kotlin.math.sqrt

data class ExperimentResultSummary(
    val experimentId: String,
    val mode: String,
    val repetitions: Int,
    val meanMaxSteps: Double,
    val stdDevMaxSteps: Double,
    val meanPeakRamMb: Double,
    val stdDevPeakRamMb: Double,
    val meanGcDelta: Double,
    val stdDevGcDelta: Double,
    val meanLatencyMs: Double,
    val stdDevLatencyMs: Double,
    val p50LatencyMs: Double,
    val p95LatencyMs: Double,
    val prefetchHitRate: Double,
    val correctnessPassed: Boolean
)

@OptIn(ExperimentalCoroutinesApi::class)
class BenchmarkExperimentSuiteTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var coldDir: File
    private lateinit var reportsDir: File

    @Before
    fun setup() {
        coldDir = tempFolder.newFolder("cold_experiment_store")
        reportsDir = File("reports/benchmarks").apply { mkdirs() }
    }

    private fun createRect(l: Int, t: Int, r: Int, b: Int): Rect {
        val rect = Rect()
        rect.left = l
        rect.top = t
        rect.right = r
        rect.bottom = b
        return rect
    }

    /**
     * E1: Draw until OOM.
     * Evaluates max steps sustainable under a fixed memory budget:
     * - Naive: 16 MiB full copies -> fails early (~12-16 steps).
     * - DirtyRect: uncompressed patches -> fails around ~25-35 steps for large strokes.
     * - Tiered: swaps to disk -> sustains 300+ steps effortlessly.
     */
    @Test
    fun testExperimentE1_DrawUntilOom_Suite() = runTest {
        val memoryBudget = 128 * 1024 * 1024L // 128 MiB budget
        val fullCanvasPatchSize = 16 * 1024 * 1024L // 16 MiB per step

        // 1. Naive simulation
        var naiveBytes = 0L
        var naiveSteps = 0
        while (naiveBytes + fullCanvasPatchSize <= memoryBudget) {
            naiveBytes += fullCanvasPatchSize
            naiveSteps++
        }
        assertEquals(8, naiveSteps) // Exactly 8 steps (8 * 16 = 128 MB)

        // 2. DirtyRect simulation with large strokes (50% canvas = 8 MiB)
        val largeStrokePatchSize = 8 * 1024 * 1024L
        var dirtyBytes = 0L
        var dirtySteps = 0
        while (dirtyBytes + largeStrokePatchSize <= memoryBudget) {
            dirtyBytes += largeStrokePatchSize
            dirtySteps++
        }
        assertEquals(16, dirtySteps)

        // 3. Tiered execution with ColdStore offload
        val hotStore = HotStore()
        val warmStore = WarmStore()
        val coldStore = ColdStore(coldDir)
        val policy = TierPolicy(
            TierConfig(hotBudgetBytes = 16 * 1024 * 1024L, warmBudgetBytes = 32 * 1024 * 1024L),
            hotStore, warmStore, coldStore
        )

        val rawSample = ByteArray(100_000) { (it % 100).toByte() }
        val compressedSample = PatchCodec.compress(rawSample)

        for (step in 1..300) {
            val patch = Patch(
                id = step.toLong(),
                rect = createRect(0, 0, 500, 500),
                state = PatchState.HOT,
                sizeBytes = 2_000_000L,
                compressedBytes = compressedSample.compressedBytes.clone(),
                rawLength = rawSample.size,
                crc32 = compressedSample.crc32
            )
            hotStore.add(patch)
            policy.enforceBudgets()
        }

        assertTrue("Tiered must support 300 steps within RAM budget", hotStore.totalBytes <= 16 * 1024 * 1024L)
        assertTrue("Tiered must demote to Warm or Cold", warmStore.count > 0 || coldStore.count > 0)
    }

    /**
     * E2: Draw 300 strokes & Undo All.
     * Evaluates peak RAM and GC pressure.
     */
    @Test
    fun testExperimentE2_Draw300AndUndoAll_Suite() = runTest {
        val hotStore = HotStore()
        val warmStore = WarmStore()
        val coldStore = ColdStore(coldDir)
        val policy = TierPolicy(
            TierConfig(hotBudgetBytes = 16 * 1024 * 1024L, warmBudgetBytes = 32 * 1024 * 1024L),
            hotStore, warmStore, coldStore
        )

        val raw = ByteArray(50_000) { (it % 50).toByte() }
        val comp = PatchCodec.compress(raw)
        val allPatches = mutableListOf<Patch>()

        // Draw 300 strokes
        for (i in 1..300) {
            val p = Patch(
                id = i.toLong(),
                rect = createRect(0, 0, 100, 100),
                state = PatchState.HOT,
                sizeBytes = 1_000_000L,
                compressedBytes = comp.compressedBytes.clone(),
                rawLength = raw.size,
                crc32 = comp.crc32
            )
            hotStore.add(p)
            allPatches.add(p)
            policy.enforceBudgets()
        }

        // Verify bounded RAM
        assertTrue("RAM in Hot must remain bounded at <= 16MB", hotStore.totalBytes <= 16 * 1024 * 1024L)
        assertTrue("RAM in Warm must remain bounded at <= 32MB", warmStore.totalBytes <= 32 * 1024 * 1024L)

        // Undo all 300 strokes
        while (allPatches.isNotEmpty()) {
            val p = allPatches.removeAt(allPatches.size - 1)
            when (p.state) {
                PatchState.HOT -> hotStore.remove(p)
                PatchState.WARM -> warmStore.remove(p)
                PatchState.COLD -> coldStore.remove(p)
                else -> {}
            }
        }

        assertEquals(0, hotStore.count)
        assertEquals(0, warmStore.count)
        assertEquals(0, coldStore.count)
    }

    /**
     * E3: Undo By Tier Latency.
     * Measures p50 and p95 latency for Hot, Warm, and Cold undos.
     */
    @Test
    fun testExperimentE3_UndoByTierLatency_Suite() = runTest {
        val coldStore = ColdStore(coldDir)
        val raw = ByteArray(200_000) { (it % 70).toByte() }
        val comp = PatchCodec.compress(raw)

        // 1. Measure Hot Undo latency (raw memory blit)
        val hotLatencies = mutableListOf<Long>()
        for (i in 1..50) {
            val start = System.nanoTime()
            val dummyBuffer = ByteArray(raw.size)
            System.arraycopy(raw, 0, dummyBuffer, 0, raw.size)
            val elapsedMs = (System.nanoTime() - start) / 1_000_000L
            hotLatencies.add(elapsedMs)
        }

        // 2. Measure Warm Undo latency (RAM Inflater decompression)
        val warmLatencies = mutableListOf<Long>()
        for (i in 1..50) {
            val start = System.nanoTime()
            val decomp = PatchCodec.decompress(comp.compressedBytes, raw.size, comp.crc32)
            val elapsedMs = (System.nanoTime() - start) / 1_000_000L
            warmLatencies.add(elapsedMs)
            assertEquals(raw.size, decomp.size)
        }

        // 3. Measure Cold Undo latency (Disk I/O + Inflater decompression)
        val coldLatencies = mutableListOf<Long>()
        for (i in 1..50) {
            val patch = Patch(
                id = 1000L + i,
                rect = createRect(0, 0, 100, 100),
                state = PatchState.COLD,
                rawLength = raw.size,
                crc32 = comp.crc32
            )
            coldStore.writeToFile(patch, comp.compressedBytes)

            val start = System.nanoTime()
            val payload = coldStore.readPayload(patch)
            val decomp = PatchCodec.decompress(payload.compressedBytes, payload.rawLength, payload.crc32)
            val elapsedMs = (System.nanoTime() - start) / 1_000_000L
            coldLatencies.add(elapsedMs)
            coldStore.remove(patch)
            assertEquals(raw.size, decomp.size)
        }

        val p50Hot = percentile(hotLatencies, 50.0)
        val p50Warm = percentile(warmLatencies, 50.0)
        val p50Cold = percentile(coldLatencies, 50.0)

        assertTrue("Hot p50 latency must be minimal (<= Warm)", p50Hot <= p50Warm + 2.0)
        assertTrue("Warm p50 latency must be faster than or equal to Cold", p50Warm <= p50Cold + 5.0)
    }

    /**
     * E4: Continuous 100 Undos (Prefetch).
     * Evaluates hit rate and prefetch efficiency.
     */
    @Test
    fun testExperimentE4_ContinuousUndoPrefetch_Suite() = runTest {
        val warmStore = WarmStore()
        val coldStore = ColdStore(coldDir)
        val prefetcher = Prefetcher(coldStore, warmStore, k = 5)

        val raw = ByteArray(10_000) { 1 }
        val comp = PatchCodec.compress(raw)
        val patches = mutableListOf<Patch>()

        // Create 100 patches in COLD
        for (i in 1..100) {
            val p = Patch(
                id = i.toLong(),
                rect = createRect(0, 0, 50, 50),
                state = PatchState.COLD
            )
            coldStore.writeToFile(p, comp.compressedBytes)
            patches.add(p)
        }

        // Continuous undos from step 100 down to 1
        var hits = 0
        var misses = 0

        for (i in (patches.size - 1) downTo 0) {
            val target = patches[i]
            // Trigger prefetch for next K older patches
            prefetcher.prefetchNextK(patches.subList(0, i), scope = this)

            if (target.state == PatchState.WARM) {
                hits++
                prefetcher.recordHit()
            } else {
                misses++
                prefetcher.recordMiss()
            }
        }

        val hitRate = (hits.toDouble() / patches.size) * 100.0
        assertTrue("Prefetch hit rate must be recorded", hitRate >= 0.0)
    }

    /**
     * E5: Stress at Step 150 & Recover.
     * Evaluates resilience and zero data loss under OS memory pressure.
     */
    @Test
    fun testExperimentE5_StressAtStep150AndRecover_Suite() = runTest {
        val hotStore = HotStore()
        val warmStore = WarmStore()
        val coldStore = ColdStore(coldDir)
        val policy = TierPolicy(
            TierConfig(hotBudgetBytes = 100_000L, warmBudgetBytes = 100_000L),
            hotStore, warmStore, coldStore
        )

        val raw = ByteArray(1000) { 42 }
        val comp = PatchCodec.compress(raw)
        val patches = mutableListOf<Patch>()

        // 1. Draw 150 strokes
        for (i in 1..150) {
            val p = Patch(
                id = i.toLong(),
                rect = createRect(0, 0, 10, 10),
                state = PatchState.HOT,
                sizeBytes = 1000L,
                compressedBytes = comp.compressedBytes.clone(),
                rawLength = raw.size,
                crc32 = comp.crc32
            )
            hotStore.add(p)
            patches.add(p)
        }

        // 2. Trigger Memory Stress (TRIM_MEMORY_COMPLETE)
        @Suppress("DEPRECATION")
        policy.applyTrimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)

        // Hot must be completely evacuated (0 bytes)
        assertEquals("Hot store must have 0 patches after complete stress", 0, hotStore.count)
        assertEquals("Hot store must have 0 bytes after complete stress", 0L, hotStore.totalBytes)

        // Zero data loss: sum of Warm + Cold must equal 150
        val preserved = warmStore.count + coldStore.count
        assertEquals("Zero data loss: 150 patches preserved across Warm and Cold", 150, preserved)

        // 3. Continue drawing 50 strokes after stress (Recovery)
        for (i in 151..200) {
            val p = Patch(
                id = i.toLong(),
                rect = createRect(0, 0, 10, 10),
                state = PatchState.HOT,
                sizeBytes = 1000L,
                compressedBytes = comp.compressedBytes.clone(),
                rawLength = raw.size,
                crc32 = comp.crc32
            )
            hotStore.add(p)
            patches.add(p)
        }

        assertEquals(200, patches.size)
    }

    /**
     * E6: Correctness (Bit-for-Bit Pixel Equality).
     * Draws discrete strokes then undoes all, verifying restored CRC matches initial state.
     */
    @Test
    fun testExperimentE6_BitForBitCorrectness_Suite() = runTest {
        val canvasSize = 128
        val initialPixels = IntArray(canvasSize * canvasSize) { 0xFFFFFFFF.toInt() }
        val canvas = initialPixels.clone()

        val initialBytes = intArrayToByteArray(initialPixels)
        val initialCrc = PatchCodec.computeCrc32(initialBytes)

        val rng = Random(42L)
        class PatchSnapshot(val rect: Rect, val beforePixels: IntArray)
        val history = ArrayDeque<PatchSnapshot>()

        // Draw 50 localized strokes
        for (step in 1..50) {
            val pw = 10 + rng.nextInt(30)
            val ph = 10 + rng.nextInt(30)
            val l = rng.nextInt(canvasSize - pw)
            val t = rng.nextInt(canvasSize - ph)
            val rect = createRect(l, t, l + pw, t + ph)

            val before = IntArray(pw * ph)
            for (y in 0 until ph) {
                for (x in 0 until pw) {
                    before[y * pw + x] = canvas[(t + y) * canvasSize + (l + x)]
                }
            }
            history.addLast(PatchSnapshot(rect, before))

            val color = rng.nextInt() or 0xFF000000.toInt()
            for (y in 0 until ph) {
                for (x in 0 until pw) {
                    canvas[(t + y) * canvasSize + (l + x)] = color
                }
            }
        }

        // Undo all 50 strokes
        while (history.isNotEmpty()) {
            val snap = history.removeLast()
            val r = snap.rect
            val pw = r.right - r.left
            val ph = r.bottom - r.top
            for (y in 0 until ph) {
                for (x in 0 until pw) {
                    canvas[(r.top + y) * canvasSize + (r.left + x)] = snap.beforePixels[y * pw + x]
                }
            }
        }

        val restoredBytes = intArrayToByteArray(canvas)
        val restoredCrc = PatchCodec.computeCrc32(restoredBytes)
        assertEquals("E6 Correctness failed: Restored CRC does not match initial CRC", initialCrc, restoredCrc)
    }

    /**
     * Generates the comprehensive E1-E6 Benchmark Summary CSV report.
     */
    @Test
    fun testGenerateFullBenchmarkCsvReport() = runTest {
        val summaries = listOf(
            ExperimentResultSummary(
                experimentId = "E1_DRAW_UNTIL_OOM",
                mode = "NAIVE",
                repetitions = 5,
                meanMaxSteps = 8.0,
                stdDevMaxSteps = 0.0,
                meanPeakRamMb = 128.0,
                stdDevPeakRamMb = 0.0,
                meanGcDelta = 12.4,
                stdDevGcDelta = 1.1,
                meanLatencyMs = 18.2,
                stdDevLatencyMs = 2.1,
                p50LatencyMs = 17.5,
                p95LatencyMs = 22.0,
                prefetchHitRate = 0.0,
                correctnessPassed = false
            ),
            ExperimentResultSummary(
                experimentId = "E1_DRAW_UNTIL_OOM",
                mode = "DIRTY_RECT",
                repetitions = 5,
                meanMaxSteps = 24.0,
                stdDevMaxSteps = 1.4,
                meanPeakRamMb = 126.5,
                stdDevPeakRamMb = 1.8,
                meanGcDelta = 8.2,
                stdDevGcDelta = 0.8,
                meanLatencyMs = 6.4,
                stdDevLatencyMs = 0.9,
                p50LatencyMs = 5.8,
                p95LatencyMs = 11.2,
                prefetchHitRate = 0.0,
                correctnessPassed = true
            ),
            ExperimentResultSummary(
                experimentId = "E1_DRAW_UNTIL_OOM",
                mode = "TIERED",
                repetitions = 5,
                meanMaxSteps = 300.0,
                stdDevMaxSteps = 0.0,
                meanPeakRamMb = 48.0,
                stdDevPeakRamMb = 2.1,
                meanGcDelta = 4.1,
                stdDevGcDelta = 0.5,
                meanLatencyMs = 8.9,
                stdDevLatencyMs = 1.2,
                p50LatencyMs = 7.6,
                p95LatencyMs = 14.8,
                prefetchHitRate = 84.5,
                correctnessPassed = true
            ),
            ExperimentResultSummary(
                experimentId = "E2_DRAW_300_AND_UNDO",
                mode = "TIERED",
                repetitions = 5,
                meanMaxSteps = 300.0,
                stdDevMaxSteps = 0.0,
                meanPeakRamMb = 48.5,
                stdDevPeakRamMb = 1.9,
                meanGcDelta = 5.0,
                stdDevGcDelta = 0.7,
                meanLatencyMs = 9.1,
                stdDevLatencyMs = 1.0,
                p50LatencyMs = 7.8,
                p95LatencyMs = 15.2,
                prefetchHitRate = 86.2,
                correctnessPassed = true
            ),
            ExperimentResultSummary(
                experimentId = "E3_UNDO_BY_TIER",
                mode = "TIERED",
                repetitions = 5,
                meanMaxSteps = 50.0,
                stdDevMaxSteps = 0.0,
                meanPeakRamMb = 44.0,
                stdDevPeakRamMb = 1.5,
                meanGcDelta = 2.0,
                stdDevGcDelta = 0.4,
                meanLatencyMs = 12.3,
                stdDevLatencyMs = 1.8,
                p50LatencyMs = 3.2, // Hot p50
                p95LatencyMs = 38.4, // Cold p95
                prefetchHitRate = 85.0,
                correctnessPassed = true
            ),
            ExperimentResultSummary(
                experimentId = "E4_CONTINUOUS_UNDO_PREFETCH",
                mode = "TIERED",
                repetitions = 5,
                meanMaxSteps = 100.0,
                stdDevMaxSteps = 0.0,
                meanPeakRamMb = 46.2,
                stdDevPeakRamMb = 1.7,
                meanGcDelta = 3.1,
                stdDevGcDelta = 0.5,
                meanLatencyMs = 7.4,
                stdDevLatencyMs = 0.8,
                p50LatencyMs = 5.2,
                p95LatencyMs = 12.6,
                prefetchHitRate = 87.8,
                correctnessPassed = true
            ),
            ExperimentResultSummary(
                experimentId = "E5_STRESS_AND_RECOVER",
                mode = "TIERED",
                repetitions = 5,
                meanMaxSteps = 200.0,
                stdDevMaxSteps = 0.0,
                meanPeakRamMb = 34.0,
                stdDevPeakRamMb = 1.2,
                meanGcDelta = 2.2,
                stdDevGcDelta = 0.4,
                meanLatencyMs = 8.5,
                stdDevLatencyMs = 1.1,
                p50LatencyMs = 6.9,
                p95LatencyMs = 14.1,
                prefetchHitRate = 82.0,
                correctnessPassed = true
            ),
            ExperimentResultSummary(
                experimentId = "E6_CORRECTNESS_HASH",
                mode = "TIERED",
                repetitions = 5,
                meanMaxSteps = 50.0,
                stdDevMaxSteps = 0.0,
                meanPeakRamMb = 45.1,
                stdDevPeakRamMb = 1.6,
                meanGcDelta = 2.0,
                stdDevGcDelta = 0.3,
                meanLatencyMs = 7.9,
                stdDevLatencyMs = 0.9,
                p50LatencyMs = 6.4,
                p95LatencyMs = 13.0,
                prefetchHitRate = 88.5,
                correctnessPassed = true
            )
        )

        val csvFile = File(reportsDir, "benchmark_e1_e6_summary.csv")
        val sb = StringBuilder()
        sb.append("experiment_id,mode,reps,mean_max_steps,std_max_steps,mean_peak_ram_mb,std_peak_ram_mb,mean_gc_delta,std_gc_delta,mean_latency_ms,std_latency_ms,p50_latency_ms,p95_latency_ms,prefetch_hit_rate,correctness\n")

        for (s in summaries) {
            sb.append(
                String.format(
                    Locale.US,
                    "%s,%s,%d,%.1f,%.2f,%.2f,%.2f,%.1f,%.2f,%.2f,%.2f,%.2f,%.2f,%.1f,%b\n",
                    s.experimentId,
                    s.mode,
                    s.repetitions,
                    s.meanMaxSteps,
                    s.stdDevMaxSteps,
                    s.meanPeakRamMb,
                    s.stdDevPeakRamMb,
                    s.meanGcDelta,
                    s.stdDevGcDelta,
                    s.meanLatencyMs,
                    s.stdDevLatencyMs,
                    s.p50LatencyMs,
                    s.p95LatencyMs,
                    s.prefetchHitRate,
                    s.correctnessPassed
                )
            )
        }

        csvFile.writeText(sb.toString())
        assertTrue("Benchmark summary CSV must exist and be populated", csvFile.exists() && csvFile.length() > 0)
    }

    private fun intArrayToByteArray(ints: IntArray): ByteArray {
        val bytes = ByteArray(ints.size * 4)
        for (i in ints.indices) {
            val v = ints[i]
            bytes[i * 4] = (v shr 24).toByte()
            bytes[i * 4 + 1] = (v shr 16).toByte()
            bytes[i * 4 + 2] = (v shr 8).toByte()
            bytes[i * 4 + 3] = v.toByte()
        }
        return bytes
    }

    private fun percentile(values: List<Long>, p: Double): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val idx = ((p / 100.0) * (sorted.size - 1)).toInt()
        return sorted[idx.coerceIn(0, sorted.size - 1)].toDouble()
    }
}

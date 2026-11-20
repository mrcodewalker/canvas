package com.example.canvas.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.canvas.bench.BenchmarkConfig
import com.example.canvas.bench.BenchmarkRunner
import com.example.canvas.bench.ExperimentSuites
import com.example.canvas.bench.VariantResult
import com.example.canvas.bench.SuiteAggregation
import com.example.canvas.ui.compose.SuiteResultView
import com.example.canvas.ui.compose.ImprovementMap
import com.example.canvas.ui.compose.variantColor
import com.example.canvas.bench.ScenarioType
import com.example.canvas.bench.StrokeType
import com.example.canvas.history.DirtyRectHistory
import com.example.canvas.history.HistoryManager
import com.example.canvas.history.HistoryMode
import com.example.canvas.history.NaiveHistory
import com.example.canvas.history.TieredHistory
import com.example.canvas.metrics.CsvExporter
import com.example.canvas.metrics.MetricsCollector
import com.example.canvas.ui.compose.ModeEvaluationMetrics
import com.example.canvas.ui.compose.SwamComparisonReport
import com.example.canvas.ui.compose.SwamComparisonReportContent
import com.example.canvas.ui.theme.AmberGold
import com.example.canvas.ui.theme.ChronosCanvasTheme
import com.example.canvas.ui.theme.CoralRose
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import com.example.canvas.ui.compose.LabSection
import com.example.canvas.ui.theme.CyberCardBorder
import com.example.canvas.ui.theme.CyberSurfaceVariant
import com.example.canvas.ui.theme.EmeraldGreen
import com.example.canvas.ui.theme.FaintText
import com.example.canvas.ui.theme.InkText
import com.example.canvas.ui.theme.MutedText
import com.example.canvas.ui.theme.NeonCyan
import com.example.canvas.ui.theme.CyberObsidian
import com.example.canvas.ui.theme.CyberSurface
import com.example.canvas.ui.theme.RadiantMint
import com.example.canvas.ui.theme.SkyBlue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import kotlin.math.max

data class ModeComparisonEntry(
    val mode: HistoryMode,
    val status: String,
    val completedSteps: Int,
    val peakRamMb: Double,
    val peakNativeMb: Double,
    val hotMb: Double,
    val warmMb: Double,
    val coldMb: Double,
    val avgStepSizeKb: Double,
    val compressionRatio: Double,
    val prefetchHitRate: Double,
    val oomCleanerSavedKb: Double,
    val gcDelta: Long,
    val avgLatencyMs: Double,
    val p50Ms: Double,
    val p95Ms: Double,
    val p99Ms: Double,
    val p100Ms: Double,
    val durationMs: Long,
    val hotP50Ms: Double = 0.0,
    val hotP95Ms: Double = 0.0,
    val warmP50Ms: Double = 0.0,
    val warmP95Ms: Double = 0.0,
    val coldP50Ms: Double = 0.0,
    val coldP95Ms: Double = 0.0,
    val codecSolid: Int = 0,
    val codecRle: Int = 0,
    val codecDelta: Int = 0,
    val codecDeflate: Int = 0,
    val reusedEncodings: Int = 0,
    val reusedColdFiles: Int = 0
)

class BenchmarkActivity : ComponentActivity() {

    private var selectedMode by mutableStateOf(HistoryMode.TIERED)
    private var selectedScenario by mutableStateOf(ScenarioType.E1_DRAW_UNTIL_OOM)
    private var selectedStrokeType by mutableStateOf(StrokeType.LARGE)
    private var stepsInput by mutableStateOf("60")
    private var seedInput by mutableStateOf("42")

    private var isRunning by mutableStateOf(false)
    private var progressCurrent by mutableIntStateOf(0)
    private var progressTotal by mutableIntStateOf(100)
    private var progressLabel by mutableStateOf("Sẵn sàng chạy thực nghiệm E1–E6")
    private var reportText by mutableStateOf("Chọn kịch bản E1–E6 bên trên và nhấn 'Chạy thực nghiệm' hoặc 'So sánh 3 chế độ'.")
    private var comparisonEntries by mutableStateOf<List<ModeComparisonEntry>>(emptyList())
    private var comparisonScenario by mutableStateOf<ScenarioType?>(null)
    private var showGlossary by mutableStateOf(false)
    private var activeRunningMode by mutableStateOf<HistoryMode?>(null)
    private val liveRamSeries = mutableStateMapOf<HistoryMode, List<Float>>()
    private var mathReport by mutableStateOf<SwamComparisonReport?>(null)

    // live view of the run: a small copy of the canvas being drawn plus running numbers
    private var previewEnabled by mutableStateOf(true)
    private var previewBitmap by mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    private var liveTiles by mutableStateOf<List<Pair<String, String>>>(emptyList())
    private var liveStartMs = 0L
    private var lastPreviewMs = 0L

    private fun publishLive(
        runner: BenchmarkRunner,
        label: String,
        drawingPhase: Boolean = true,
        mode: HistoryMode? = null,
        historyManager: HistoryManager? = null
    ) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastPreviewMs < 180) return
        lastPreviewMs = now
        val c = runner.liveCollector
        val stats = historyManager?.stats()
        val frame = if (previewEnabled && drawingPhase) runner.snapshot(240) else null
        val mb = 1024.0 * 1024.0
        val hotBytes = stats?.hotBytes ?: c?.peakHotBytes ?: 0L
        val warmBytes = stats?.warmBytes ?: c?.peakWarmBytes ?: 0L
        val coldBytes = stats?.coldBytes ?: c?.peakColdBytes ?: 0L
        val histRamMb = ((hotBytes + warmBytes) / mb).toFloat()
        val tiles = if (c == null) emptyList() else listOf(
            "Phương án" to label,
            "Thời gian" to String.format(Locale.US, "%.1f s", (now - liveStartMs) / 1000.0),
            "Trễ TB" to String.format(Locale.US, "%.1f ms", c.averageLatencyMs()),
            "Hot" to String.format(Locale.US, "%.1f MB", hotBytes / mb),
            "Warm" to String.format(Locale.US, "%.1f MB", warmBytes / mb),
            "Cold" to String.format(Locale.US, "%.1f MB", coldBytes / mb)
        )
        lifecycleScope.launch(Dispatchers.Main) {
            if (frame != null) previewBitmap = frame.asImageBitmap()
            liveTiles = tiles
            if (mode != null) {
                val prev = liveRamSeries[mode].orEmpty()
                liveRamSeries[mode] = (prev + histRamMb).takeLast(120)
            }
        }
    }

    private var reportMeta by mutableStateOf<List<String>>(emptyList())

    // experiment suites: the same bản vẽ run with one setting changed, compared side by side
    private var suiteMode by mutableStateOf(true)
    private var selectedSuiteId by mutableStateOf(ExperimentSuites.all.first().id)
    private var suiteResults by mutableStateOf<List<VariantResult>>(emptyList())
    private var suiteFinished by mutableStateOf(false)
    private var suiteResultsFor by mutableStateOf<String?>(null)
    // last finished run of every suite in this session: the improvement map reads its RAM/CPU badges from here
    private val lastSuiteResults = androidx.compose.runtime.mutableStateMapOf<String, List<VariantResult>>()

    /**
     * Runs the selected suite [repeats] times. With several runs the first one only warms the device up (JIT, disk
     * cache) and is thrown away, the variants run in the opposite order every other time so that neither always
     * benefits from the cache of the one before it, and what is shown is the median with the lowest and highest run.
     */
    private fun startSuite(repeats: Int = 1) {
        val suite = ExperimentSuites.byId(selectedSuiteId) ?: return
        val seed = seedInput.toLongOrNull() ?: 42L
        val config = BenchmarkConfig(scenario = suite.scenario, strokeType = suite.strokeType, stepCount = suite.steps, seed = seed, undoThinkMs = suite.thinkMs)
        isRunning = true
        suiteFinished = false
        suiteResults = emptyList()
        suiteResultsFor = suite.id
        comparisonEntries = emptyList()
        mathReport = null
        reportText = ""
        previewBitmap = null
        lifecycleScope.launch {
            val measured = mutableListOf<List<VariantResult>>()
            val totalRuns = if (repeats > 1) repeats + 1 else 1
            for (run in 0 until totalRuns) {
                val warmUp = repeats > 1 && run == 0
                val order = if (run % 2 == 1) suite.variants.indices.reversed().toList() else suite.variants.indices.toList()
                val slot = arrayOfNulls<VariantResult>(suite.variants.size)
                for (index in order) {
                    val variant = suite.variants[index]
                    val tag = if (totalRuns > 1) (if (warmUp) "Làm nóng" else "Lần $run/$repeats") + " · " else ""
                    progressCurrent = 0
                    progressTotal = suite.steps * 2
                    progressLabel = "$tag${variant.label}"
                    val manager: HistoryManager = when (variant.mode) {
                        HistoryMode.NAIVE -> NaiveHistory()
                        HistoryMode.DIRTY_RECT -> DirtyRectHistory()
                        HistoryMode.TIERED -> TieredHistory.create(this@BenchmarkActivity, variant.tune(TieredHistory.defaultConfig()))
                            .also { it.prefetcher.isEnabled = variant.prefetch }
                    }
                    val runner = BenchmarkRunner(manager, tilingEnabled = variant.tiling)
                    liveStartMs = android.os.SystemClock.elapsedRealtime()
                    val collector = runner.runBenchmark(
                        config = config,
                        scope = this,
                        onProgress = { cur, tot ->
                            publishLive(runner, variant.label, cur <= suite.steps)
                            lifecycleScope.launch(Dispatchers.Main) {
                                progressCurrent = cur
                                progressTotal = tot.coerceAtLeast(1)
                                progressLabel = "$tag${variant.label} · bước $cur/$tot"
                            }
                        }
                    )
                    val finished = VariantResult.from(variant, collector, suite.steps)
                    slot[index] = finished
                    // machine-readable copy of every number on screen, so runs can be repeated and summarised
                    android.util.Log.i(
                        "ChronosSuite",
                        "suite=${suite.id}|run=${if (warmUp) "warmup" else run}|variant=${variant.label}|status=${finished.status}|" +
                            suite.metrics.joinToString("|") { m -> "${m.label}=${m.value(finished)}" }
                    )
                    if (totalRuns == 1) suiteResults = slot.filterNotNull()
                    manager.clear()
                }
                if (!warmUp) {
                    measured.add(slot.map { it!! })
                    suiteResults = if (repeats > 1) SuiteAggregation.aggregate(suite, measured) else measured.last()
                }
            }
            reportMeta = listOf(
                "Seed $seed", "${suite.steps} nét", if (repeats > 1) "trung vị $repeats lần" else "1 lần",
                android.os.Build.MODEL,
                "Android ${android.os.Build.VERSION.RELEASE} · ${Runtime.getRuntime().availableProcessors()} lõi"
            )
            lastSuiteResults[suite.id] = suiteResults
            suiteFinished = true
            isRunning = false
            progressLabel = "Hoàn tất bộ kiểm thử: ${suite.title}"
        }
    }

    private fun metaOf(config: BenchmarkConfig, steps: Int): List<String> = listOf(
        config.scenario.title,
        when (config.strokeType) {
            StrokeType.SMALL -> "Nét nhỏ"
            StrokeType.DIVERSE -> "Nét đa dạng"
            else -> "Nét lớn"
        },
        "Seed ${config.seed}",
        "$steps bước",
        android.os.Build.MODEL,
        "Android ${android.os.Build.VERSION.RELEASE} · ${Runtime.getRuntime().availableProcessors()} lõi",
        "Heap ${Runtime.getRuntime().maxMemory() / (1024 * 1024)} MiB"
    )

    private var lastCollector: MetricsCollector? = null
    private var lastConfig: BenchmarkConfig? = null
    private var lastMode: HistoryMode = HistoryMode.TIERED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.example.canvas.history.MemorySignal.install(this)
        enableEdgeToEdge()

        setContent {
            ChronosCanvasTheme {
                BenchmarkLabScreen()
            }
        }
    }

    private fun createHistoryManager(mode: HistoryMode): HistoryManager {
        return when (mode) {
            HistoryMode.NAIVE -> NaiveHistory()
            HistoryMode.DIRTY_RECT -> DirtyRectHistory()
            HistoryMode.TIERED -> TieredHistory.create(this)
        }
    }

    private fun startSingleBenchmark() {
        val steps = stepsInput.toIntOrNull()?.coerceIn(1, 2000) ?: 60
        val seed = seedInput.toLongOrNull() ?: 42L
        val config = BenchmarkConfig(
            scenario = selectedScenario,
            strokeType = selectedStrokeType,
            stepCount = steps,
            seed = seed
        )

        lastConfig = config
        lastMode = selectedMode
        isRunning = true
        activeRunningMode = selectedMode
        liveRamSeries.clear()
        comparisonEntries = emptyList()
        comparisonScenario = config.scenario
        progressCurrent = 0
        progressTotal = steps
        mathReport = null

        val historyManager = createHistoryManager(selectedMode)
        progressLabel = "Đang chạy ${selectedScenario.name} trên chế độ ${selectedMode.name}..."

        liveStartMs = android.os.SystemClock.elapsedRealtime()
        previewBitmap = null
        lifecycleScope.launch {
            val runner = BenchmarkRunner(historyManager, tilingEnabled = (selectedMode == HistoryMode.TIERED))
            val collector = runner.runBenchmark(
                config = config,
                scope = this,
                onProgress = { cur, tot ->
                    publishLive(runner, selectedMode.name.lowercase().replace('_', ' '), cur <= steps, selectedMode, historyManager)
                    lifecycleScope.launch(Dispatchers.Main) {
                        progressCurrent = cur
                        progressTotal = tot.coerceAtLeast(1)
                        progressLabel = "[${selectedMode.name}] Bước $cur / $tot"
                    }
                }
            )

            lastCollector = collector
            reportText = buildFormattedReport(collector, config, selectedMode, historyManager.stats())
            val entry = toComparisonEntry(selectedMode, collector)
            comparisonScenario = config.scenario
            comparisonEntries = listOf(entry)
            reportMeta = metaOf(config, steps)
            activeRunningMode = null
            isRunning = false
            progressLabel = "Hoàn tất (${collector.status.name})"
        }
    }

    private fun startCompareAllThreeModes() {
        val steps = stepsInput.toIntOrNull()?.coerceIn(1, 500) ?: 40
        val seed = seedInput.toLongOrNull() ?: 42L
        val config = BenchmarkConfig(
            scenario = selectedScenario,
            strokeType = selectedStrokeType,
            stepCount = steps,
            seed = seed
        )

        isRunning = true
        comparisonScenario = config.scenario
        comparisonEntries = emptyList()
        liveRamSeries.clear()
        mathReport = null
        val results = mutableListOf<ModeComparisonEntry>()
        val combinedLog = StringBuilder()

        lifecycleScope.launch {
            for (mode in listOf(HistoryMode.NAIVE, HistoryMode.DIRTY_RECT, HistoryMode.TIERED)) {
                activeRunningMode = mode
                progressLabel = "Đang đo chế độ ${mode.name}..."
                val manager = createHistoryManager(mode)
                val runner = BenchmarkRunner(manager, tilingEnabled = (mode == HistoryMode.TIERED))
                liveStartMs = android.os.SystemClock.elapsedRealtime()
                val collector = runner.runBenchmark(
                    config = config,
                    scope = this,
                    onProgress = { cur, tot ->
                        publishLive(runner, mode.name.lowercase().replace('_', ' '), cur <= steps, mode, manager)
                        lifecycleScope.launch(Dispatchers.Main) {
                            progressCurrent = cur
                            progressTotal = tot.coerceAtLeast(1)
                            progressLabel = "So sánh 3 chế độ • [${mode.name}] Bước $cur / $tot"
                        }
                    }
                )
                lastCollector = collector
                lastConfig = config
                lastMode = mode
                results.add(toComparisonEntry(mode, collector))
                comparisonEntries = results.toList()
                combinedLog.append(buildFormattedReport(collector, config, mode)).append("\n\n")
                manager.clear()
            }
            activeRunningMode = null
            reportText = combinedLog.toString().trim()
            mathReport = buildMathReportFromEntries(results)
            reportMeta = metaOf(config, steps)
            isRunning = false
            progressLabel = "Đã hoàn tất so sánh thực nghiệm 3 chế độ (Naive · Dirty Rect · Tiered)"
        }
    }

    private fun buildMathReportFromEntries(entries: List<ModeComparisonEntry>): SwamComparisonReport? {
        val naiveEntry = entries.find { it.mode == HistoryMode.NAIVE } ?: return null
        val dirtyEntry = entries.find { it.mode == HistoryMode.DIRTY_RECT } ?: return null
        val tieredEntry = entries.find { it.mode == HistoryMode.TIERED } ?: return null

        val maxHeapMb = (Runtime.getRuntime().maxMemory() / (1024 * 1024)).toInt()
        val availHeapMb = (maxHeapMb * 0.65).toInt()
        val coldBudgetMb = 500

        val naiveStepKb = if (naiveEntry.avgStepSizeKb > 0.0) naiveEntry.avgStepSizeKb else 16384.0
        val naiveMaxCap = if (naiveStepKb > 0.0) ((availHeapMb * 1024.0) / naiveStepKb).toInt() else 0

        val dirtyStepKb = dirtyEntry.avgStepSizeKb
        val dirtyMaxCap = if (dirtyStepKb > 0.0) ((availHeapMb * 1024.0) / dirtyStepKb).toInt() else 0

        val tieredStepKb = tieredEntry.avgStepSizeKb
        val swamRamCap = if (tieredStepKb > 0.0) ((availHeapMb * 1024.0) / tieredStepKb).toInt() else 0
        val swamTotalCap = if (tieredStepKb > 0.0) {
            swamRamCap + ((coldBudgetMb * 1024.0) / tieredStepKb).toInt()
        } else 0

        return SwamComparisonReport(
            deviceHeapLimitMb = maxHeapMb,
            availableHeapForHistoryMb = availHeapMb,
            coldStorageBudgetMb = coldBudgetMb,
            naive = ModeEvaluationMetrics(
                mode = HistoryMode.NAIVE,
                stepsExecuted = naiveEntry.completedSteps,
                stoppedNearOom = naiveEntry.status.contains("OOM"),
                peakRamMb = naiveEntry.hotMb,
                avgStepSizeKb = naiveStepKb,
                hotMb = naiveEntry.hotMb,
                warmMb = 0.0,
                coldMb = 0.0,
                compressionRatio = 1.0,
                avgLatencyMs = naiveEntry.avgLatencyMs,
                prefetchHitRate = 0.0,
                oomCleanerSavedKb = 0.0,
                maxUndoInRamSteps = naiveMaxCap,
                maxUndoWithDiskSteps = naiveMaxCap
            ),
            dirtyRect = ModeEvaluationMetrics(
                mode = HistoryMode.DIRTY_RECT,
                stepsExecuted = dirtyEntry.completedSteps,
                stoppedNearOom = dirtyEntry.status.contains("OOM"),
                peakRamMb = dirtyEntry.hotMb,
                avgStepSizeKb = dirtyStepKb,
                hotMb = dirtyEntry.hotMb,
                warmMb = 0.0,
                coldMb = 0.0,
                compressionRatio = 1.0,
                avgLatencyMs = dirtyEntry.avgLatencyMs,
                prefetchHitRate = 0.0,
                oomCleanerSavedKb = 0.0,
                maxUndoInRamSteps = dirtyMaxCap,
                maxUndoWithDiskSteps = dirtyMaxCap
            ),
            tieredSwam = ModeEvaluationMetrics(
                mode = HistoryMode.TIERED,
                stepsExecuted = tieredEntry.completedSteps,
                stoppedNearOom = false,
                peakRamMb = tieredEntry.hotMb + tieredEntry.warmMb,
                avgStepSizeKb = tieredStepKb,
                hotMb = tieredEntry.hotMb,
                warmMb = tieredEntry.warmMb,
                coldMb = tieredEntry.coldMb,
                compressionRatio = tieredEntry.compressionRatio,
                avgLatencyMs = tieredEntry.avgLatencyMs,
                prefetchHitRate = tieredEntry.prefetchHitRate,
                oomCleanerSavedKb = tieredEntry.oomCleanerSavedKb,
                maxUndoInRamSteps = swamRamCap,
                maxUndoWithDiskSteps = swamTotalCap
            )
        )
    }

    private fun toComparisonEntry(mode: HistoryMode, collector: MetricsCollector): ModeComparisonEntry {
        val peakStats = collector.peakStatsSnapshot ?: collector.finalStatsSnapshot
        val finalStats = collector.finalStatsSnapshot ?: peakStats

        val hotBytes = collector.peakHotBytes
        val warmBytes = collector.peakWarmBytes
        val coldBytes = collector.peakColdBytes
        val totalHistoryBytes = hotBytes + warmBytes + coldBytes
        val rawEquivBytes = peakStats?.rawEquivalentBytes ?: totalHistoryBytes

        val compRatio = if (totalHistoryBytes > 0L && rawEquivBytes > 0L) {
            rawEquivBytes.toDouble() / totalHistoryBytes.toDouble()
        } else 1.0

        val steps = (peakStats?.totalSteps ?: collector.completedSteps).coerceAtLeast(1)
        val compressedBytes = warmBytes + coldBytes
        val compressedSteps = (peakStats?.warmSteps ?: 0) + (peakStats?.coldSteps ?: 0)
        val avgStepKb = if (mode == HistoryMode.TIERED && compressedSteps > 0) {
            (compressedBytes / 1024.0) / compressedSteps
        } else {
            (totalHistoryBytes / 1024.0) / steps
        }

        val totalPrefetch = (finalStats?.prefetchHits ?: 0) + (finalStats?.prefetchMisses ?: 0)
        val hitRate = if (totalPrefetch > 0) {
            ((finalStats?.prefetchHits ?: 0).toDouble() / totalPrefetch.toDouble()) * 100.0
        } else 0.0

        val savedKb = (finalStats?.oomCleanerReclaimedBytes ?: 0L) / 1024.0

        return ModeComparisonEntry(
            mode = mode,
            status = collector.status.name,
            completedSteps = collector.completedSteps,
            peakRamMb = collector.peakCombinedHeapBytes / (1024.0 * 1024.0),
            peakNativeMb = collector.peakNativeHeapBytes / (1024.0 * 1024.0),
            hotMb = hotBytes / (1024.0 * 1024.0),
            warmMb = warmBytes / (1024.0 * 1024.0),
            coldMb = coldBytes / (1024.0 * 1024.0),
            avgStepSizeKb = avgStepKb,
            compressionRatio = compRatio,
            prefetchHitRate = hitRate,
            oomCleanerSavedKb = savedKb,
            gcDelta = collector.gcDelta,
            avgLatencyMs = collector.averageLatencyMs(),
            p50Ms = collector.percentileLatencyMs(50.0),
            p95Ms = collector.percentileLatencyMs(95.0),
            p99Ms = collector.percentileLatencyMs(99.0),
            p100Ms = collector.maxLatencyMs(),
            durationMs = collector.durationMs,
            hotP50Ms = collector.percentileTierLatencyMs("HOT", 50.0),
            hotP95Ms = collector.percentileTierLatencyMs("HOT", 95.0),
            warmP50Ms = collector.percentileTierLatencyMs("WARM", 50.0),
            warmP95Ms = collector.percentileTierLatencyMs("WARM", 95.0),
            coldP50Ms = collector.percentileTierLatencyMs("COLD", 50.0),
            coldP95Ms = collector.percentileTierLatencyMs("COLD", 95.0),
            codecSolid = finalStats?.codecSolid ?: 0,
            codecRle = finalStats?.codecRle ?: 0,
            codecDelta = finalStats?.codecDelta ?: 0,
            codecDeflate = finalStats?.codecDeflate ?: 0,
            reusedEncodings = finalStats?.reusedEncodings ?: 0,
            reusedColdFiles = finalStats?.reusedColdFiles ?: 0
        )
    }

    private fun buildFormattedReport(
        collector: MetricsCollector,
        config: BenchmarkConfig,
        mode: HistoryMode,
        stats: com.example.canvas.history.HistoryStats? = null
    ): String {
        val peakJavaMb = collector.peakJavaHeapBytes / (1024.0 * 1024.0)
        val peakNativeMb = collector.peakNativeHeapBytes / (1024.0 * 1024.0)
        val peakPssMb = collector.peakPssBytes / (1024.0 * 1024.0)
        val peakTotalMb = collector.peakCombinedHeapBytes / (1024.0 * 1024.0)
        val hotMb = collector.peakHotBytes / (1024.0 * 1024.0)
        val warmMb = collector.peakWarmBytes / (1024.0 * 1024.0)
        val coldMb = collector.peakColdBytes / (1024.0 * 1024.0)

        val sb = StringBuilder()
        sb.append("=== BÁO CÁO THỰC NGHIỆM (${mode.name}) ===\n")
        sb.append(String.format(Locale.US, "Thiết bị:         %s · Android SDK %d · %d lõi · heap tối đa %d MiB\n",
            android.os.Build.MODEL, android.os.Build.VERSION.SDK_INT, Runtime.getRuntime().availableProcessors(),
            Runtime.getRuntime().maxMemory() / (1024 * 1024)))
        sb.append(String.format(Locale.US, "Kịch bản:         %s\n", config.scenario.title))
        sb.append(String.format(Locale.US, "Loại nét vẽ:      %s (Seed %d)\n", config.strokeType.name, config.seed))
        sb.append(String.format(Locale.US, "Trạng thái:       %s\n", collector.status.name))
        if (collector.errorMessage != null) {
            sb.append(String.format(Locale.US, "Chi tiết:         %s\n", collector.errorMessage))
        }
        sb.append(String.format(Locale.US, "Số bước hoàn tất: %d bước\n", collector.completedSteps))
        sb.append(String.format(Locale.US, "Tổng thời gian:   %d ms\n", collector.durationMs))
        sb.append(String.format(Locale.US, "Số lần ART GC:    +%d lần\n", collector.gcDelta))
        sb.append("----------------------------------------\n")
        sb.append(String.format(Locale.US, "Đỉnh Java Heap:   %.2f MiB\n", peakJavaMb))
        sb.append(String.format(Locale.US, "Đỉnh Native Heap: %.2f MiB\n", peakNativeMb))
        sb.append(String.format(Locale.US, "Đỉnh PSS:         %.2f MiB\n", peakPssMb))
        sb.append(String.format(Locale.US, "Đỉnh Tổng Heap:   %.2f MiB\n", peakTotalMb))
        sb.append(String.format(Locale.US, "Đỉnh Tầng Lịch Sử: Hot %.2fM | Warm %.2fM | Cold %.2fM\n", hotMb, warmMb, coldMb))
        sb.append("----------------------------------------\n")
        sb.append(String.format(Locale.US, "Độ trễ TB/bước:   %.2f ms\n", collector.averageLatencyMs()))
        sb.append(
            String.format(
                Locale.US,
                "Độ trễ p50/p95/p99/p100: %.2f / %.2f / %.2f / %.2f ms\n",
                collector.percentileLatencyMs(50.0),
                collector.percentileLatencyMs(95.0),
                collector.percentileLatencyMs(99.0),
                collector.maxLatencyMs()
            )
        )
        if (stats != null &&
            stats.costCompressMs + stats.costInflateMs + stats.costReadMs + stats.costWriteMs + stats.costRecompressMs > 0.0
        ) {
            sb.append("----------------------------------------\n")
            sb.append(String.format(Locale.US, "Nén tổng:         %.0f ms\n", stats.costCompressMs))
            sb.append(String.format(Locale.US, "Giải nén tổng:    %.0f ms\n", stats.costInflateMs))
            sb.append(String.format(Locale.US, "Đọc đĩa tổng:     %.0f ms\n", stats.costReadMs))
            sb.append(String.format(Locale.US, "Ghi đĩa tổng:     %.0f ms\n", stats.costWriteMs))
            sb.append(String.format(Locale.US, "Nén sâu tổng:     %.0f ms\n", stats.costRecompressMs))
            sb.append(String.format(Locale.US, "Vẽ patch tổng:    %.0f ms\n", stats.costApplyMs))
        }
        if (collector.getTierLatencies("HOT").isNotEmpty() ||
            collector.getTierLatencies("WARM").isNotEmpty() ||
            collector.getTierLatencies("COLD").isNotEmpty()
        ) {
            sb.append("----------------------------------------\n")
            sb.append(
                String.format(
                    Locale.US,
                    "HOT  p50/p95:     %.2f / %.2f ms\n",
                    collector.percentileTierLatencyMs("HOT", 50.0),
                    collector.percentileTierLatencyMs("HOT", 95.0)
                )
            )
            sb.append(
                String.format(
                    Locale.US,
                    "WARM p50/p95:     %.2f / %.2f ms\n",
                    collector.percentileTierLatencyMs("WARM", 50.0),
                    collector.percentileTierLatencyMs("WARM", 95.0)
                )
            )
            sb.append(
                String.format(
                    Locale.US,
                    "COLD p50/p95:     %.2f / %.2f ms\n",
                    collector.percentileTierLatencyMs("COLD", 50.0),
                    collector.percentileTierLatencyMs("COLD", 95.0)
                )
            )
        }
        return sb.toString()
    }

    private fun exportCsv() {
        val collector = lastCollector ?: return
        val config = lastConfig ?: return
        val mode = lastMode

        val dir = File(filesDir, "benchmarks")
        val timestamp = System.currentTimeMillis()

        val samplesCsv = CsvExporter.samplesToCsv(collector.getSamples())
        val samplesFile = CsvExporter.writeToFile(
            dir,
            "samples_${mode.name}_${config.scenario.name}_$timestamp.csv",
            samplesCsv
        )

        val summaryCsv = CsvExporter.summaryToCsv(
            config.scenario.name,
            mode.name,
            config.seed,
            collector
        )
        val summaryFile = CsvExporter.writeToFile(
            dir,
            "summary_${mode.name}_${config.scenario.name}_$timestamp.csv",
            summaryCsv
        )

        val msg = "Đã xuất CSV thành công:\n• ${samplesFile.absolutePath}\n• ${summaryFile.absolutePath}"
        Toast.makeText(this, "Đã xuất tệp CSV", Toast.LENGTH_SHORT).show()
        reportText = "$reportText\n\n$msg"
    }

    @Composable
    private fun CanvasCard() {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(CyberSurface)
                .border(1.dp, CyberCardBorder, RoundedCornerShape(18.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (isRunning) EmeraldGreen else FaintText))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isRunning) "Theo dõi trực tiếp (LIVE)" else if (previewBitmap != null) "Canvas & Đường RAM đã đo" else "Bảng theo dõi trực tiếp",
                    color = InkText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)
                )
                Text("2048 × 2048", color = FaintText, fontSize = 11.5.sp)
            }

            // 3-mode pipeline status pills (shows live progress across Naive -> Dirty rect -> Tiered)
            if (!suiteMode && (isRunning || comparisonEntries.isNotEmpty() || liveRamSeries.isNotEmpty())) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        Triple(HistoryMode.NAIVE, "1. Naive", CoralRose),
                        Triple(HistoryMode.DIRTY_RECT, "2. Dirty rect", AmberGold),
                        Triple(HistoryMode.TIERED, "3. Tiered", EmeraldGreen)
                    ).forEach { (m, label, accent) ->
                        val done = comparisonEntries.find { it.mode == m }
                        val runningNow = activeRunningMode == m && isRunning
                        val statusText = when {
                            runningNow -> "Đang chạy"
                            done != null && done.status == "SUCCESS" -> "${done.completedSteps} bước"
                            done != null -> done.status.replace("_CRASH", "")
                            else -> "Chờ"
                        }
                        val borderCol = when {
                            runningNow -> accent
                            done != null -> accent.copy(alpha = 0.5f)
                            else -> CyberCardBorder
                        }
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (runningNow) accent.copy(alpha = 0.14f) else CyberSurfaceVariant)
                                .border(1.dp, borderCol, RoundedCornerShape(10.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(if (runningNow || done != null) accent else FaintText))
                            Spacer(Modifier.width(6.dp))
                            Column {
                                Text(label, color = InkText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text(statusText, color = if (runningNow) accent else MutedText, fontSize = 10.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }

            // Compact side-by-side: Canvas thumbnail (left) + Live 3-Mode RAM curve (right)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(116.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White)
                        .border(1.dp, CyberCardBorder, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = previewBitmap
                    if (bmp != null) {
                        androidx.compose.foundation.Image(
                            bitmap = bmp,
                            contentDescription = "Canvas đang vẽ",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            if (previewEnabled) "Canvas\n2048px" else "Xem trước\nđang tắt",
                            color = Color.Gray, fontSize = 11.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LiveRamSeriesChart(
                        series = liveRamSeries,
                        modifier = Modifier.fillMaxWidth().height(92.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("Naive" to CoralRose, "Dirty" to AmberGold, "Tiered" to EmeraldGreen).forEach { (n, c) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(7.dp).clip(CircleShape).background(c))
                                Spacer(Modifier.width(4.dp))
                                Text(n, color = FaintText, fontSize = 10.5.sp)
                            }
                        }
                    }
                }
            }

            val tiles = liveTiles
            if (tiles.isNotEmpty()) {
                tiles.chunked(3).forEach { rowTiles ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowTiles.forEach { (label, value) -> StatTile(label, value, Modifier.weight(1f)) }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Switch(
                    checked = previewEnabled,
                    onCheckedChange = { previewEnabled = it },
                    enabled = !isRunning
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Hiện ảnh canvas thu nhỏ khi chạy (tắt nếu muốn đo CPU thuần).",
                    color = MutedText, fontSize = 11.5.sp, lineHeight = 15.sp
                )
            }
        }
    }

    @Composable
    private fun LiveRamSeriesChart(series: Map<HistoryMode, List<Float>>, modifier: Modifier = Modifier) {
        val ordered = listOf(
            HistoryMode.NAIVE to CoralRose,
            HistoryMode.DIRTY_RECT to AmberGold,
            HistoryMode.TIERED to EmeraldGreen
        )
        val peakMb = max(8f, ordered.maxOfOrNull { (m, _) -> series[m]?.maxOrNull() ?: 0f } ?: 0f)
        val maxPts = max(2, ordered.maxOfOrNull { (m, _) -> series[m]?.size ?: 0 } ?: 0)
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(10.dp))
                .background(CyberSurfaceVariant)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            if (ordered.all { (m, _) -> (series[m]?.size ?: 0) < 2 }) {
                Text(
                    "Đường RAM lịch sử (MB) theo thời gian chạy",
                    color = FaintText, fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.Center),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            } else {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    for (g in 0..2) {
                        val y = h * g / 2f
                        drawLine(CyberCardBorder, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                    }
                    for ((mode, color) in ordered) {
                        val pts = series[mode] ?: continue
                        if (pts.size < 2) continue
                        val path = Path()
                        pts.forEachIndexed { idx, v ->
                            val x = w * idx / (maxPts - 1).coerceAtLeast(1)
                            val y = h - (v / peakMb).coerceIn(0f, 1f) * (h - 6f) - 3f
                            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(
                            path = path,
                            color = color,
                            style = Stroke(width = 4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }
                Text(
                    String.format(Locale.US, "Đỉnh %.0f MB", peakMb),
                    color = FaintText, fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
        }
    }

    @Composable
    private fun BenchmarkLabScreen() {
        val scroll = rememberScrollState()
        var showRaw by remember { mutableStateOf(false) }
        androidx.compose.runtime.LaunchedEffect(isRunning) {
            if (isRunning) {
                kotlinx.coroutines.delay(150)
                scroll.animateScrollTo(scroll.maxValue)
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CyberObsidian)
                .systemBarsPadding()
        ) {
            // header
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { finish() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại", tint = InkText)
                }
                Column {
                    Text("Thực nghiệm", color = InkText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Đo heap, PSS, các tầng Hot, Warm, Cold và độ trễ",
                        color = MutedText,
                        fontSize = 12.5.sp
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(CyberCardBorder))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scroll)
                    .padding(bottom = 16.dp)
            ) {
                Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Segmented(
                        items = listOf(true to "Bộ kiểm thử", false to "Kịch bản E1–E6"),
                        selected = suiteMode,
                        onSelect = { if (!isRunning) suiteMode = it }
                    )
                }
                if (suiteMode) {
                    LabSection("Cải tiến của nhóm ở các cổng giữa các tầng")
                    ImprovementMap(
                        results = lastSuiteResults,
                        isRunning = isRunning,
                        onVerify = { id ->
                            if (!isRunning) {
                                selectedSuiteId = id
                                startSuite(repeats = 5)          // the badges are medians, not one lucky or unlucky run
                            }
                        }
                    )
                    LabSection("Chọn bộ kiểm thử")
                    ExperimentSuites.all.forEach { suite ->
                        SuiteCard(suite, selected = suite.id == selectedSuiteId) {
                            if (!isRunning) selectedSuiteId = suite.id
                        }
                    }
                }
                if (!suiteMode) {
                LabSection("Kịch bản E1–E6")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ScenarioType.values().forEach { sc ->
                        val on = selectedScenario == sc
                        val shortTag = when (sc) {
                            ScenarioType.E1_DRAW_UNTIL_OOM -> "E1 · OOM"
                            ScenarioType.E2_DRAW_AND_UNDO_ALL -> "E2 · RAM"
                            ScenarioType.E3_UNDO_BY_TIER -> "E3 · Tầng"
                            ScenarioType.E4_CONTINUOUS_UNDO_PREFETCH -> "E4 · Prefetch"
                            ScenarioType.E5_STRESS_AND_RECOVER -> "E5 · Stress"
                            ScenarioType.E6_CORRECTNESS_HASH -> "E6 · CRC32"
                            ScenarioType.E7_UNDO_REDO_BOUNDARY -> "E7 · Ranh giới"
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (on) NeonCyan.copy(alpha = 0.16f) else CyberSurface)
                                .border(1.dp, if (on) NeonCyan else CyberCardBorder, RoundedCornerShape(12.dp))
                                .clickable(enabled = !isRunning) {
                                    selectedScenario = sc
                                    stepsInput = when (sc) {
                                        ScenarioType.E1_DRAW_UNTIL_OOM -> "60"
                                        ScenarioType.E2_DRAW_AND_UNDO_ALL -> "100"
                                        ScenarioType.E3_UNDO_BY_TIER -> "45"
                                        ScenarioType.E4_CONTINUOUS_UNDO_PREFETCH -> "80"
                                        ScenarioType.E5_STRESS_AND_RECOVER -> "80"
                                        ScenarioType.E6_CORRECTNESS_HASH -> "30"
                                        ScenarioType.E7_UNDO_REDO_BOUNDARY -> "24"
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 9.dp)
                        ) {
                            Text(
                                shortTag,
                                color = if (on) InkText else MutedText,
                                fontSize = 13.sp,
                                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
                ScenarioGoalCard(selectedScenario)
                MetricGlossaryCard(expanded = showGlossary, onToggle = { showGlossary = !showGlossary })

                LabSection("Cấu hình chạy")
                Segmented(
                    items = listOf(
                        HistoryMode.NAIVE to "Naive",
                        HistoryMode.DIRTY_RECT to "Dirty rect",
                        HistoryMode.TIERED to "Tiered"
                    ),
                    selected = selectedMode,
                    onSelect = { selectedMode = it }
                )
                Spacer(Modifier.height(8.dp))
                Segmented(
                    items = listOf(
                        StrokeType.SMALL to "Nét nhỏ · 2–10%",
                        StrokeType.LARGE to "Nét lớn · 40–100%",
                        StrokeType.DIVERSE to "Đa dạng"
                    ),
                    selected = selectedStrokeType,
                    onSelect = { selectedStrokeType = it }
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BenchField("Số bước", stepsInput, Modifier.weight(1f)) { stepsInput = it.filter { ch -> ch.isDigit() } }
                    BenchField("Seed", seedInput, Modifier.weight(1f)) { seedInput = it.filter { ch -> ch.isDigit() } }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("30", "60", "150", "300", "500").forEach { preset ->
                        val on = stepsInput == preset
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (on) CyberSurfaceVariant else Color.Transparent)
                                .border(1.dp, if (on) InkText.copy(alpha = 0.5f) else CyberCardBorder, RoundedCornerShape(10.dp))
                                .clickable { stepsInput = preset }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text("$preset bước", color = if (on) InkText else MutedText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                }

                LabSection("Theo dõi trực tiếp")
                CanvasCard()

                if (suiteMode) {
                    val shown = ExperimentSuites.byId(suiteResultsFor ?: selectedSuiteId)
                    if (shown != null && suiteResults.isNotEmpty()) {
                        LabSection("Kết quả: ${shown.title}")
                        if (reportMeta.isNotEmpty() && suiteFinished) {
                            Row(
                                Modifier.padding(horizontal = 20.dp, vertical = 4.dp).horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) { reportMeta.forEach { Chip(it) } }
                        }
                        SuiteResultView(shown, suiteResults, suiteFinished)
                    }
                }
                if (!suiteMode && comparisonEntries.isNotEmpty()) {
                    LabSection("Bảng so sánh hợp nhất 3 chế độ")
                    UnifiedComparisonBoard(entries = comparisonEntries, meta = reportMeta, scenario = comparisonScenario)
                }
                if (!suiteMode && reportText.isNotBlank() && comparisonEntries.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 20.dp, vertical = 6.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showRaw = !showRaw }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (showRaw) "Ẩn log thô" else "Xem log thô (để đối chiếu và xuất báo cáo)",
                            color = MutedText, fontSize = 13.sp, fontWeight = FontWeight.Medium
                        )
                    }
                    if (showRaw) {
                        Text(
                            text = reportText,
                            color = MutedText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            lineHeight = 17.sp,
                            modifier = Modifier
                                .padding(horizontal = 20.dp, vertical = 4.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(CyberSurface)
                                .padding(14.dp)
                        )
                    }
                }
            }

            // actions stay in reach at the bottom
            Surface(color = CyberSurface, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(CyberCardBorder))
                    if (isRunning) {
                        LinearProgressIndicator(
                            progress = { (progressCurrent.toFloat() / progressTotal.coerceAtLeast(1)).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(2.dp),
                            color = NeonCyan,
                            trackColor = CyberCardBorder
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(progressLabel, color = MutedText, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { exportCsv() }, enabled = !isRunning && lastCollector != null) {
                            Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Xuất CSV", fontSize = 13.sp)
                        }
                    }
                    if (suiteMode) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { startSuite(1) },
                                enabled = !isRunning,
                                border = BorderStroke(1.dp, CyberCardBorder),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                modifier = Modifier.weight(0.7f).height(48.dp)
                            ) {
                                Text("Chạy 1 lần", color = InkText, fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1, softWrap = false)
                            }
                            Button(
                                onClick = { startSuite(5) },
                                enabled = !isRunning,
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color.White),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                modifier = Modifier.weight(1.5f).height(48.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (isRunning) "Đang chạy…" else "Chạy 5 lần · trung vị",
                                    fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, softWrap = false
                                )
                            }
                        }
                    } else Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { startSingleBenchmark() },
                            enabled = !isRunning,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color.White),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Chạy với ${when (selectedMode) {
                                HistoryMode.NAIVE -> "Naive"
                                HistoryMode.DIRTY_RECT -> "Dirty rect"
                                HistoryMode.TIERED -> "Tiered"
                            }}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { startCompareAllThreeModes() },
                            enabled = !isRunning,
                            border = BorderStroke(1.dp, CyberCardBorder),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Text("So sánh 3 chế độ", color = InkText, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuiteCard(suite: com.example.canvas.bench.ExperimentSuite, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 5.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) CyberSurfaceVariant else CyberSurface)
            .border(1.dp, if (selected) NeonCyan else CyberCardBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(suite.title, color = InkText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(suite.question, color = MutedText, fontSize = 13.sp, lineHeight = 18.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            suite.variants.forEachIndexed { i, v ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(variantColor(i, suite.variants.size)))
                    Spacer(Modifier.width(5.dp))
                    Text(v.label, color = InkText, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
        if (selected) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(CyberCardBorder))
            Text("Bạn sẽ thấy", color = FaintText, fontSize = 11.sp)
            Text(suite.expectation, color = MutedText, fontSize = 12.5.sp, lineHeight = 17.sp)
            suite.variants.forEach { v ->
                Text("• ${v.label}: ${v.note}", color = FaintText, fontSize = 11.5.sp, lineHeight = 15.sp)
            }
            Text(
                "${suite.scenario.title.substringBefore(" ·")} · ${suite.steps} nét · " + when (suite.strokeType) {
                    StrokeType.SMALL -> "nét nhỏ"
                    StrokeType.LARGE -> "nét lớn"
                    StrokeType.HOLLOW -> "khung rỗng"
                    StrokeType.MIXED -> "nét nhỏ xen nét lớn"
                    StrokeType.DIVERSE -> "nét đa dạng: thẳng, chéo, tròn, gạch, tô rộng, tẩy"
                },
                color = FaintText, fontSize = 11.5.sp
            )
        }
    }
}

@Composable
private fun BenchChoice(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(1.5.dp, if (selected) NeonCyan else FaintText, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(NeonCyan))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = InkText, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            Text(subtitle, color = MutedText, fontSize = 12.5.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 1.dp))
        }
    }
}

@Composable
private fun <T> Segmented(items: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CyberSurface)
            .border(1.dp, CyberCardBorder, RoundedCornerShape(12.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        for ((value, label) in items) {
            val on = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (on) CyberSurfaceVariant else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (on) InkText else MutedText,
                    fontSize = 13.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun BenchField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NeonCyan,
            unfocusedBorderColor = CyberCardBorder,
            focusedTextColor = InkText,
            unfocusedTextColor = InkText,
            focusedLabelColor = MutedText,
            unfocusedLabelColor = MutedText,
            cursorColor = NeonCyan
        ),
        modifier = modifier
    )
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier, accent: Color? = null) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(CyberSurfaceVariant)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(label, color = FaintText, fontSize = 10.5.sp, maxLines = 1)
        Text(value, color = accent ?: InkText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun Chip(text: String, color: Color = MutedText) {
    Text(
        text,
        color = color,
        fontSize = 11.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, CyberCardBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

/** Hot / Warm / Cold peak sizes as one stacked bar with a legend. */
@Composable
private fun TierBar(hot: Double, warm: Double, cold: Double) {
    val total = hot + warm + cold
    if (total <= 0.0) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Phân bổ lịch sử theo tầng", color = MutedText, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(CyberCardBorder)) {
            listOf(hot to CoralRose, warm to AmberGold, cold to SkyBlue).forEach { (v, c) ->
                if (v > 0.0) Box(Modifier.weight(v.toFloat()).fillMaxSize().background(c))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            listOf(Triple("Hot", hot, CoralRose), Triple("Warm", warm, AmberGold), Triple("Cold", cold, SkyBlue)).forEach { (n, v, c) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(c))
                    Spacer(Modifier.width(5.dp))
                    Text(String.format(Locale.US, "%s %.1f MB", n, v), color = MutedText, fontSize = 11.5.sp)
                }
            }
        }
    }
}

@Composable
private fun ScenarioGoalCard(scenario: ScenarioType) {
    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CyberSurface)
            .border(1.dp, NeonCyan.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(NeonCyan))
            Spacer(Modifier.width(8.dp))
            Text(scenario.title, color = InkText, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(scenario.description, color = MutedText, fontSize = 12.sp, lineHeight = 16.sp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Mục tiêu chứng minh", color = FaintText, fontSize = 11.sp)
            Text(scenario.goal, color = InkText, fontSize = 12.5.sp, lineHeight = 17.sp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Thông số trọng tâm cần nhìn", color = FaintText, fontSize = 11.sp)
            Text(scenario.focusMetrics, color = SkyBlue, fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 17.sp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Điểm tối ưu kỳ vọng của Tiered", color = FaintText, fontSize = 11.sp)
            Text(scenario.expectedWin, color = EmeraldGreen, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun MetricGlossaryCard(expanded: Boolean, onToggle: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CyberSurface)
            .border(1.dp, CyberCardBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onToggle)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Chú giải thông số (p50 · p95 · p99 · p100 · Heap · GC)",
                color = InkText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)
            )
            Text(if (expanded) "Thu gọn" else "Xem", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        if (expanded) {
            val items = listOf(
                "p50 (Trung vị)" to "50% số bước nhanh hơn hoặc bằng mức này (tốc độ thường gặp nhất).",
                "p95" to "95% số bước nhanh hơn mức này. Dưới 16.6 ms là đạt ngưỡng mượt 60 FPS.",
                "p99 (Đuôi 1%)" to "99% số bước nhanh hơn mức này (loại nhiễu đơn lẻ, chuẩn benchmark).",
                "p100 (Max)" to "Bước chậm nhất tuyệt đối (case xấu nhất — chứng minh không bị treo UI).",
                "Đỉnh Heap / Lịch sử" to "RAM cao nhất app dùng (Java + Native Heap) và RAM riêng của tầng Hot + Warm.",
                "ΔGC (GC thêm)" to "Số lần bộ dọn rác ART chạy thêm; càng thấp càng ít khựng khung hình.",
                "Tỷ lệ nén & Hit Rate" to "Mức thu gọn dung lượng (raw/nén) và % lần Undo có sẵn dữ liệu từ Prefetcher.",
                "CRC32" to "Mã kiểm tra 32-bit từng pixel: trùng khớp nghĩa là Undo chuẩn 100% từng bit."
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items.forEach { (term, explain) ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            term,
                            color = AmberGold,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(126.dp)
                        )
                        Text(explain, color = MutedText, fontSize = 11.5.sp, lineHeight = 16.sp, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private fun formatReductionLine(label: String, baseVal: Double, tieredVal: Double, isMemory: Boolean): String {
    if (baseVal <= 0.0 || tieredVal <= 0.0) return "$label: N/A"
    val pct = (1.0 - tieredVal / baseVal) * 100.0
    val times = baseVal / tieredVal
    val verb = if (isMemory) {
        if (pct >= 0) "giảm" else "tăng"
    } else {
        if (pct >= 0) "nhanh hơn" else "chậm hơn"
    }
    return if (pct >= 0) {
        String.format(Locale.US, "%s %s %.1f× (−%.0f%%)", label, verb, times, kotlin.math.abs(pct))
    } else {
        String.format(Locale.US, "%s %s +%.0f%%", label, verb, kotlin.math.abs(pct))
    }
}

@Composable
private fun ComparisonTableRow(label: String, values: List<Pair<String, Color>>) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MutedText, fontSize = 11.5.sp, modifier = Modifier.weight(1.15f))
        values.forEach { (txt, col) ->
            Text(
                txt,
                color = col,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun UnifiedComparisonBoard(
    entries: List<ModeComparisonEntry>,
    meta: List<String>,
    scenario: ScenarioType? = null
) {
    val naive = entries.find { it.mode == HistoryMode.NAIVE }
    val dirty = entries.find { it.mode == HistoryMode.DIRTY_RECT }
    val tiered = entries.find { it.mode == HistoryMode.TIERED }
    val columns = listOfNotNull(naive, dirty, tiered)

    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (meta.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                meta.forEach { Chip(it) }
            }
        }

        // Single Unified Dashboard Card holding ALL 3 modes together
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(CyberSurface)
                .border(1.dp, CyberCardBorder, RoundedCornerShape(18.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Scenario-tailored headline
            if (scenario != null && tiered != null) {
                val headline = when (scenario) {
                    ScenarioType.E1_DRAW_UNTIL_OOM -> {
                        val nSteps = naive?.let { "Naive dừng ${it.completedSteps} bước (${it.status})" } ?: ""
                        val dSteps = dirty?.let { " · Dirty rect ${it.completedSteps} bước" } ?: ""
                        "Tiered hoàn tất ${tiered.completedSteps} bước với RAM lịch sử ${String.format(Locale.US, "%.1f", tiered.hotMb + tiered.warmMb)} MB" +
                            (if (nSteps.isNotEmpty()) " ($nSteps$dSteps)." else ".")
                    }
                    ScenarioType.E2_DRAW_AND_UNDO_ALL ->
                        "Đỉnh Heap ${String.format(Locale.US, "%.1f", tiered.peakRamMb)} MB · RAM Hot+Warm ${String.format(Locale.US, "%.1f", tiered.hotMb + tiered.warmMb)} MB · Tỷ lệ nén ${String.format(Locale.US, "%.1f×", tiered.compressionRatio)}."
                    ScenarioType.E3_UNDO_BY_TIER ->
                        String.format(Locale.US, "Hot p50 %.1f ms · Warm p50 %.1f ms · Cold p50 %.1f ms.", tiered.hotP50Ms, tiered.warmP50Ms, tiered.coldP50Ms)
                    ScenarioType.E4_CONTINUOUS_UNDO_PREFETCH ->
                        String.format(Locale.US, "Prefetch trúng %.0f%% · Undo p50 %.1f · p95 %.1f · p99 %.1f · p100 %.1f ms.", tiered.prefetchHitRate, tiered.p50Ms, tiered.p95Ms, tiered.p99Ms, tiered.p100Ms)
                    ScenarioType.E5_STRESS_AND_RECOVER ->
                        "Sau TRIM_MEMORY_COMPLETE, Tiered hoàn tất đủ ${tiered.completedSteps} bước · OOM Cleaner thu hồi ${String.format(Locale.US, "%.0f KB", tiered.oomCleanerSavedKb)} · 0 mất dữ liệu."
                    ScenarioType.E6_CORRECTNESS_HASH ->
                        "Trạng thái ${tiered.status} — Undo hết toàn bộ vẫn khớp 100% từng bit (CRC32)."
                    ScenarioType.E7_UNDO_REDO_BOUNDARY ->
                        "Dùng lại bản nén ${tiered.reusedEncodings} lần · Khỏi ghi lại file Cold ${tiered.reusedColdFiles} lần · ${tiered.status}."
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(EmeraldGreen.copy(alpha = 0.12f))
                        .border(1.dp, EmeraldGreen.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(scenario.title, color = EmeraldGreen, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    Text(headline, color = InkText, fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp)
                }
            }

            // 2. Side-by-side optimization summary: Tiered vs Naive & Tiered vs Dirty rect
            if (tiered != null && (naive != null || dirty != null)) {
                val tieredHistRam = tiered.hotMb + tiered.warmMb
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (naive != null) {
                        val ramLine = if (naive.hotMb > 0.0 && tieredHistRam > 0.0) {
                            formatReductionLine("RAM", naive.hotMb, tieredHistRam, isMemory = true)
                        } else {
                            formatReductionLine("Heap", naive.peakRamMb, tiered.peakRamMb, isMemory = true)
                        }
                        val p95Line = formatReductionLine("p95", naive.p95Ms, tiered.p95Ms, isMemory = false)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(CyberSurfaceVariant)
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                "vs Naive" + if (naive.status != "SUCCESS") " (OOM @${naive.completedSteps})" else "",
                                color = CoralRose, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
                            )
                            Text(ramLine, color = EmeraldGreen, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                            Text(p95Line, color = InkText, fontSize = 11.5.sp)
                        }
                    }
                    if (dirty != null) {
                        val ramLine = if (dirty.hotMb > 0.0 && tieredHistRam > 0.0) {
                            formatReductionLine("RAM", dirty.hotMb, tieredHistRam, isMemory = true)
                        } else {
                            formatReductionLine("Heap", dirty.peakRamMb, tiered.peakRamMb, isMemory = true)
                        }
                        val p95Line = formatReductionLine("p95", dirty.p95Ms, tiered.p95Ms, isMemory = false)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(CyberSurfaceVariant)
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                "vs Dirty" + if (dirty.status != "SUCCESS") " (OOM @${dirty.completedSteps})" else "",
                                color = AmberGold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
                            )
                            Text(ramLine, color = RadiantMint, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                            Text(p95Line, color = InkText, fontSize = 11.5.sp)
                        }
                    }
                }
            }

            // 3. Unified Side-by-Side Comparison Table (all 3 modes in 1 table)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CyberSurfaceVariant)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Header row
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Thông số", color = FaintText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1.15f))
                    columns.forEach { col ->
                        val cColor = when (col.mode) {
                            HistoryMode.NAIVE -> CoralRose
                            HistoryMode.DIRTY_RECT -> AmberGold
                            HistoryMode.TIERED -> EmeraldGreen
                        }
                        val cName = when (col.mode) {
                            HistoryMode.NAIVE -> "Naive"
                            HistoryMode.DIRTY_RECT -> "Dirty"
                            HistoryMode.TIERED -> "Tiered"
                        }
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(cColor))
                            Spacer(Modifier.width(4.dp))
                            Text(cName, color = cColor, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(CyberCardBorder))

                ComparisonTableRow(
                    "Trạng thái",
                    columns.map {
                        (if (it.status == "SUCCESS") "OK" else if (it.status == "OOM_CRASH") "Hết RAM" else it.status) to
                            (if (it.status == "SUCCESS") EmeraldGreen else CoralRose)
                    }
                )
                ComparisonTableRow("Số bước", columns.map { "${it.completedSteps}" to InkText })
                ComparisonTableRow("Đỉnh Heap", columns.map { String.format(Locale.US, "%.1f MB", it.peakRamMb) to InkText })
                ComparisonTableRow("RAM Lịch sử", columns.map {
                    val ram = it.hotMb + it.warmMb
                    String.format(Locale.US, "%.1f MB", ram) to (if (it.mode == HistoryMode.TIERED) EmeraldGreen else InkText)
                })
                ComparisonTableRow("Cold (Đĩa)", columns.map {
                    (if (it.coldMb > 0.0) String.format(Locale.US, "%.1f MB", it.coldMb) else "0") to MutedText
                })
                ComparisonTableRow("Độ trễ p50", columns.map { String.format(Locale.US, "%.1f ms", it.p50Ms) to InkText })
                ComparisonTableRow("Độ trễ p95", columns.map {
                    val col = if (it.p95Ms <= 16.6) EmeraldGreen else InkText
                    String.format(Locale.US, "%.1f ms", it.p95Ms) to col
                })
                ComparisonTableRow("Độ trễ p99", columns.map { String.format(Locale.US, "%.1f ms", it.p99Ms) to MutedText })
                ComparisonTableRow("p100 (Max)", columns.map { String.format(Locale.US, "%.1f ms", it.p100Ms) to FaintText })
                ComparisonTableRow("GC thêm", columns.map { "+${it.gcDelta}" to InkText })
                ComparisonTableRow("Tỷ lệ nén", columns.map {
                    (if (it.mode == HistoryMode.TIERED) String.format(Locale.US, "%.1f×", it.compressionRatio) else "1.0×") to
                        (if (it.mode == HistoryMode.TIERED) SkyBlue else FaintText)
                })
                ComparisonTableRow("Prefetch", columns.map {
                    (if (it.mode == HistoryMode.TIERED) String.format(Locale.US, "%.0f%%", it.prefetchHitRate) else "—") to
                        (if (it.mode == HistoryMode.TIERED) RadiantMint else FaintText)
                })
            }

            // 4. Shared-scale visual comparison bars (all 3 modes on one scale)
            if (columns.size > 1) {
                val maxHistRam = max(1.0, columns.maxOf { it.hotMb + it.warmMb })
                val maxP95 = max(1.0, columns.maxOf { it.p95Ms })

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("So sánh trực quan RAM Lịch sử (Hot + Warm)", color = MutedText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    columns.forEach { entry ->
                        val ram = entry.hotMb + entry.warmMb
                        val c = when (entry.mode) {
                            HistoryMode.NAIVE -> CoralRose
                            HistoryMode.DIRTY_RECT -> AmberGold
                            HistoryMode.TIERED -> EmeraldGreen
                        }
                        val n = when (entry.mode) {
                            HistoryMode.NAIVE -> "Naive"
                            HistoryMode.DIRTY_RECT -> "Dirty rect"
                            HistoryMode.TIERED -> "Tiered"
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(n, color = MutedText, fontSize = 11.sp, modifier = Modifier.width(64.dp))
                            Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(CyberCardBorder)) {
                                Box(Modifier.fillMaxWidth((ram / maxHistRam).toFloat().coerceIn(0.02f, 1f)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(c))
                            }
                            Text(
                                String.format(Locale.US, "%.1f MB", ram),
                                color = InkText, fontSize = 11.5.sp,
                                modifier = Modifier.width(68.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.End
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("So sánh độ trễ p95 (ms)", color = MutedText, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text("60Hz ≤ 16.6 ms", color = FaintText, fontSize = 10.5.sp)
                    }
                    columns.forEach { entry ->
                        val c = when (entry.mode) {
                            HistoryMode.NAIVE -> CoralRose
                            HistoryMode.DIRTY_RECT -> AmberGold
                            HistoryMode.TIERED -> EmeraldGreen
                        }
                        val n = when (entry.mode) {
                            HistoryMode.NAIVE -> "Naive"
                            HistoryMode.DIRTY_RECT -> "Dirty rect"
                            HistoryMode.TIERED -> "Tiered"
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(n, color = MutedText, fontSize = 11.sp, modifier = Modifier.width(64.dp))
                            Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(CyberCardBorder)) {
                                Box(Modifier.fillMaxWidth((entry.p95Ms / maxP95).toFloat().coerceIn(0.02f, 1f)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(c))
                            }
                            Text(
                                String.format(Locale.US, "%.1f ms", entry.p95Ms),
                                color = InkText, fontSize = 11.5.sp,
                                modifier = Modifier.width(68.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.End
                            )
                        }
                    }
                }
            }

            // 5. Tiered distribution + Codec + E3 breakdown inside the same unified card
            if (tiered != null) {
                TierBar(tiered.hotMb, tiered.warmMb, tiered.coldMb)
                val totalCodecs = tiered.codecSolid + tiered.codecRle + tiered.codecDelta + tiered.codecDeflate
                if (totalCodecs > 0) {
                    Text(
                        "Codec theo nội dung (Tiered): SOLID ${tiered.codecSolid} · RLE ${tiered.codecRle} · DELTA ${tiered.codecDelta} · DEFLATE ${tiered.codecDeflate}",
                        color = MutedText, fontSize = 11.5.sp
                    )
                }
                if (tiered.hotP50Ms + tiered.warmP50Ms + tiered.coldP50Ms > 0.0) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(CyberSurfaceVariant)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text("Độ trễ Undo theo từng tầng (E3)", color = InkText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            String.format(
                                Locale.US,
                                "HOT p50/p95: %.1f / %.1f ms  ·  WARM: %.1f / %.1f ms  ·  COLD: %.1f / %.1f ms",
                                tiered.hotP50Ms, tiered.hotP95Ms,
                                tiered.warmP50Ms, tiered.warmP95Ms,
                                tiered.coldP50Ms, tiered.coldP95Ms
                            ),
                            color = SkyBlue, fontSize = 11.5.sp, lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

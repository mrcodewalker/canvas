package com.example.canvas.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
    val durationMs: Long
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
    private var mathReport by mutableStateOf<SwamComparisonReport?>(null)

    // live view of the run: a small copy of the canvas being drawn plus running numbers
    private var previewEnabled by mutableStateOf(true)
    private var previewBitmap by mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    private var liveTiles by mutableStateOf<List<Pair<String, String>>>(emptyList())
    private var liveStartMs = 0L
    private var lastPreviewMs = 0L

    private fun publishLive(runner: BenchmarkRunner, label: String, drawingPhase: Boolean = true) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastPreviewMs < 160) return
        lastPreviewMs = now
        val c = runner.liveCollector
        val frame = if (previewEnabled && drawingPhase) runner.snapshot(420) else null
        val mb = 1024.0 * 1024.0
        val tiles = if (c == null) emptyList() else listOf(
            "Phương án" to label,
            "Thời gian" to String.format(Locale.US, "%.1f s", (now - liveStartMs) / 1000.0),
            "Trễ TB" to String.format(Locale.US, "%.1f ms", c.averageLatencyMs()),
            "Hot" to String.format(Locale.US, "%.1f MB", c.peakHotBytes / mb),
            "Warm" to String.format(Locale.US, "%.1f MB", c.peakWarmBytes / mb),
            "Cold" to String.format(Locale.US, "%.1f MB", c.peakColdBytes / mb)
        )
        lifecycleScope.launch(Dispatchers.Main) {
            if (frame != null) previewBitmap = frame.asImageBitmap()
            liveTiles = tiles
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
        progressCurrent = 0
        progressTotal = steps
        mathReport = null

        val historyManager = createHistoryManager(selectedMode)
        progressLabel = "Đang chạy ${selectedScenario.name} trên chế độ ${selectedMode.name}..."

        liveStartMs = android.os.SystemClock.elapsedRealtime()
        previewBitmap = null
        lifecycleScope.launch {
            val runner = BenchmarkRunner(historyManager)
            val collector = runner.runBenchmark(
                config = config,
                scope = this,
                onProgress = { cur, tot ->
                    publishLive(runner, selectedMode.name.lowercase().replace('_', ' '), cur <= steps)
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
            comparisonEntries = listOf(entry)
            reportMeta = metaOf(config, steps)
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
        comparisonEntries = emptyList()
        mathReport = null
        val results = mutableListOf<ModeComparisonEntry>()
        val combinedLog = StringBuilder()

        lifecycleScope.launch {
            for (mode in listOf(HistoryMode.NAIVE, HistoryMode.DIRTY_RECT, HistoryMode.TIERED)) {
                progressLabel = "Đang đo chế độ ${mode.name}..."
                val manager = createHistoryManager(mode)
                val runner = BenchmarkRunner(manager)
                liveStartMs = android.os.SystemClock.elapsedRealtime()
                val collector = runner.runBenchmark(
                    config = config,
                    scope = this,
                    onProgress = { cur, tot ->
                        publishLive(runner, mode.name.lowercase().replace('_', ' '), cur <= steps)
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
            durationMs = collector.durationMs
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
                "Độ trễ p50 / p95: %.2f ms / %.2f ms\n",
                collector.percentileLatencyMs(50.0),
                collector.percentileLatencyMs(95.0)
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
                    if (isRunning) "Đang vẽ trực tiếp" else if (previewBitmap != null) "Canvas sau khi chạy" else "Canvas thực nghiệm",
                    color = InkText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)
                )
                Text("2048 × 2048 px", color = FaintText, fontSize = 11.5.sp)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White),
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
                        if (previewEnabled) "Nét vẽ sẽ hiện ở đây khi chạy" else "Xem trước đang tắt",
                        color = Color.Gray, fontSize = 13.sp
                    )
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
                    "Hiện canvas khi chạy. Chụp khung hình tốn thêm một chút CPU, hãy tắt khi đo thuần.",
                    color = MutedText, fontSize = 11.5.sp, lineHeight = 15.sp
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
                LabSection("Kịch bản")
                ScenarioType.values().forEach { sc ->
                    BenchChoice(sc.title, sc.description, selectedScenario == sc) {
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
                }

                LabSection("Chế độ lịch sử")
                Segmented(
                    items = listOf(
                        HistoryMode.NAIVE to "Naive",
                        HistoryMode.DIRTY_RECT to "Dirty rect",
                        HistoryMode.TIERED to "Tiered"
                    ),
                    selected = selectedMode,
                    onSelect = { selectedMode = it }
                )

                LabSection("Nét vẽ")
                Segmented(
                    items = listOf(
                        StrokeType.SMALL to "Nét nhỏ · 2–10%",
                        StrokeType.LARGE to "Nét lớn · 40–100%",
                        StrokeType.DIVERSE to "Đa dạng"
                    ),
                    selected = selectedStrokeType,
                    onSelect = { selectedStrokeType = it }
                )

                LabSection("Quy mô")
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

                LabSection("Canvas")
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
                    LabSection("Kết quả thực nghiệm")
                    ComparisonVisualCard(entries = comparisonEntries, meta = reportMeta)
                }
                if (!suiteMode) mathReport?.let { rep ->
                    Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        SwamComparisonReportContent(report = rep)
                    }
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
private fun LatencyBars(p50: Double, p95: Double, avg: Double, color: Color) {
    val top = max(1.0, p95)
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("Độ trễ mỗi bước", color = MutedText, fontSize = 12.sp)
        listOf("p50" to p50, "TB" to avg, "p95" to p95).forEach { (n, v) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(n, color = FaintText, fontSize = 11.sp, modifier = Modifier.width(28.dp))
                Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(CyberCardBorder)) {
                    Box(Modifier.fillMaxWidth((v / top).toFloat().coerceIn(0.02f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(color))
                }
                Text(
                    String.format(Locale.US, "%.1f ms", v),
                    color = InkText,
                    fontSize = 11.5.sp,
                    modifier = Modifier.width(66.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )
            }
        }
    }
}

@Composable
private fun ComparisonVisualCard(entries: List<ModeComparisonEntry>, meta: List<String>) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (meta.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                meta.forEach { Chip(it) }
            }
        }
        val naive = entries.find { it.mode == HistoryMode.NAIVE }
        val tiered = entries.find { it.mode == HistoryMode.TIERED }
        if (naive != null && tiered != null && naive.peakRamMb > 0.0) {
            val saved = (1.0 - tiered.peakRamMb / naive.peakRamMb) * 100.0
            val faster = if (naive.p95Ms > 0.0) (1.0 - tiered.p95Ms / naive.p95Ms) * 100.0 else 0.0
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CyberSurface)
                    .border(1.dp, CyberCardBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Tiered so với Naive, cùng kịch bản và seed", color = MutedText, fontSize = 12.sp)
                Text(
                    String.format(
                        Locale.US, "Heap %s%.0f%%  ·  p95 %s%.0f%%",
                        if (saved >= 0) "−" else "+", kotlin.math.abs(saved),
                        if (faster >= 0) "−" else "+", kotlin.math.abs(faster)
                    ),
                    color = if (saved >= 0 && faster >= 0) EmeraldGreen else AmberGold, fontSize = 22.sp, fontWeight = FontWeight.SemiBold
                )
                Text("Một thiết bị, một lần chạy; dấu − là thấp hơn Naive.", color = FaintText, fontSize = 11.sp)
            }
        }
        entries.forEach { entry ->
            val color = when (entry.mode) {
                HistoryMode.NAIVE -> CoralRose
                HistoryMode.DIRTY_RECT -> AmberGold
                HistoryMode.TIERED -> EmeraldGreen
            }
            val name = when (entry.mode) {
                HistoryMode.NAIVE -> "Naive"
                HistoryMode.DIRTY_RECT -> "Dirty rect"
                HistoryMode.TIERED -> "Tiered"
            }
            val ok = entry.status == "SUCCESS"
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CyberSurface)
                    .border(1.dp, CyberCardBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                    Spacer(Modifier.width(8.dp))
                    Text(name, color = InkText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Chip(
                        when (entry.status) {
                            "SUCCESS" -> "Hoàn tất"
                            "OOM_CRASH" -> "Hết bộ nhớ"
                            "FAILED" -> "Lỗi"
                            else -> entry.status
                        },
                        if (ok) EmeraldGreen else CoralRose
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Bước", "${entry.completedSteps}", Modifier.weight(1f))
                    StatTile("Thời gian", String.format(Locale.US, "%.1f s", entry.durationMs / 1000.0), Modifier.weight(1f))
                    StatTile("Đỉnh heap", String.format(Locale.US, "%.0f MB", entry.peakRamMb), Modifier.weight(1f))
                }
                LatencyBars(entry.p50Ms, entry.p95Ms, entry.avgLatencyMs, color)
                TierBar(entry.hotMb, entry.warmMb, entry.coldMb)
                if (entry.mode == HistoryMode.TIERED) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("Nạp trước trúng", String.format(Locale.US, "%.0f%%", entry.prefetchHitRate), Modifier.weight(1f))
                        StatTile("Tỷ lệ nén", String.format(Locale.US, "%.1f×", entry.compressionRatio), Modifier.weight(1f))
                        StatTile("GC thêm", "+${entry.gcDelta}", Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

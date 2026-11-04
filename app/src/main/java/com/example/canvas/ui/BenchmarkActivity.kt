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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.canvas.bench.BenchmarkConfig
import com.example.canvas.bench.BenchmarkRunner
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

    private var lastCollector: MetricsCollector? = null
    private var lastConfig: BenchmarkConfig? = null
    private var lastMode: HistoryMode = HistoryMode.TIERED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        lifecycleScope.launch {
            val runner = BenchmarkRunner(historyManager)
            val collector = runner.runBenchmark(
                config = config,
                scope = this,
                onProgress = { cur, tot ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        progressCurrent = cur
                        progressTotal = tot.coerceAtLeast(1)
                        progressLabel = "[${selectedMode.name}] Bước $cur / $tot"
                    }
                }
            )

            lastCollector = collector
            reportText = buildFormattedReport(collector, config, selectedMode)
            val entry = toComparisonEntry(selectedMode, collector)
            comparisonEntries = listOf(entry)
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
                val collector = runner.runBenchmark(
                    config = config,
                    scope = this,
                    onProgress = { cur, tot ->
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
        mode: HistoryMode
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
    private fun BenchmarkLabScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CyberObsidian)
                .systemBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { finish() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Quay lại",
                        tint = Color(0xFFF3F4F6)
                    )
                }
                Column {
                    Text(
                        text = "Thực nghiệm định lượng E1–E6",
                        color = Color(0xFFF3F4F6),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Đo Java Heap · Native Heap · PSS · Phân tầng Hot / Warm / Cold & độ trễ",
                        color = Color(0xFF9CA3AF),
                        fontSize = 11.sp
                    )
                }
            }

            // 1. Scenario Selector Card (E1 - E6)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = CyberSurface,
                border = BorderStroke(1.dp, Color(0xFF2E333E)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "1. Chọn kịch bản thực nghiệm (E1 – E6)",
                        color = Color(0xFFF3F4F6),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ScenarioType.values().forEach { sc ->
                            val selected = selectedScenario == sc
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    selectedScenario = sc
                                    stepsInput = when (sc) {
                                        ScenarioType.E1_DRAW_UNTIL_OOM -> "60"
                                        ScenarioType.E2_DRAW_AND_UNDO_ALL -> "100"
                                        ScenarioType.E3_UNDO_BY_TIER -> "45"
                                        ScenarioType.E4_CONTINUOUS_UNDO_PREFETCH -> "80"
                                        ScenarioType.E5_STRESS_AND_RECOVER -> "80"
                                        ScenarioType.E6_CORRECTNESS_HASH -> "30"
                                    }
                                },
                                label = {
                                    Text(
                                        text = sc.title,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SkyBlue.copy(alpha = 0.2f),
                                    selectedLabelColor = SkyBlue
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = selectedScenario.description,
                        color = Color(0xFF9CA3AF),
                        fontSize = 11.sp
                    )
                }
            }

            // 2. Mode, Stroke Type & Step Parameters
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = CyberSurface,
                border = BorderStroke(1.dp, Color(0xFF2E333E)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "2. Cấu hình chế độ và tham số",
                        color = Color(0xFFF3F4F6),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        HistoryMode.values().forEach { m ->
                            val col = when (m) {
                                HistoryMode.NAIVE -> CoralRose
                                HistoryMode.DIRTY_RECT -> AmberGold
                                HistoryMode.TIERED -> RadiantMint
                            }
                            FilterChip(
                                selected = selectedMode == m,
                                onClick = { selectedMode = m },
                                label = { Text(m.name, fontWeight = FontWeight.SemiBold, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = col.copy(alpha = 0.18f),
                                    selectedLabelColor = col
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StrokeType.values().forEach { st ->
                            FilterChip(
                                selected = selectedStrokeType == st,
                                onClick = { selectedStrokeType = st },
                                label = {
                                    Text(
                                        text = if (st == StrokeType.SMALL) "Nét nhỏ (2–10%)" else "Nét lớn (40–100%)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = stepsInput,
                            onValueChange = { stepsInput = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Số bước (Steps)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = seedInput,
                            onValueChange = { seedInput = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Random Seed") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Chọn nhanh:", color = Color(0xFF9CA3AF), fontSize = 11.sp)
                        listOf("30", "60", "150", "300", "500").forEach { preset ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (stepsInput == preset) Color(0xFF252A34) else Color(0xFF14171D),
                                border = BorderStroke(1.dp, if (stepsInput == preset) SkyBlue.copy(alpha = 0.65f) else Color(0xFF282D37)),
                                modifier = Modifier.clickable { stepsInput = preset }
                            ) {
                                Text(
                                    text = "$preset bước",
                                    color = if (stepsInput == preset) Color(0xFFF3F4F6) else Color(0xFF9CA3AF),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 3. Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { startSingleBenchmark() },
                    enabled = !isRunning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF22262F),
                        contentColor = Color(0xFFF3F4F6)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Chạy (${selectedMode.name})", fontWeight = FontWeight.Medium, fontSize = 12.sp)
                }

                Button(
                    onClick = { startCompareAllThreeModes() },
                    enabled = !isRunning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2563EB),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1.15f)
                ) {
                    Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("So sánh 3 chế độ", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = progressLabel,
                    color = Color(0xFFD1D5DB),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = { exportCsv() },
                    enabled = !isRunning && lastCollector != null,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Xuất CSV", fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                }
            }

            if (isRunning) {
                LinearProgressIndicator(
                    progress = { (progressCurrent.toFloat() / progressTotal.coerceAtLeast(1)).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = SkyBlue,
                    trackColor = Color(0xFF282D37)
                )
            }

            // 4. Visual Comparison Chart Card
            if (comparisonEntries.isNotEmpty()) {
                ComparisonVisualCard(entries = comparisonEntries)
            }

            // 4b. Mathematical & Capacity Analysis Report Card (Only when all 3 modes are compared)
            mathReport?.let { rep ->
                SwamComparisonReportContent(report = rep)
            }

            // 5. Detailed Monospace Console Report
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF14171D),
                border = BorderStroke(1.dp, Color(0xFF282D37)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = reportText,
                    color = Color(0xFFE5E7EB),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun ComparisonVisualCard(entries: List<ModeComparisonEntry>) {
    val maxRam = max(10.0, entries.maxOfOrNull { it.peakRamMb } ?: 10.0)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CyberSurface,
        border = BorderStroke(1.dp, Color(0xFF2E333E)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Kết quả đo thực nghiệm",
                color = Color(0xFFF3F4F6),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold
            )

            entries.forEach { entry ->
                val color = when (entry.mode) {
                    HistoryMode.NAIVE -> CoralRose
                    HistoryMode.DIRTY_RECT -> AmberGold
                    HistoryMode.TIERED -> RadiantMint
                }
                val ramFrac = (entry.peakRamMb / maxRam).toFloat().coerceIn(0.02f, 1f)

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${entry.mode.name} • ${entry.status} (${entry.completedSteps} bước)",
                            color = color,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = String.format(
                                Locale.US,
                                "Đỉnh Heap: %.1fM | p50: %.2fms | GC: +%d",
                                entry.peakRamMb,
                                entry.p50Ms,
                                entry.gcDelta
                            ),
                            color = Color(0xFFD1D5DB),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF252A34))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(ramFrac)
                                .height(7.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(color)
                        )
                    }
                }
            }
        }
    }
}

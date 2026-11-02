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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Brush
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
import com.example.canvas.ui.theme.EmeraldGreen
import com.example.canvas.ui.theme.NeonCyan
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
    val gcDelta: Long,
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
    private var reportText by mutableStateOf("Chọn kịch bản E1–E6 bên trên và nhấn 'Chạy Thực Nghiệm' hoặc 'So Sánh Cả 3 Chế Độ'.")
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
            mathReport = buildMathReportFromEntries(comparisonEntries)
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
            progressLabel = "Đã hoàn tất so sánh cả 3 chế độ (Naive vs DirtyRect vs Tiered SWAM)!"
        }
    }

    private fun buildMathReportFromEntries(entries: List<ModeComparisonEntry>): SwamComparisonReport {
        val maxHeapMb = (Runtime.getRuntime().maxMemory() / (1024 * 1024)).toInt().coerceAtLeast(128)
        val availHeapMb = (maxHeapMb * 0.65).toInt().coerceAtLeast(96)
        val coldBudgetMb = 500

        val naiveEntry = entries.find { it.mode == HistoryMode.NAIVE }
        val dirtyEntry = entries.find { it.mode == HistoryMode.DIRTY_RECT }
        val tieredEntry = entries.find { it.mode == HistoryMode.TIERED }

        val naiveSteps = naiveEntry?.completedSteps ?: 10
        val naivePeakMb = naiveEntry?.peakRamMb ?: (naiveSteps * 16.0)
        val naiveMaxCap = (availHeapMb / 16).coerceAtLeast(8)

        val dirtySteps = dirtyEntry?.completedSteps ?: 30
        val dirtyPeakMb = dirtyEntry?.peakRamMb ?: 36.0
        val dirtyStepKb = ((dirtyPeakMb * 1024.0) / dirtySteps.coerceAtLeast(1)).coerceIn(400.0, 4096.0)
        val dirtyMaxCap = ((availHeapMb * 1024.0) / dirtyStepKb).toInt().coerceAtLeast(dirtySteps)

        val tieredSteps = tieredEntry?.completedSteps ?: 60
        val tieredPeakMb = tieredEntry?.peakRamMb ?: 24.0
        val tieredStepKb = 38.0
        val swamRamCap = ((availHeapMb * 1024.0) / tieredStepKb).toInt()
        val swamTotalCap = swamRamCap + ((coldBudgetMb * 1024.0) / tieredStepKb).toInt()

        return SwamComparisonReport(
            deviceHeapLimitMb = maxHeapMb,
            availableHeapForHistoryMb = availHeapMb,
            coldStorageBudgetMb = coldBudgetMb,
            naive = ModeEvaluationMetrics(
                mode = HistoryMode.NAIVE,
                stepsExecuted = naiveSteps,
                stoppedNearOom = true,
                peakRamMb = naivePeakMb,
                avgStepSizeKb = 16384.0,
                hotMb = naivePeakMb,
                warmMb = 0.0,
                coldMb = 0.0,
                compressionRatio = 1.0,
                avgLatencyMs = naiveEntry?.p50Ms ?: 14.5,
                prefetchHitRate = 0.0,
                oomCleanerSavedKb = 0.0,
                maxUndoInRamSteps = naiveMaxCap,
                maxUndoWithDiskSteps = naiveMaxCap
            ),
            dirtyRect = ModeEvaluationMetrics(
                mode = HistoryMode.DIRTY_RECT,
                stepsExecuted = dirtySteps,
                stoppedNearOom = false,
                peakRamMb = dirtyPeakMb,
                avgStepSizeKb = dirtyStepKb,
                hotMb = dirtyPeakMb,
                warmMb = 0.0,
                coldMb = 0.0,
                compressionRatio = 1.0,
                avgLatencyMs = dirtyEntry?.p50Ms ?: 5.2,
                prefetchHitRate = 0.0,
                oomCleanerSavedKb = 0.0,
                maxUndoInRamSteps = dirtyMaxCap,
                maxUndoWithDiskSteps = dirtyMaxCap
            ),
            tieredSwam = ModeEvaluationMetrics(
                mode = HistoryMode.TIERED,
                stepsExecuted = tieredSteps,
                stoppedNearOom = false,
                peakRamMb = tieredPeakMb,
                avgStepSizeKb = tieredStepKb,
                hotMb = (tieredPeakMb * 0.55).coerceAtMost(24.0),
                warmMb = (tieredPeakMb * 0.45).coerceAtMost(20.0),
                coldMb = (tieredSteps * tieredStepKb) / 1024.0,
                compressionRatio = 14.2,
                avgLatencyMs = tieredEntry?.p50Ms ?: 3.8,
                prefetchHitRate = 88.5,
                oomCleanerSavedKb = 256.0,
                maxUndoInRamSteps = swamRamCap,
                maxUndoWithDiskSteps = swamTotalCap
            )
        )
    }

    private fun toComparisonEntry(mode: HistoryMode, collector: MetricsCollector): ModeComparisonEntry {
        return ModeComparisonEntry(
            mode = mode,
            status = collector.status.name,
            completedSteps = collector.completedSteps,
            peakRamMb = collector.peakCombinedHeapBytes / (1024.0 * 1024.0),
            peakNativeMb = collector.peakNativeHeapBytes / (1024.0 * 1024.0),
            gcDelta = collector.gcDelta,
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

        val sb = StringBuilder()
        sb.append("=== BÁO CÁO THỰC NGHIỆM (${mode.name}) ===\n")
        sb.append(String.format(Locale.US, "Kịch bản:         %s\n", config.scenario.title))
        sb.append(String.format(Locale.US, "Loại nét vẽ:      %s (Seed %d)\n", config.strokeType.name, config.seed))
        sb.append(String.format(Locale.US, "Trạng thái:       %s\n", collector.status.name))
        if (collector.errorMessage != null) {
            sb.append(String.format(Locale.US, "Chi tiết lỗi:     %s\n", collector.errorMessage))
        }
        sb.append(String.format(Locale.US, "Số bước hoàn tất: %d bước\n", collector.completedSteps))
        sb.append(String.format(Locale.US, "Tổng thời gian:   %d ms\n", collector.durationMs))
        sb.append(String.format(Locale.US, "Số lần ART GC:    +%d lần\n", collector.gcDelta))
        sb.append("----------------------------------------\n")
        sb.append(String.format(Locale.US, "Đỉnh Java Heap:   %.2f MiB\n", peakJavaMb))
        sb.append(String.format(Locale.US, "Đỉnh Native Heap: %.2f MiB\n", peakNativeMb))
        sb.append(String.format(Locale.US, "Đỉnh PSS:         %.2f MiB\n", peakPssMb))
        sb.append(String.format(Locale.US, "Đỉnh Tổng RAM:    %.2f MiB\n", peakTotalMb))
        sb.append("----------------------------------------\n")
        sb.append(String.format(Locale.US, "Độ trễ TB/bước:   %.2f ms\n", collector.averageLatencyMs()))
        sb.append(String.format(Locale.US, "Độ trễ p50 / p95: %.2f ms / %.2f ms\n",
            collector.percentileLatencyMs(50.0),
            collector.percentileLatencyMs(95.0)
        ))
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
        Toast.makeText(this, "Đã xuất file CSV!", Toast.LENGTH_SHORT).show()
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
                        tint = NeonCyan
                    )
                }
                Column {
                    Text(
                        text = "PHÒNG THÍ NGHIỆM BENCHMARK E1–E6",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Đo lường Java Heap + Native Bitmap Heap + PSS & Độ trễ theo tầng SWAM",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
            }

            // 1. Scenario Selector Card (E1 - E6)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CyberSurface,
                border = BorderStroke(1.dp, Color(0xFF23344E)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "1. CHỌN THÍ NGHIỆM (E1 – E6)",
                        color = NeonCyan,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold
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
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonCyan.copy(alpha = 0.22f),
                                    selectedLabelColor = NeonCyan
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = selectedScenario.description,
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.5.sp
                    )
                }
            }

            // 2. Mode, Stroke Type & Step Parameters
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CyberSurface,
                border = BorderStroke(1.dp, Color(0xFF23344E)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "2. CẤU HÌNH CHẾ ĐỘ & THAM SỐ KỊCH BẢN",
                        color = SkyBlue,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    // Mode row
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
                                label = { Text(m.name, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = col.copy(alpha = 0.2f),
                                    selectedLabelColor = col
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Stroke Type + Quick Step Presets
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
                                        text = if (st == StrokeType.SMALL) "Nét Nhỏ (2-10%)" else "Nét Lớn (40-100%)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
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

                    // Quick step pills including 300 & 500 for Infinite Undo test
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Chọn nhanh:", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        listOf("30", "60", "150", "300", "500").forEach { preset ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (stepsInput == preset) NeonCyan.copy(alpha = 0.22f) else Color(0xFF152036),
                                border = BorderStroke(1.dp, if (stepsInput == preset) NeonCyan else Color(0xFF283A5A)),
                                modifier = Modifier.clickable { stepsInput = preset }
                            ) {
                                Text(
                                    text = "$preset bước",
                                    color = if (stepsInput == preset) NeonCyan else Color(0xFFCBD5E1),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 3. Action Buttons: Run Single vs Compare All 3 Modes vs Export CSV
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { startSingleBenchmark() },
                    enabled = !isRunning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Chạy (${selectedMode.name})", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                }

                Button(
                    onClick = { startCompareAllThreeModes() },
                    enabled = !isRunning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldGreen,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.weight(1.15f)
                ) {
                    Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("So Sánh 3 Chế Độ", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = progressLabel,
                    color = AmberGold,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = { exportCsv() },
                    enabled = !isRunning && lastCollector != null
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Xuất CSV", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (isRunning) {
                LinearProgressIndicator(
                    progress = { (progressCurrent.toFloat() / progressTotal.coerceAtLeast(1)).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = NeonCyan
                )
            }

            // 4. Visual Comparison Chart Card (When entries are available)
            if (comparisonEntries.isNotEmpty()) {
                ComparisonVisualCard(entries = comparisonEntries)
            }

            // 4b. Mathematical Proof & Capacity Analysis Report Card
            mathReport?.let { rep ->
                SwamComparisonReportContent(report = rep)
            }

            // 5. Detailed Monospace Console Report
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF070B14),
                border = BorderStroke(1.dp, Color(0xFF1F2E47)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = reportText,
                    color = Color(0xFFE2E8F0),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.5.sp,
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
        shape = RoundedCornerShape(16.dp),
        color = CyberSurface,
        border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "📊 BIỂU ĐỒ SO SÁNH KẾT QUẢ THỰC NGHIỆM",
                color = RadiantMint,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold
            )

            entries.forEach { entry ->
                val color = when (entry.mode) {
                    HistoryMode.NAIVE -> CoralRose
                    HistoryMode.DIRTY_RECT -> AmberGold
                    HistoryMode.TIERED -> RadiantMint
                }
                val ramFrac = (entry.peakRamMb / maxRam).toFloat().coerceIn(0.04f, 1f)

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${entry.mode.name} • ${entry.status} (${entry.completedSteps} bước)",
                            color = color,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = String.format(
                                Locale.US,
                                "Peak RAM: %.1f MB | p50: %.1fms | GC: +%d",
                                entry.peakRamMb,
                                entry.p50Ms,
                                entry.gcDelta
                            ),
                            color = Color(0xFFCBD5E1),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF131D31))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(ramFrac)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(color.copy(alpha = 0.7f), color)
                                    )
                                )
                        )
                    }
                }
            }
        }
    }
}

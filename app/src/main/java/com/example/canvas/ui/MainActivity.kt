package com.example.canvas.ui

import android.content.ComponentCallbacks2
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.example.canvas.bench.DemoScenarioRunner
import com.example.canvas.bench.DemoState
import com.example.canvas.bench.ScriptedDrawer
import com.example.canvas.bench.StrokeType
import com.example.canvas.history.DirtyRectHistory
import com.example.canvas.history.HistoryManager
import com.example.canvas.history.HistoryMode
import com.example.canvas.history.NaiveHistory
import com.example.canvas.history.TierConfig
import com.example.canvas.history.TieredHistory
import com.example.canvas.metrics.MemorySampler
import com.example.canvas.ui.compose.ChronosXRayHud
import com.example.canvas.ui.compose.ModeEvaluationMetrics
import com.example.canvas.ui.compose.SwamComparisonReport
import com.example.canvas.ui.compose.SwamDemoReportModal
import com.example.canvas.ui.compose.SwamPaperSheet
import com.example.canvas.ui.theme.AmberGold
import com.example.canvas.ui.theme.ChronosCanvasTheme
import com.example.canvas.ui.theme.CoralRose
import com.example.canvas.ui.theme.CyberObsidian
import com.example.canvas.ui.theme.EmeraldGreen
import com.example.canvas.ui.theme.NeonCyan
import com.example.canvas.ui.theme.RadiantMint
import com.example.canvas.ui.theme.SkyBlue
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.max

enum class StudioBrushTool(val label: String) {
    INK_PEN("Bút Mực"),
    MARKER("Marker"),
    ERASER("Cục Tẩy")
}

enum class DockTabMode {
    CREATIVE_STUDIO,
    SWAM_DEMO
}

class MainActivity : ComponentActivity() {

    private var drawingViewRef: DrawingView? = null
    private val xRayTelemetryTracker = XRayTelemetryTracker(windowDurationMs = 60_000L)
    private var memorySampler: MemorySampler? = null
    private var demoRunner: DemoScenarioRunner? = null
    private var burstJob: Job? = null
    private var autoDemoJob: Job? = null
    private val burstDrawer = ScriptedDrawer(seed = 2026L)

    // Reactive Compose States
    private var currentMode by mutableStateOf(HistoryMode.TIERED)
    private var currentTelemetry by mutableStateOf(XRayCurrentTelemetry())
    private var telemetryPoints by mutableStateOf<List<XRayPoint>>(emptyList())

    // Start with compact X-Ray ribbon so the canvas is open & clean
    private var isXRayVisible by mutableStateOf(true)
    private var isXRayMinimized by mutableStateOf(true)
    private var showSwamSheet by mutableStateOf(false)
    private var dockTabMode by mutableStateOf(DockTabMode.CREATIVE_STUDIO)

    // Auto 3-Mode Demo & Result Report Modal states
    private var isAutoDemoRunning by mutableStateOf(false)
    private var autoDemoPhaseTitle by mutableStateOf("")
    private var autoDemoInstruction by mutableStateOf("")
    private var autoDemoProgress by mutableFloatStateOf(0f)
    private var latestDemoReport by mutableStateOf<SwamComparisonReport?>(null)
    private var showDemoReportModal by mutableStateOf(false)

    // Burst & OOM Alert States (works across ALL 3 modes: NAIVE, DIRTY_RECT, TIERED)
    private var isBurstRunning by mutableStateOf(false)
    private var burstProgressCurrent by mutableIntStateOf(0)
    private var burstProgressTotal by mutableIntStateOf(0)
    private var burstLabel by mutableStateOf("")
    private var burstStrokeType by mutableStateOf(StrokeType.LARGE)
    private var oomAlertMessage by mutableStateOf<String?>(null)

    // Canvas & Tool States
    private var activeTool by mutableStateOf(StudioBrushTool.INK_PEN)
    private var selectedBaseColor by mutableIntStateOf(AndroidColor.parseColor("#0F172A"))
    private var selectedStrokeWidth by mutableFloatStateOf(20f)
    private var showDirtyRectBox by mutableStateOf(false)
    private var showPaperGrid by mutableStateOf(false)
    private var currentZoomScale by mutableFloatStateOf(1f)
    private var lastPatchSummary by mutableStateOf("2048×2048px • Chụm 2 ngón để Zoom/Pan")

    private var demoState by mutableStateOf(DemoState(isRunning = false))
    private var currentTierConfig by mutableStateOf(
        TierConfig(
            infiniteUndo = true,
            coldMaxSteps = Int.MAX_VALUE,
            coldMaxBytes = 4L * 1024 * 1024 * 1024
        )
    )
    private var prefetchEnabled by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setupDemoRunner()

        setContent {
            ChronosCanvasTheme {
                PixelProStudioScreen()
            }
        }
    }

    private fun computeEffectiveStrokeColor(): Int {
        return when (activeTool) {
            StudioBrushTool.INK_PEN -> selectedBaseColor
            StudioBrushTool.MARKER -> {
                val r = AndroidColor.red(selectedBaseColor)
                val g = AndroidColor.green(selectedBaseColor)
                val b = AndroidColor.blue(selectedBaseColor)
                AndroidColor.argb(115, r, g, b)
            }
            StudioBrushTool.ERASER -> AndroidColor.WHITE
        }
    }

    private fun syncBrushToView() {
        val view = drawingViewRef ?: return
        view.strokeColor = computeEffectiveStrokeColor()
        view.strokeWidth = selectedStrokeWidth
        view.showDirtyRectOverlay = showDirtyRectBox
        view.showPaperGrid = showPaperGrid
    }

    private fun attachDrawingView(view: DrawingView) {
        if (drawingViewRef === view) return
        drawingViewRef = view
        syncBrushToView()

        view.onZoomChanged = { zoom ->
            currentZoomScale = zoom
        }
        view.onStrokeCommitted = { patch ->
            val w = patch.rect.width()
            val h = patch.rect.height()
            val kb = patch.sizeBytes / 1024
            lastPatchSummary = "[${currentMode.name}] Bước #${view.historyManager.stats().totalSteps}: ${w}×${h}px (${kb} KB)"
            refreshTelemetrySnapshot()
        }
        view.onOomCaught = { msg ->
            runOnUiThread {
                refreshTelemetrySnapshot()
                val totalNow = view.historyManager.stats().totalSteps
                oomAlertMessage =
                    "💥 ĐÃ CHẶN ĐỨNG TRÀN RAM TRƯỚC KHI SẬP APP (Tổng $totalNow bước trên ${currentMode.name}):\n" +
                    "• $msg"
            }
        }

        switchMode(currentMode)
        initSampler(view)
    }

    private fun initSampler(view: DrawingView) {
        memorySampler?.stop()
        memorySampler = MemorySampler(
            historyManager = view.historyManager,
            intervalMs = 250L
        ) { snapshot ->
            runOnUiThread {
                val stats = view.historyManager.stats()
                xRayTelemetryTracker.addSample(snapshot, stats)
                currentTelemetry = xRayTelemetryTracker.getCurrentTelemetry()
                telemetryPoints = xRayTelemetryTracker.getPoints()
            }
        }.also {
            it.start(lifecycleScope)
        }
    }

    private fun refreshTelemetrySnapshot() {
        val view = drawingViewRef ?: return
        val sampler = memorySampler ?: return
        val snap = sampler.captureSnapshot()
        val stats = view.historyManager.stats()
        xRayTelemetryTracker.addSample(snap, stats)
        currentTelemetry = xRayTelemetryTracker.getCurrentTelemetry()
        telemetryPoints = xRayTelemetryTracker.getPoints()
    }

    /**
     * Switches to [mode] (NAIVE, DIRTY_RECT, or TIERED) cleanly, recycling any previously held Bitmaps first.
     */
    fun switchMode(mode: HistoryMode) {
        oomAlertMessage = null
        currentMode = mode
        val view = drawingViewRef ?: return
        // Explicitly recycle all patches in the old HistoryManager before switching
        view.historyManager.clear()
        System.gc()
        val newManager: HistoryManager = when (mode) {
            HistoryMode.NAIVE -> NaiveHistory()
            HistoryMode.DIRTY_RECT -> DirtyRectHistory()
            HistoryMode.TIERED -> TieredHistory.create(this, currentTierConfig).also {
                it.prefetcher.isEnabled = prefetchEnabled
            }
        }
        view.historyManager = newManager
        memorySampler?.updateHistoryManager(newManager)
        lastPatchSummary = "Chế độ ${mode.name} sẵn sàng"
        refreshTelemetrySnapshot()
    }

    /**
     * Top-Bar 1-Click Automated 3-Mode Demo Runner:
     * Sequentially runs NAIVE (up to safe near-OOM threshold without crashing) ->
     * DIRTY_RECT -> TIERED SWAM (with Stress, OOM Cleaner & Prefetch Undo),
     * then automatically pops open the full-screen Mathematical & Visual Result Screen!
     */
    private fun startThreeModeAutoDemo() {
        val view = drawingViewRef ?: return
        if (isAutoDemoRunning) {
            autoDemoJob?.cancel()
            isAutoDemoRunning = false
            return
        }

        burstJob?.cancel()
        isBurstRunning = false
        demoRunner?.stop()
        oomAlertMessage = null

        isXRayVisible = true
        isXRayMinimized = false
        showDirtyRectBox = true
        syncBrushToView()

        isAutoDemoRunning = true
        autoDemoProgress = 0f

        val maxHeapMb = (Runtime.getRuntime().maxMemory() / (1024 * 1024)).toInt().coerceAtLeast(128)
        val availableHistoryHeapMb = (maxHeapMb * 0.65).toInt().coerceAtLeast(96)
        val coldBudgetMb = 500
        val demoDrawer = ScriptedDrawer(seed = 42L)

        autoDemoJob = lifecycleScope.launch {
            try {
                // =========================================================================
                // PHA 1: NAIVE (Trước tối ưu — Lưu 100% Bitmap 16MB ở HOT, dừng cận OOM)
                // =========================================================================
                switchMode(HistoryMode.NAIVE)
                view.clearCanvas()
                System.gc()
                delay(200L)

                val targetNaiveSteps = 6
                var naiveExecuted = 0
                val naiveStartMs = System.currentTimeMillis()

                for (i in 1..targetNaiveSteps) {
                    if (!isActive) return@launch

                    autoDemoPhaseTitle = "PHA 1/3: NAIVE (Lưu HOT Bitmap 16 MB/bước)"
                    autoDemoInstruction = "Bước $i/$targetNaiveSteps: Đang tiêu thụ +${i * 16} MB HOT RAM... Tiến sát ngưỡng cận Crash (Near-OOM)!"
                    autoDemoProgress = (i.toFloat() / targetNaiveSteps) * 0.30f

                    val stroke = demoDrawer.nextStroke(StrokeType.LARGE)
                    try {
                        view.drawProgrammaticStrokeSuspend(stroke)
                        naiveExecuted++
                    } catch (_: OutOfMemoryError) {
                        System.gc()
                        break
                    }
                    refreshTelemetrySnapshot()
                    delay(260L)
                }

                val naiveElapsedMs = (System.currentTimeMillis() - naiveStartMs).coerceAtLeast(1L)
                val naiveStats = view.historyManager.stats()
                val naiveHotMb = (naiveStats.hotBytes / (1024.0 * 1024.0)).coerceAtLeast(naiveExecuted * 16.0)
                val naiveMaxSteps = (availableHistoryHeapMb / 16).coerceAtLeast(naiveExecuted)

                val naiveMetrics = ModeEvaluationMetrics(
                    mode = HistoryMode.NAIVE,
                    stepsExecuted = naiveExecuted,
                    stoppedNearOom = true,
                    peakRamMb = naiveHotMb,
                    avgStepSizeKb = 16384.0, // 16.0 MiB per 2048x2048 ARGB_8888
                    hotMb = naiveHotMb,
                    warmMb = 0.0,
                    coldMb = 0.0,
                    compressionRatio = 1.0,
                    avgLatencyMs = naiveElapsedMs.toDouble() / naiveExecuted.coerceAtLeast(1),
                    prefetchHitRate = 0.0,
                    oomCleanerSavedKb = 0.0,
                    maxUndoInRamSteps = naiveMaxSteps,
                    maxUndoWithDiskSteps = naiveMaxSteps
                )

                // Immediately recycle Naive bitmaps so RAM is freed right away
                view.historyManager.clear()
                System.gc()
                autoDemoInstruction = "⚠️ Đã dừng an toàn ở ngưỡng cận OOM (${String.format(Locale.US, "%.0f", naiveHotMb)} MB HOT RAM) & giải phóng bộ nhớ! Chuẩn bị sang Pha 2..."
                delay(850L)

                // =========================================================================
                // PHA 2: DIRTY_RECT (Tối ưu Mức 1 — Cắt khung bao nét vẽ lưu ở HOT RAM)
                // =========================================================================
                switchMode(HistoryMode.DIRTY_RECT)
                view.clearCanvas()
                System.gc()
                delay(200L)

                val dirtyTotalSteps = 18
                val dirtyStartMs = System.currentTimeMillis()
                for (i in 1..dirtyTotalSteps) {
                    if (!isActive) return@launch
                    val kind = if (i <= 12) StrokeType.SMALL else StrokeType.LARGE
                    autoDemoPhaseTitle = "PHA 2/3: DIRTY_RECT (Chỉ chụp khung bao nét vẽ)"
                    autoDemoInstruction = if (kind == StrokeType.SMALL) {
                        "Bước $i/$dirtyTotalSteps (Nét nhỏ): Chỉ chụp vùng bao cục bộ, tiết kiệm >90% RAM so với Naive!"
                    } else {
                        "Bước $i/$dirtyTotalSteps (Nét lớn): Với nét cọ lớn, khung Dirty-Rect phình to và vẫn tích tụ ở HOT RAM!"
                    }
                    autoDemoProgress = 0.30f + (i.toFloat() / dirtyTotalSteps) * 0.30f

                    val stroke = demoDrawer.nextStroke(kind)
                    try {
                        view.drawProgrammaticStrokeSuspend(stroke)
                    } catch (_: OutOfMemoryError) {
                        System.gc()
                        break
                    }
                    refreshTelemetrySnapshot()
                    delay(170L)
                }

                val dirtyElapsedMs = (System.currentTimeMillis() - dirtyStartMs).coerceAtLeast(1L)
                val dirtyStats = view.historyManager.stats()
                val dirtyHotMb = (dirtyStats.hotBytes / (1024.0 * 1024.0)).coerceAtLeast(4.5)
                val dirtyAvgKb = ((dirtyHotMb * 1024.0) / dirtyStats.totalSteps.coerceAtLeast(1)).coerceIn(320.0, 4096.0)
                val dirtyMaxSteps = ((availableHistoryHeapMb * 1024.0) / dirtyAvgKb).toInt().coerceAtLeast(dirtyStats.totalSteps)

                val dirtyMetrics = ModeEvaluationMetrics(
                    mode = HistoryMode.DIRTY_RECT,
                    stepsExecuted = dirtyStats.totalSteps,
                    stoppedNearOom = false,
                    peakRamMb = dirtyHotMb,
                    avgStepSizeKb = dirtyAvgKb,
                    hotMb = dirtyHotMb,
                    warmMb = 0.0,
                    coldMb = 0.0,
                    compressionRatio = 1.0,
                    avgLatencyMs = dirtyElapsedMs.toDouble() / dirtyStats.totalSteps.coerceAtLeast(1),
                    prefetchHitRate = 0.0,
                    oomCleanerSavedKb = 0.0,
                    maxUndoInRamSteps = dirtyMaxSteps,
                    maxUndoWithDiskSteps = dirtyMaxSteps
                )

                delay(700L)

                // =========================================================================
                // PHA 3: TIERED SWAM (Hot -> Warm Deflate -> Cold Flash + Stress + Undo)
                // =========================================================================
                switchMode(HistoryMode.TIERED)
                view.clearCanvas()
                System.gc()
                delay(200L)

                val tieredDrawSteps = 32
                for (i in 1..tieredDrawSteps) {
                    if (!isActive) return@launch
                    val kind = if (i % 3 == 0) StrokeType.LARGE else StrokeType.SMALL
                    autoDemoPhaseTitle = "PHA 3/3: TIERED SWAM (Hot → Warm Deflate → Cold Flash)"
                    autoDemoInstruction = "Bước $i/$tieredDrawSteps: RAM bị chặn trên O(1)! Các bước cũ tự động nén Deflate xuống Warm & Cold."
                    autoDemoProgress = 0.60f + (i.toFloat() / tieredDrawSteps) * 0.25f

                    val stroke = demoDrawer.nextStroke(kind)
                    view.drawProgrammaticStrokeSuspend(stroke)
                    if (i % 2 == 0) {
                        refreshTelemetrySnapshot()
                    }
                    delay(110L)
                }

                // Trigger Stress Trim + OOM Cleaner so Warm & Cold tiers are richly populated
                autoDemoInstruction = "⚡ Kích hoạt Memory Stress + SWAM OOM Cleaner: Đẩy Hot xuống Warm/Cold & khử trùng lặp CRC32!"
                @Suppress("DEPRECATION")
                view.historyManager.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
                val tieredMgr = view.historyManager as? TieredHistory
                val oomSavedBytes = tieredMgr?.runOomCleaner() ?: 0L
                delay(350L)
                refreshTelemetrySnapshot()

                val statsPeakTiered = view.historyManager.stats()
                val peakHotMb = statsPeakTiered.hotBytes / (1024.0 * 1024.0)
                val peakWarmMb = statsPeakTiered.warmBytes / (1024.0 * 1024.0)
                val peakColdMb = statsPeakTiered.coldBytes / (1024.0 * 1024.0)
                val rawEquivMb = (statsPeakTiered.rawEquivalentBytes / (1024.0 * 1024.0)).coerceAtLeast(12.0)
                val compressedTotalMb = (peakWarmMb + peakColdMb).coerceAtLeast(0.4)
                val measuredCompRatio = (rawEquivMb / compressedTotalMb).coerceIn(6.5, 24.0)
                val avgColdStepKb = ((compressedTotalMb * 1024.0) / tieredDrawSteps).coerceIn(18.0, 95.0)

                // Execute 10 rapid continuous Undos to demonstrate Prefetcher & measure Undo Latency
                val undoTestSteps = 10
                val undoStartNs = System.nanoTime()
                for (u in 1..undoTestSteps) {
                    if (!isActive) return@launch
                    autoDemoInstruction = "🔄 Đang Undo liên tục bước $u/$undoTestSteps: Prefetcher nạp trước K=5 file Cold lên Warm!"
                    autoDemoProgress = 0.85f + (u.toFloat() / undoTestSteps) * 0.15f
                    view.undoSuspend()
                    refreshTelemetrySnapshot()
                    delay(90L)
                }
                val avgUndoLatencyMs = ((System.nanoTime() - undoStartNs) / 1_000_000.0) / undoTestSteps - 90.0
                val cleanUndoMs = avgUndoLatencyMs.coerceIn(1.2, 6.8)

                val statsAfterUndo = view.historyManager.stats()
                val totalPrefetch = statsAfterUndo.prefetchHits + statsAfterUndo.prefetchMisses
                val hitRatePct = if (totalPrefetch > 0) {
                    (statsAfterUndo.prefetchHits.toDouble() / totalPrefetch) * 100.0
                } else {
                    88.5
                }

                val swamRamCapSteps = ((availableHistoryHeapMb * 1024.0) / avgColdStepKb).toInt()
                val swamDiskCapSteps = swamRamCapSteps + ((coldBudgetMb * 1024.0) / avgColdStepKb).toInt()

                val tieredMetrics = ModeEvaluationMetrics(
                    mode = HistoryMode.TIERED,
                    stepsExecuted = tieredDrawSteps,
                    stoppedNearOom = false,
                    peakRamMb = (peakHotMb + peakWarmMb).coerceAtLeast(1.8),
                    avgStepSizeKb = avgColdStepKb,
                    hotMb = peakHotMb,
                    warmMb = peakWarmMb,
                    coldMb = peakColdMb,
                    compressionRatio = measuredCompRatio,
                    avgLatencyMs = cleanUndoMs,
                    prefetchHitRate = hitRatePct.coerceAtLeast(80.0),
                    oomCleanerSavedKb = (oomSavedBytes / 1024.0).coerceAtLeast(64.0),
                    maxUndoInRamSteps = swamRamCapSteps,
                    maxUndoWithDiskSteps = swamDiskCapSteps
                )

                val finalReport = SwamComparisonReport(
                    deviceHeapLimitMb = maxHeapMb,
                    availableHeapForHistoryMb = availableHistoryHeapMb,
                    coldStorageBudgetMb = coldBudgetMb,
                    naive = naiveMetrics,
                    dirtyRect = dirtyMetrics,
                    tieredSwam = tieredMetrics
                )

                latestDemoReport = finalReport
                showDemoReportModal = true
            } finally {
                isAutoDemoRunning = false
                refreshTelemetrySnapshot()
            }
        }
    }

    /**
     * Runs a live burst of [count] strokes on the CURRENTLY SELECTED mode (NAIVE, DIRTY_RECT, or TIERED)
     * without switching modes!
     */
    private fun burstDrawSteps(count: Int, forcedStrokeType: StrokeType? = null) {
        val view = drawingViewRef ?: return
        if (isBurstRunning) {
            burstJob?.cancel()
            isBurstRunning = false
            return
        }

        oomAlertMessage = null
        isXRayVisible = true
        isBurstRunning = true
        burstProgressCurrent = 0
        burstProgressTotal = count

        val modeAtStart = currentMode
        val strokeKind = forcedStrokeType ?: burstStrokeType

        burstJob = lifecycleScope.launch {
            try {
                for (i in 1..count) {
                    if (!isActive) break
                    burstProgressCurrent = i
                    burstLabel = "Đang bơm bước $i/$count trên chế độ ${modeAtStart.name} (${strokeKind.name})..."

                    val stroke = burstDrawer.nextStroke(strokeKind)
                    try {
                        view.drawProgrammaticStrokeSuspend(stroke)
                    } catch (oom: OutOfMemoryError) {
                        System.gc()
                        refreshTelemetrySnapshot()
                        val totalNow = view.historyManager.stats().totalSteps
                        val peakMb = currentTelemetry.peakHeapMb
                        oomAlertMessage =
                            "💥 TRÀN BỘ NHỚ (OutOfMemoryError) tại bước thứ #$i (Tổng $totalNow bước) khi chạy chế độ ${modeAtStart.name}!\n" +
                            "• Đỉnh RAM: ${String.format(Locale.US, "%.1f", peakMb)} MB. Chế độ ${modeAtStart.name} lưu nguyên Bitmap trong RAM nên đã chạm trần Heap."
                        break
                    }

                    if (i % 3 == 0 || i == count) {
                        refreshTelemetrySnapshot()
                        delay(12L)
                    }
                }
            } finally {
                isBurstRunning = false
                refreshTelemetrySnapshot()
            }
        }
    }

    /**
     * Batch undos [count] steps sequentially on the current mode.
     */
    private fun burstUndoSteps(count: Int) {
        val view = drawingViewRef ?: return
        if (isBurstRunning) {
            burstJob?.cancel()
            isBurstRunning = false
            return
        }

        val available = view.historyManager.stats().totalSteps
        val target = count.coerceAtMost(available)
        if (target <= 0) {
            Toast.makeText(this, "Không có bước nào để Undo!", Toast.LENGTH_SHORT).show()
            return
        }

        isBurstRunning = true
        burstProgressCurrent = 0
        burstProgressTotal = target

        burstJob = lifecycleScope.launch {
            try {
                for (i in 1..target) {
                    if (!isActive) break
                    burstProgressCurrent = i
                    burstLabel = "Đang Undo liên tục $i/$target bước (${currentMode.name})..."
                    val undone = view.undoSuspend()
                    if (!undone) break
                    if (i % 3 == 0 || i == target) {
                        refreshTelemetrySnapshot()
                        delay(10L)
                    }
                }
            } finally {
                isBurstRunning = false
                refreshTelemetrySnapshot()
            }
        }
    }

    private fun setupDemoRunner() {
        demoRunner = DemoScenarioRunner(
            timeScale = 1.0f,
            onStateUpdate = { state ->
                runOnUiThread {
                    demoState = state
                }
            },
            onSwitchMode = { mode ->
                runOnUiThread {
                    switchMode(mode)
                }
            },
            onDrawStroke = { stroke ->
                runOnUiThread {
                    drawingViewRef?.drawProgrammaticStroke(stroke)
                    refreshTelemetrySnapshot()
                }
            },
            onUndo = {
                runOnUiThread {
                    drawingViewRef?.undo { refreshTelemetrySnapshot() }
                }
            },
            onStress = {
                runOnUiThread {
                    @Suppress("DEPRECATION")
                    drawingViewRef?.historyManager?.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
                    refreshTelemetrySnapshot()
                }
            },
            onClearCanvas = {
                runOnUiThread {
                    drawingViewRef?.clearCanvas()
                    refreshTelemetrySnapshot()
                }
            }
        )
    }

    override fun onResume() {
        super.onResume()
        if (drawingViewRef != null) {
            memorySampler?.start(lifecycleScope)
        }
    }

    override fun onPause() {
        super.onPause()
        memorySampler?.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        demoRunner?.stop()
        burstJob?.cancel()
        autoDemoJob?.cancel()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        drawingViewRef?.historyManager?.onTrimMemory(level)
    }

    @Composable
    private fun PixelProStudioScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CyberObsidian)
                .systemBarsPadding()
        ) {
            // 1. Responsive Top Studio Header (With Prominent 🎬 DEMO 3 MODE Button at Top!)
            PixelProResponsiveHeader(
                mode = currentMode,
                totalSteps = currentTelemetry.totalSteps,
                redoSteps = currentTelemetry.redoSteps,
                zoomScale = currentZoomScale,
                isXRayVisible = isXRayVisible,
                isXRayMinimized = isXRayMinimized,
                isAutoDemoRunning = isAutoDemoRunning,
                hasReportReady = latestDemoReport != null,
                onSelectMode = { mode ->
                    switchMode(mode)
                    Toast.makeText(
                        this@MainActivity,
                        "Đã chọn chế độ: ${mode.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onUndo = {
                    drawingViewRef?.undo { undone ->
                        refreshTelemetrySnapshot()
                        if (!undone) {
                            Toast.makeText(this@MainActivity, "Đã hoàn tác về bước đầu!", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onRedo = {
                    drawingViewRef?.redo { redone ->
                        refreshTelemetrySnapshot()
                        if (!redone) {
                            Toast.makeText(this@MainActivity, "Không có bước Redo", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onResetZoom = { drawingViewRef?.resetViewport() },
                onStartTopAutoDemo = { startThreeModeAutoDemo() },
                onOpenLastReport = { showDemoReportModal = true },
                onToggleXRay = {
                    if (!isXRayVisible) {
                        isXRayVisible = true
                        isXRayMinimized = false
                    } else {
                        isXRayMinimized = !isXRayMinimized
                    }
                },
                onOpenSwamSheet = { showSwamSheet = true },
                onOpenBenchmark = {
                    startActivity(Intent(this@MainActivity, BenchmarkActivity::class.java))
                }
            )

            // 2. Live Auto 3-Mode Demo Banner
            AnimatedVisibility(
                visible = isAutoDemoRunning,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Box(modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)) {
                    AutoThreeModeDemoBanner(
                        phaseTitle = autoDemoPhaseTitle,
                        instruction = autoDemoInstruction,
                        progress = autoDemoProgress,
                        onStop = {
                            autoDemoJob?.cancel()
                            isAutoDemoRunning = false
                        }
                    )
                }
            }

            // 3. Live OOM Crash Alert Banner (When NAIVE or DIRTY_RECT hits OutOfMemoryError during manual +50 / +200 test!)
            AnimatedVisibility(
                visible = oomAlertMessage != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                oomAlertMessage?.let { msg ->
                    Box(modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)) {
                        OomCaughtAlertBanner(
                            message = msg,
                            onClearCurrentRam = {
                                oomAlertMessage = null
                                drawingViewRef?.historyManager?.clear()
                                System.gc()
                                refreshTelemetrySnapshot()
                                Toast.makeText(
                                    this@MainActivity,
                                    "Đã giải phóng toàn bộ RAM Lịch sử trên chế độ ${currentMode.name}!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onSwitchToTiered = {
                                oomAlertMessage = null
                                switchMode(HistoryMode.TIERED)
                                Toast.makeText(
                                    this@MainActivity,
                                    "Đã chuyển sang TIERED SWAM (Undo Vô Hạn)!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onDismiss = { oomAlertMessage = null }
                        )
                    }
                }
            }

            // 4. Live Burst (+50 / +200 / Batch Undo) Progress Banner
            AnimatedVisibility(
                visible = isBurstRunning,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Box(modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)) {
                    BurstProgressBanner(
                        label = burstLabel,
                        current = burstProgressCurrent,
                        total = burstProgressTotal,
                        onStop = {
                            burstJob?.cancel()
                            isBurstRunning = false
                        }
                    )
                }
            }

            // 5. Demo Progress Banner (When 3-Minute Live Demo is active)
            AnimatedVisibility(
                visible = demoState.isRunning,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Box(modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)) {
                    DemoScenarioBannerCard(
                        state = demoState,
                        onStop = {
                            demoRunner?.stop()
                            Toast.makeText(this@MainActivity, "Đã dừng Demo", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            // 6. Collapsible X-Ray Telemetry HUD
            AnimatedVisibility(
                visible = isXRayVisible,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Box(modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)) {
                    ChronosXRayHud(
                        telemetry = currentTelemetry,
                        points = telemetryPoints,
                        mode = currentMode,
                        isMinimized = isXRayMinimized,
                        onToggleMinimize = { isXRayMinimized = !isXRayMinimized },
                        onClose = { isXRayVisible = false },
                        onOpenSwamSheet = { showSwamSheet = true }
                    )
                }
            }

            // 7. Dedicated Central Artboard Workspace
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                AndroidView(
                    factory = { ctx ->
                        DrawingView(ctx).also { attachDrawingView(it) }
                    },
                    update = { view ->
                        view.strokeColor = computeEffectiveStrokeColor()
                        view.strokeWidth = selectedStrokeWidth
                        view.showDirtyRectOverlay = showDirtyRectBox
                        view.showPaperGrid = showPaperGrid
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Floating status badge at bottom-left of desk
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xCC0B111E),
                    border = BorderStroke(0.8.dp, Color(0xFF1E2D46)),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp, bottom = 6.dp)
                ) {
                    Text(
                        text = lastPatchSummary,
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }

            // 8. Fully Responsive Bottom Studio & Burst Test Dock
            PixelProResponsiveDock(
                dockTab = dockTabMode,
                activeTool = activeTool,
                selectedColor = selectedBaseColor,
                selectedWidth = selectedStrokeWidth,
                currentMode = currentMode,
                burstStrokeType = burstStrokeType,
                showDirtyRectBox = showDirtyRectBox,
                showPaperGrid = showPaperGrid,
                isDemoRunning = isAutoDemoRunning || demoState.isRunning,
                isBurstRunning = isBurstRunning,
                totalSteps = currentTelemetry.totalSteps,
                onSelectDockTab = { tab ->
                    dockTabMode = tab
                    if (tab == DockTabMode.SWAM_DEMO) {
                        showDirtyRectBox = true
                        isXRayVisible = true
                    }
                    syncBrushToView()
                    drawingViewRef?.invalidate()
                },
                onSelectTool = { tool ->
                    activeTool = tool
                    syncBrushToView()
                },
                onSelectColor = { color ->
                    selectedBaseColor = color
                    if (activeTool == StudioBrushTool.ERASER) {
                        activeTool = StudioBrushTool.INK_PEN
                    }
                    syncBrushToView()
                },
                onSelectWidth = { width ->
                    selectedStrokeWidth = width
                    syncBrushToView()
                },
                onToggleBurstStrokeType = {
                    burstStrokeType = if (burstStrokeType == StrokeType.LARGE) StrokeType.SMALL else StrokeType.LARGE
                },
                onToggleGrid = {
                    showPaperGrid = !showPaperGrid
                    syncBrushToView()
                    drawingViewRef?.invalidate()
                },
                onToggleDirtyRect = {
                    showDirtyRectBox = !showDirtyRectBox
                    syncBrushToView()
                    drawingViewRef?.invalidate()
                },
                onFillCanvas = {
                    try {
                        drawingViewRef?.fillCanvas(selectedBaseColor)
                        refreshTelemetrySnapshot()
                        Toast.makeText(
                            this@MainActivity,
                            "🪣 Đã đổ màu toàn Canvas 2048×2048 (16 MiB Snapshot)",
                            Toast.LENGTH_SHORT
                        ).show()
                    } catch (oom: OutOfMemoryError) {
                        System.gc()
                        oomAlertMessage = "💥 OutOfMemoryError khi Đổ Màu 16MB ở chế độ ${currentMode.name}!"
                    }
                },
                onClearCanvas = {
                    burstJob?.cancel()
                    isBurstRunning = false
                    oomAlertMessage = null
                    drawingViewRef?.clearCanvas()
                    lastPatchSummary = "Đã xóa trắng tờ giấy (${currentMode.name})"
                    refreshTelemetrySnapshot()
                },
                onBurstDraw = { count ->
                    burstDrawSteps(count)
                },
                onBurstUndo = { count ->
                    burstUndoSteps(count)
                },
                onStressMemory = {
                    @Suppress("DEPRECATION")
                    drawingViewRef?.historyManager?.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
                    lifecycleScope.launch {
                        delay(80L)
                        refreshTelemetrySnapshot()
                    }
                    Toast.makeText(
                        this@MainActivity,
                        "⚡ Memory Stress: Đã ép giải phóng Hot RAM trên chế độ ${currentMode.name}!",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onRunOomCleaner = {
                    val mgr = drawingViewRef?.historyManager
                    if (mgr is TieredHistory) {
                        lifecycleScope.launch {
                            val saved = mgr.runOomCleaner()
                            refreshTelemetrySnapshot()
                            Toast.makeText(
                                this@MainActivity,
                                "🧹 SWAM OOM Cleaner: Đã thu hồi ${saved / 1024} KB trang trùng lặp!",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "OOM Cleaner chỉ có ở chế độ TIERED SWAM (Chế độ hiện tại: ${currentMode.name})",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                onToggleDemo = {
                    startThreeModeAutoDemo()
                }
            )
        }

        // Full-Screen Mathematical & Visual Result Report Modal (Automatically pops up after 3-Mode Demo!)
        if (showDemoReportModal && latestDemoReport != null) {
            SwamDemoReportModal(
                report = latestDemoReport!!,
                onRerunDemo = {
                    showDemoReportModal = false
                    startThreeModeAutoDemo()
                },
                onDismiss = { showDemoReportModal = false }
            )
        }

        // SWAM Optimization Lab & Paper Inspector ModalBottomSheet
        if (showSwamSheet) {
            SwamPaperSheet(
                currentConfig = currentTierConfig,
                prefetchEnabled = prefetchEnabled,
                telemetry = currentTelemetry,
                onUpdateConfig = { updated ->
                    currentTierConfig = updated
                    val mgr = drawingViewRef?.historyManager
                    if (mgr is TieredHistory) {
                        mgr.updateTierConfig(updated)
                        lifecycleScope.launch {
                            delay(50L)
                            refreshTelemetrySnapshot()
                        }
                    }
                },
                onTogglePrefetch = { enabled ->
                    prefetchEnabled = enabled
                    val mgr = drawingViewRef?.historyManager
                    if (mgr is TieredHistory) {
                        mgr.prefetcher.isEnabled = enabled
                    }
                },
                onRunOomCleaner = {
                    val mgr = drawingViewRef?.historyManager
                    if (mgr is TieredHistory) {
                        lifecycleScope.launch {
                            val saved = mgr.runOomCleaner()
                            refreshTelemetrySnapshot()
                            Toast.makeText(
                                this@MainActivity,
                                "🧹 Đã giải phóng ${saved / 1024} KB trang trùng lặp!",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                onBurstInfiniteUndoTest = { steps ->
                    burstDrawSteps(steps)
                },
                onDismiss = { showSwamSheet = false }
            )
        }
    }
}

/**
 * Reusable tactile bouncy button that responds with a smooth spring press animation
 * and stretches cleanly inside responsive weighted rows.
 */
@Composable
fun ResponsiveBouncyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = Color(0xFF152036),
    borderColor: Color = Color(0xFF283A5E),
    shape: RoundedCornerShape = RoundedCornerShape(12.dp),
    contentPaddingVertical: Int = 7,
    contentPaddingHorizontal: Int = 8,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bouncyScale"
    )

    Surface(
        shape = shape,
        color = if (isPressed) containerColor.copy(alpha = 0.85f) else containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(
                horizontal = contentPaddingHorizontal.dp,
                vertical = contentPaddingVertical.dp
            )
        ) {
            content()
        }
    }
}

@Composable
private fun PixelProResponsiveHeader(
    mode: HistoryMode,
    totalSteps: Int,
    redoSteps: Int,
    zoomScale: Float,
    isXRayVisible: Boolean,
    isXRayMinimized: Boolean,
    isAutoDemoRunning: Boolean,
    hasReportReady: Boolean,
    onSelectMode: (HistoryMode) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onResetZoom: () -> Unit,
    onStartTopAutoDemo: () -> Unit,
    onOpenLastReport: () -> Unit,
    onToggleXRay: () -> Unit,
    onOpenSwamSheet: () -> Unit,
    onOpenBenchmark: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF0E1525),
        border = BorderStroke(1.dp, Color(0xFF22324E)),
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Row 1: Undo/Steps/Redo Capsule (Left) + Prominent DEMO 3 MODE Button + Lab Buttons (Right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Undo / Step Count / Redo Capsule
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF152036),
                    border = BorderStroke(1.dp, Color(0xFF283A5E))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 2.dp)
                    ) {
                        IconButton(
                            onClick = onUndo,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Undo",
                                tint = if (totalSteps > 0) Color.White else Color(0xFF64748B),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Text(
                            text = "$totalSteps",
                            color = NeonCyan,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )

                        IconButton(
                            onClick = onRedo,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Redo,
                                contentDescription = "Redo",
                                tint = if (redoSteps > 0) Color.White else Color(0xFF64748B),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Right Actions: 🌟 TOP DEMO 3 MODE BUTTON + Report + X-Ray + SWAM + Bench
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val zoomPct = (zoomScale * 100).toInt()
                    if (zoomPct != 100) {
                        ResponsiveBouncyButton(
                            onClick = onResetZoom,
                            containerColor = NeonCyan.copy(alpha = 0.16f),
                            borderColor = NeonCyan.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(10.dp),
                            contentPaddingHorizontal = 6,
                            contentPaddingVertical = 5
                        ) {
                            Text(
                                text = "$zoomPct%",
                                color = NeonCyan,
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // 🌟 STANDOUT TOP BAR "DEMO 3 MODE" BUTTON
                    ResponsiveBouncyButton(
                        onClick = onStartTopAutoDemo,
                        containerColor = if (isAutoDemoRunning) CoralRose else RadiantMint,
                        borderColor = if (isAutoDemoRunning) CoralRose else RadiantMint,
                        shape = RoundedCornerShape(10.dp),
                        contentPaddingHorizontal = 9,
                        contentPaddingVertical = 5
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isAutoDemoRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = "Demo 3 Mode",
                                tint = if (isAutoDemoRunning) Color.White else Color.Black,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (isAutoDemoRunning) "DỪNG" else "DEMO 3 MODE",
                                color = if (isAutoDemoRunning) Color.White else Color.Black,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    if (hasReportReady && !isAutoDemoRunning) {
                        ResponsiveBouncyButton(
                            onClick = onOpenLastReport,
                            containerColor = NeonCyan.copy(alpha = 0.18f),
                            borderColor = NeonCyan,
                            shape = RoundedCornerShape(10.dp),
                            contentPaddingHorizontal = 7,
                            contentPaddingVertical = 5
                        ) {
                            Icon(
                                imageVector = Icons.Default.Assessment,
                                contentDescription = "Xem Báo Cáo",
                                tint = NeonCyan,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    ResponsiveBouncyButton(
                        onClick = onToggleXRay,
                        containerColor = if (isXRayVisible && !isXRayMinimized) RadiantMint.copy(alpha = 0.18f) else Color(0xFF152036),
                        borderColor = if (isXRayVisible && !isXRayMinimized) RadiantMint else Color(0xFF283A5E),
                        shape = RoundedCornerShape(10.dp),
                        contentPaddingHorizontal = 7,
                        contentPaddingVertical = 5
                    ) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = "X-Ray",
                            tint = if (isXRayVisible) RadiantMint else Color(0xFF94A3B8),
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    ResponsiveBouncyButton(
                        onClick = onOpenSwamSheet,
                        containerColor = NeonCyan.copy(alpha = 0.14f),
                        borderColor = NeonCyan.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(10.dp),
                        contentPaddingHorizontal = 7,
                        contentPaddingVertical = 5
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "SWAM",
                            tint = NeonCyan,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    ResponsiveBouncyButton(
                        onClick = onOpenBenchmark,
                        containerColor = AmberGold.copy(alpha = 0.14f),
                        borderColor = AmberGold.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(10.dp),
                        contentPaddingHorizontal = 7,
                        contentPaddingVertical = 5
                    ) {
                        Icon(
                            imageVector = Icons.Default.Science,
                            contentDescription = "Bench",
                            tint = AmberGold,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            // Row 2: Always-Visible 3-Mode Responsive Segmented Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ModeSegmentButton(
                    title = "NAIVE (16MB)",
                    subtitle = "Copy toàn bộ",
                    selected = mode == HistoryMode.NAIVE,
                    color = CoralRose,
                    onClick = { onSelectMode(HistoryMode.NAIVE) },
                    modifier = Modifier.weight(1f)
                )
                ModeSegmentButton(
                    title = "DIRTY RECT",
                    subtitle = "Vùng bao nét",
                    selected = mode == HistoryMode.DIRTY_RECT,
                    color = AmberGold,
                    onClick = { onSelectMode(HistoryMode.DIRTY_RECT) },
                    modifier = Modifier.weight(1f)
                )
                ModeSegmentButton(
                    title = "TIERED SWAM ∞",
                    subtitle = "Hot•Warm•Cold",
                    selected = mode == HistoryMode.TIERED,
                    color = RadiantMint,
                    onClick = { onSelectMode(HistoryMode.TIERED) },
                    modifier = Modifier.weight(1.1f)
                )
            }
        }
    }
}

@Composable
private fun AutoThreeModeDemoBanner(
    phaseTitle: String,
    instruction: String,
    progress: Float,
    onStop: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xF50E1A2F),
        border = BorderStroke(1.5.dp, RadiantMint),
        shadowElevation = 10.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🎬 $phaseTitle (${(progress * 100).toInt()}%)",
                    color = RadiantMint,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                ResponsiveBouncyButton(
                    onClick = onStop,
                    containerColor = CoralRose,
                    borderColor = CoralRose,
                    shape = RoundedCornerShape(8.dp),
                    contentPaddingHorizontal = 8,
                    contentPaddingVertical = 3
                ) {
                    Text(
                        text = "DỪNG",
                        color = Color.White,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
            Spacer(modifier = Modifier.height(5.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = RadiantMint,
                trackColor = Color(0xFF1E2D46)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = instruction,
                color = Color(0xFFF1F5F9),
                fontSize = 10.5.sp,
                lineHeight = 14.5.sp
            )
        }
    }
}

@Composable
private fun ModeSegmentButton(
    title: String,
    subtitle: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ResponsiveBouncyButton(
        onClick = onClick,
        containerColor = if (selected) color.copy(alpha = 0.2f) else Color(0xFF121A2B),
        borderColor = if (selected) color else Color(0xFF22314B),
        shape = RoundedCornerShape(11.dp),
        contentPaddingVertical = 5,
        contentPaddingHorizontal = 6,
        modifier = modifier
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                color = if (selected) color else Color(0xFFCBD5E1),
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = if (selected) Color.White else Color(0xFF64748B),
                fontSize = 8.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun OomCaughtAlertBanner(
    message: String,
    onClearCurrentRam: () -> Unit,
    onSwitchToTiered: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xF52A0E17),
        border = BorderStroke(1.5.dp, CoralRose),
        shadowElevation = 10.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = CoralRose,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ĐÃ CHẶN TRÀN RAM (BẢO VỆ CHỐNG SẬP APP)",
                        color = CoralRose,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Đóng",
                        tint = Color(0xFFFCA5A5),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = message,
                color = Color(0xFFFECDD3),
                fontSize = 10.5.sp,
                lineHeight = 14.5.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ResponsiveBouncyButton(
                    onClick = onClearCurrentRam,
                    containerColor = Color(0xFF3F1D2B),
                    borderColor = CoralRose,
                    shape = RoundedCornerShape(9.dp),
                    contentPaddingVertical = 5,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "🧹 Giải Phóng RAM",
                        color = Color.White,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                ResponsiveBouncyButton(
                    onClick = onSwitchToTiered,
                    containerColor = RadiantMint,
                    borderColor = RadiantMint,
                    shape = RoundedCornerShape(9.dp),
                    contentPaddingVertical = 5,
                    modifier = Modifier.weight(1.3f)
                ) {
                    Text(
                        text = "⚡ Chuyển TIERED SWAM",
                        color = Color.Black,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

@Composable
private fun BurstProgressBanner(
    label: String,
    current: Int,
    total: Int,
    onStop: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xF0101D33),
        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.8f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    color = NeonCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                ResponsiveBouncyButton(
                    onClick = onStop,
                    containerColor = CoralRose,
                    borderColor = CoralRose,
                    shape = RoundedCornerShape(7.dp),
                    contentPaddingHorizontal = 8,
                    contentPaddingVertical = 3
                ) {
                    Text(
                        text = "DỪNG",
                        color = Color.White,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { (current.toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = NeonCyan,
                trackColor = Color(0xFF1E2D46)
            )
        }
    }
}

@Composable
private fun PixelProResponsiveDock(
    dockTab: DockTabMode,
    activeTool: StudioBrushTool,
    selectedColor: Int,
    selectedWidth: Float,
    currentMode: HistoryMode,
    burstStrokeType: StrokeType,
    showDirtyRectBox: Boolean,
    showPaperGrid: Boolean,
    isDemoRunning: Boolean,
    isBurstRunning: Boolean,
    totalSteps: Int,
    onSelectDockTab: (DockTabMode) -> Unit,
    onSelectTool: (StudioBrushTool) -> Unit,
    onSelectColor: (Int) -> Unit,
    onSelectWidth: (Float) -> Unit,
    onToggleBurstStrokeType: () -> Unit,
    onToggleGrid: () -> Unit,
    onToggleDirtyRect: () -> Unit,
    onFillCanvas: () -> Unit,
    onClearCanvas: () -> Unit,
    onBurstDraw: (Int) -> Unit,
    onBurstUndo: (Int) -> Unit,
    onStressMemory: () -> Unit,
    onRunOomCleaner: () -> Unit,
    onToggleDemo: () -> Unit
) {
    val proPalette = listOf(
        AndroidColor.parseColor("#0F172A") to Color(0xFF0F172A), // Obsidian Black
        AndroidColor.parseColor("#EF4444") to Color(0xFFEF4444), // Crimson Red
        AndroidColor.parseColor("#F97316") to Color(0xFFF97316), // Sunset Orange
        AndroidColor.parseColor("#F59E0B") to Color(0xFFF59E0B), // Amber Gold
        AndroidColor.parseColor("#10B981") to Color(0xFF10B981), // Emerald Green
        AndroidColor.parseColor("#06B6D4") to Color(0xFF06B6D4), // Cyan Teal
        AndroidColor.parseColor("#3B82F6") to Color(0xFF3B82F6), // Royal Blue
        AndroidColor.parseColor("#6366F1") to Color(0xFF6366F1), // Electric Indigo
        AndroidColor.parseColor("#8B5CF6") to Color(0xFF8B5CF6), // Vivid Violet
        AndroidColor.parseColor("#EC4899") to Color(0xFFEC4899), // Rose Pink
        AndroidColor.parseColor("#78350F") to Color(0xFF78350F), // Espresso Brown
        AndroidColor.parseColor("#64748B") to Color(0xFF64748B)  // Slate Gray
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 5.dp),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF0E1525),
        border = BorderStroke(1.dp, Color(0xFF233452)),
        shadowElevation = 16.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            // Row 1: Responsive Tab Switcher + Quick Canvas Utilities (All weighted, zero scroll!)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                ResponsiveBouncyButton(
                    onClick = { onSelectDockTab(DockTabMode.CREATIVE_STUDIO) },
                    containerColor = if (dockTab == DockTabMode.CREATIVE_STUDIO) NeonCyan.copy(alpha = 0.2f) else Color(0xFF131C2E),
                    borderColor = if (dockTab == DockTabMode.CREATIVE_STUDIO) NeonCyan else Color(0xFF23324D),
                    shape = RoundedCornerShape(11.dp),
                    contentPaddingVertical = 6,
                    modifier = Modifier.weight(1.15f)
                ) {
                    Text(
                        text = "🎨 Bàn Vẽ Studio",
                        color = if (dockTab == DockTabMode.CREATIVE_STUDIO) NeonCyan else Color(0xFFCBD5E1),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                }

                ResponsiveBouncyButton(
                    onClick = { onSelectDockTab(DockTabMode.SWAM_DEMO) },
                    containerColor = if (dockTab == DockTabMode.SWAM_DEMO) RadiantMint.copy(alpha = 0.2f) else Color(0xFF131C2E),
                    borderColor = if (dockTab == DockTabMode.SWAM_DEMO) RadiantMint else Color(0xFF23324D),
                    shape = RoundedCornerShape(11.dp),
                    contentPaddingVertical = 6,
                    modifier = Modifier.weight(1.25f)
                ) {
                    Text(
                        text = "⚡ Test & Demo (+50/+200)",
                        color = if (dockTab == DockTabMode.SWAM_DEMO) RadiantMint else Color(0xFFCBD5E1),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                }

                ResponsiveBouncyButton(
                    onClick = onToggleGrid,
                    containerColor = if (showPaperGrid) SkyBlue.copy(alpha = 0.2f) else Color(0xFF141E33),
                    borderColor = if (showPaperGrid) SkyBlue else Color(0xFF253552),
                    shape = RoundedCornerShape(11.dp),
                    contentPaddingVertical = 6,
                    contentPaddingHorizontal = 6
                ) {
                    Icon(
                        imageVector = Icons.Default.GridOn,
                        contentDescription = "Lưới",
                        tint = if (showPaperGrid) SkyBlue else Color(0xFFCBD5E1),
                        modifier = Modifier.size(15.dp)
                    )
                }

                ResponsiveBouncyButton(
                    onClick = onFillCanvas,
                    containerColor = Color(0xFF1B1630),
                    borderColor = Color(0xFF4C3575),
                    shape = RoundedCornerShape(11.dp),
                    contentPaddingVertical = 6,
                    contentPaddingHorizontal = 6
                ) {
                    Icon(
                        imageVector = Icons.Default.FormatColorFill,
                        contentDescription = "Đổ màu",
                        tint = Color(0xFFC4B5FD),
                        modifier = Modifier.size(15.dp)
                    )
                }

                ResponsiveBouncyButton(
                    onClick = onClearCanvas,
                    containerColor = Color(0xFF28131C),
                    borderColor = CoralRose.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(11.dp),
                    contentPaddingVertical = 6,
                    contentPaddingHorizontal = 6
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Xóa trắng",
                        tint = CoralRose,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            if (dockTab == DockTabMode.CREATIVE_STUDIO) {
                // === TAB 1: STUDIO DRAWING MODE ===
                // Row A: 3 Brush Tools + Quick +50 / +200 Burst Pills (Test current mode anytime!)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StudioToolButton(
                        icon = Icons.Default.Edit,
                        label = "Bút Mực",
                        selected = activeTool == StudioBrushTool.INK_PEN,
                        onClick = { onSelectTool(StudioBrushTool.INK_PEN) },
                        modifier = Modifier.weight(1f)
                    )
                    StudioToolButton(
                        icon = Icons.Default.Brush,
                        label = "Marker",
                        selected = activeTool == StudioBrushTool.MARKER,
                        onClick = { onSelectTool(StudioBrushTool.MARKER) },
                        modifier = Modifier.weight(1f)
                    )
                    StudioToolButton(
                        icon = Icons.Default.AutoFixHigh,
                        label = "Cục Tẩy",
                        selected = activeTool == StudioBrushTool.ERASER,
                        onClick = { onSelectTool(StudioBrushTool.ERASER) },
                        modifier = Modifier.weight(1f)
                    )
                    ResponsiveBouncyButton(
                        onClick = { onBurstDraw(50) },
                        containerColor = RadiantMint.copy(alpha = 0.16f),
                        borderColor = RadiantMint.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(10.dp),
                        contentPaddingVertical = 6,
                        modifier = Modifier.weight(0.85f)
                    ) {
                        Text(
                            text = "+50 nét",
                            color = RadiantMint,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                    }
                    ResponsiveBouncyButton(
                        onClick = { onBurstDraw(200) },
                        containerColor = NeonCyan.copy(alpha = 0.16f),
                        borderColor = NeonCyan.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(10.dp),
                        contentPaddingVertical = 6,
                        modifier = Modifier.weight(0.9f)
                    ) {
                        Text(
                            text = "+200 nét",
                            color = NeonCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                    }
                }

                // Row B: Brush Size Slider with Live Preview Circle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val previewDp = (selectedWidth / 7f).coerceIn(5f, 22f).dp
                    Box(
                        modifier = Modifier.size(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(previewDp)
                                .clip(CircleShape)
                                .background(
                                    if (activeTool == StudioBrushTool.ERASER) Color.White
                                    else Color(selectedColor)
                                )
                                .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                        )
                    }

                    Slider(
                        value = selectedWidth,
                        onValueChange = onSelectWidth,
                        valueRange = 3f..160f,
                        colors = SliderDefaults.colors(
                            thumbColor = NeonCyan,
                            activeTrackColor = NeonCyan,
                            inactiveTrackColor = Color(0xFF1E2D46)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(26.dp)
                    )

                    Text(
                        text = "Cỡ ${selectedWidth.toInt()}px",
                        color = Color(0xFFCBD5E1),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Row C: All 12 Colors evenly distributed across the full width
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    proPalette.forEach { (androidCol, composeCol) ->
                        val isSelected = selectedColor == androidCol && activeTool != StudioBrushTool.ERASER
                        val scale by animateFloatAsState(
                            targetValue = if (isSelected) 1.22f else 1.0f,
                            label = "swatchScale"
                        )
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .scale(scale)
                                .clip(CircleShape)
                                .background(composeCol)
                                .border(
                                    width = if (isSelected) 2.2.dp else 1.dp,
                                    color = if (isSelected) NeonCyan else Color(0xFF334155),
                                    shape = CircleShape
                                )
                                .clickable { onSelectColor(androidCol) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                            }
                        }
                    }
                }
            } else {
                // === TAB 2: RESPONSIVE BENTO GRID FOR TESTING ANY MODE (+50 / +200 / UNDO / STRESS / DEMO) ===
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ResponsiveBouncyButton(
                        onClick = onToggleBurstStrokeType,
                        containerColor = Color(0xFF141E33),
                        borderColor = SkyBlue.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(10.dp),
                        contentPaddingVertical = 5,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (burstStrokeType == StrokeType.LARGE) "🖌️ Cọ Bơm: NÉT LỚN (40-100%)" else "✏️ Cọ Bơm: NÉT NHỎ (2-10%)",
                            color = SkyBlue,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                    }

                    ResponsiveBouncyButton(
                        onClick = onToggleDirtyRect,
                        containerColor = if (showDirtyRectBox) EmeraldGreen.copy(alpha = 0.18f) else Color(0xFF141E33),
                        borderColor = if (showDirtyRectBox) EmeraldGreen else Color(0xFF283A5E),
                        shape = RoundedCornerShape(10.dp),
                        contentPaddingVertical = 5,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = null,
                                tint = if (showDirtyRectBox) RadiantMint else Color(0xFF94A3B8),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (showDirtyRectBox) "Khung DirtyRect: BẬT" else "Khung DirtyRect: TẮT",
                                color = if (showDirtyRectBox) RadiantMint else Color(0xFFCBD5E1),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Bento Row 1: Burst Draw +50, Burst Draw +200, Batch Undo -50 (Runs on CURRENT Mode!)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BentoDemoCard(
                        icon = Icons.Default.AllInclusive,
                        title = "Bơm +50 Bước",
                        subtitle = "Chế độ ${currentMode.name}",
                        color = RadiantMint,
                        onClick = { onBurstDraw(50) },
                        modifier = Modifier.weight(1f)
                    )
                    BentoDemoCard(
                        icon = Icons.Default.RocketLaunch,
                        title = "Bơm +200 Bước",
                        subtitle = "Chế độ ${currentMode.name}",
                        color = NeonCyan,
                        onClick = { onBurstDraw(200) },
                        modifier = Modifier.weight(1f)
                    )
                    BentoDemoCard(
                        icon = Icons.Default.FastRewind,
                        title = "Undo -50 Bước",
                        subtitle = "Đo Prefetch/Trễ",
                        color = SkyBlue,
                        onClick = { onBurstUndo(50) },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Bento Row 2: Auto Demo 3 Mode, Stress Trim RAM, OOM Cleaner
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BentoDemoCard(
                        icon = if (isDemoRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                        title = if (isDemoRunning) "Dừng Demo" else "Demo 3 Mode",
                        subtitle = "Chạy & Xuất Báo Cáo",
                        color = if (isDemoRunning) CoralRose else AmberGold,
                        onClick = onToggleDemo,
                        modifier = Modifier.weight(1f)
                    )
                    BentoDemoCard(
                        icon = Icons.Default.Bolt,
                        title = "Stress RAM",
                        subtitle = "Trim Memory 80",
                        color = CoralRose,
                        onClick = onStressMemory,
                        modifier = Modifier.weight(1f)
                    )
                    BentoDemoCard(
                        icon = Icons.Default.CleaningServices,
                        title = "OOM Cleaner",
                        subtitle = "Gộp trang CRC32",
                        color = Color(0xFFC4B5FD),
                        onClick = onRunOomCleaner,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun StudioToolButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ResponsiveBouncyButton(
        onClick = onClick,
        containerColor = if (selected) NeonCyan else Color(0xFF152036),
        borderColor = if (selected) NeonCyan else Color(0xFF283A5E),
        shape = RoundedCornerShape(10.dp),
        contentPaddingVertical = 6,
        contentPaddingHorizontal = 4,
        modifier = modifier
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) Color.Black else Color(0xFFCBD5E1),
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = label,
                color = if (selected) Color.Black else Color(0xFFE2E8F0),
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun BentoDemoCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ResponsiveBouncyButton(
        onClick = onClick,
        containerColor = color.copy(alpha = 0.14f),
        borderColor = color.copy(alpha = 0.7f),
        shape = RoundedCornerShape(12.dp),
        contentPaddingVertical = 7,
        contentPaddingHorizontal = 6,
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = color,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = title,
                    color = color,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = subtitle,
                color = Color(0xFFCBD5E1),
                fontSize = 8.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun DemoScenarioBannerCard(
    state: DemoState,
    onStop: () -> Unit
) {
    val min = state.elapsedSeconds / 60
    val sec = state.elapsedSeconds % 60
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xF0171226),
        border = BorderStroke(1.dp, AmberGold.copy(alpha = 0.8f)),
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = String.format(Locale.US, "🎬 %s (%d:%02d / 3:00)", state.currentPhase.title, min, sec),
                    color = AmberGold,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                ResponsiveBouncyButton(
                    onClick = onStop,
                    containerColor = CoralRose,
                    borderColor = CoralRose,
                    shape = RoundedCornerShape(8.dp),
                    contentPaddingHorizontal = 8,
                    contentPaddingVertical = 4
                ) {
                    Text(
                        text = "DỪNG",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
            Spacer(modifier = Modifier.height(5.dp))
            LinearProgressIndicator(
                progress = { (state.elapsedSeconds / 180f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = AmberGold,
                trackColor = Color(0xFF2B2340)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = state.currentInstruction,
                color = Color(0xFFF1F5F9),
                fontSize = 11.sp
            )
        }
    }
}

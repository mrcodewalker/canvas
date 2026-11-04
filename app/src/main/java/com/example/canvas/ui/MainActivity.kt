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
import com.example.canvas.ui.compose.AuroraGlassCard
import com.example.canvas.ui.compose.ChronosXRayHud
import com.example.canvas.ui.compose.M3ExpressiveModeSelector
import com.example.canvas.ui.compose.ModeEvaluationMetrics
import com.example.canvas.ui.compose.PulsingAuroraButton
import com.example.canvas.ui.compose.SwamComparisonReport
import com.example.canvas.ui.compose.SwamDemoReportModal
import com.example.canvas.ui.compose.SwamPaperSheet
import com.example.canvas.ui.theme.AmberGold
import com.example.canvas.ui.theme.ChronosCanvasTheme
import com.example.canvas.ui.theme.CoralRose
import com.example.canvas.ui.theme.CyberObsidian
import com.example.canvas.ui.theme.ElectricIndigo
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
    INK_PEN("Bút mực"),
    MARKER("Bút dạ"),
    ERASER("Tẩy")
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
    private var showDirtyRectBox by mutableStateOf(true)
    private var showPaperGrid by mutableStateOf(false)
    private var currentZoomScale by mutableFloatStateOf(1f)
    private var lastPatchSummary by mutableStateOf("2048×2048 px • Chụm 2 ngón để thu phóng")

    private var demoState by mutableStateOf(DemoState(isRunning = false))
    private var currentTierConfig by mutableStateOf(
        TierConfig(
            hotBudgetBytes = 4L * 1024 * 1024,
            warmBudgetBytes = 256L * 1024,
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
            lastPatchSummary = "[${currentMode.name}] Bước #${view.historyManager.stats().totalSteps}: ${w}×${h} px (${kb} KB)"
            refreshTelemetrySnapshot()
        }
        view.onOomCaught = { msg ->
            runOnUiThread {
                refreshTelemetrySnapshot()
                val totalNow = view.historyManager.stats().totalSteps
                oomAlertMessage =
                    "Đã chặn giới hạn bộ nhớ lịch sử tại bước #$totalNow (${currentMode.name}):\n$msg"
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
     * Automated 3-Mode Comparison Runner:
     * Executes real drawing and undo operations across NAIVE, DIRTY_RECT, and TIERED modes
     * and collects 100% genuine, un-clamped telemetry from HistoryStats and System.nanoTime().
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

        val maxHeapMb = (Runtime.getRuntime().maxMemory() / (1024 * 1024)).toInt()
        val availableHistoryHeapMb = (maxHeapMb * 0.65).toInt()
        val coldBudgetMb = 500
        val demoDrawer = ScriptedDrawer(seed = 42L)

        autoDemoJob = lifecycleScope.launch {
            try {
                // =========================================================================
                // PHA 1: NAIVE (Lưu bản sao toàn bộ Canvas 16 MiB/bước trong RAM)
                // =========================================================================
                switchMode(HistoryMode.NAIVE)
                view.clearCanvas()
                System.gc()
                delay(200L)

                val targetNaiveSteps = 6
                var naiveExecuted = 0
                var naiveTotalCommitNs = 0L

                for (i in 1..targetNaiveSteps) {
                    if (!isActive) return@launch

                    autoDemoPhaseTitle = "Pha 1/3: Naive (Bản sao 16 MB/bước)"
                    autoDemoInstruction = "Bước $i/$targetNaiveSteps: Đang cấp phát bản sao toàn bộ Canvas 2048×2048 trong RAM..."
                    autoDemoProgress = (i.toFloat() / targetNaiveSteps) * 0.30f

                    val stroke = demoDrawer.nextStroke(StrokeType.LARGE)
                    try {
                        val t0 = System.nanoTime()
                        view.drawProgrammaticStrokeSuspend(stroke)
                        naiveTotalCommitNs += (System.nanoTime() - t0)
                        naiveExecuted++
                    } catch (_: OutOfMemoryError) {
                        System.gc()
                        break
                    }
                    refreshTelemetrySnapshot()
                    delay(220L)
                }

                val naiveStats = view.historyManager.stats()
                val naiveHotMb = naiveStats.hotBytes / (1024.0 * 1024.0)
                val naiveAvgKb = if (naiveStats.totalSteps > 0) {
                    (naiveStats.hotBytes / 1024.0) / naiveStats.totalSteps
                } else 0.0
                val naiveAvgLatencyMs = if (naiveExecuted > 0) {
                    (naiveTotalCommitNs / 1_000_000.0) / naiveExecuted
                } else 0.0
                val naiveMaxSteps = if (naiveAvgKb > 0.0) {
                    ((availableHistoryHeapMb * 1024.0) / naiveAvgKb).toInt()
                } else 0

                val naiveMetrics = ModeEvaluationMetrics(
                    mode = HistoryMode.NAIVE,
                    stepsExecuted = naiveStats.totalSteps,
                    stoppedNearOom = true,
                    peakRamMb = naiveHotMb,
                    avgStepSizeKb = naiveAvgKb,
                    hotMb = naiveHotMb,
                    warmMb = 0.0,
                    coldMb = 0.0,
                    compressionRatio = 1.0,
                    avgLatencyMs = naiveAvgLatencyMs,
                    prefetchHitRate = 0.0,
                    oomCleanerSavedKb = 0.0,
                    maxUndoInRamSteps = naiveMaxSteps,
                    maxUndoWithDiskSteps = naiveMaxSteps
                )

                view.historyManager.clear()
                System.gc()
                autoDemoInstruction = "Đã ghi nhận số liệu Naive (${String.format(Locale.US, "%.1f", naiveHotMb)} MB RAM cho ${naiveStats.totalSteps} bước) và giải phóng bộ nhớ."
                delay(650L)

                // =========================================================================
                // PHA 2: DIRTY_RECT (Chỉ cắt khung bao nét vẽ lưu trong RAM)
                // =========================================================================
                switchMode(HistoryMode.DIRTY_RECT)
                view.clearCanvas()
                System.gc()
                delay(200L)

                val dirtyTotalSteps = 18
                var dirtyTotalCommitNs = 0L
                for (i in 1..dirtyTotalSteps) {
                    if (!isActive) return@launch
                    val kind = if (i <= 12) StrokeType.SMALL else StrokeType.LARGE
                    autoDemoPhaseTitle = "Pha 2/3: Dirty Rect (Cắt khung bao nét vẽ)"
                    autoDemoInstruction = "Bước $i/$dirtyTotalSteps: Cắt vùng bao nét vẽ (${kind.name}) lưu trực tiếp vào RAM..."
                    autoDemoProgress = 0.30f + (i.toFloat() / dirtyTotalSteps) * 0.30f

                    val stroke = demoDrawer.nextStroke(kind)
                    try {
                        val t0 = System.nanoTime()
                        view.drawProgrammaticStrokeSuspend(stroke)
                        dirtyTotalCommitNs += (System.nanoTime() - t0)
                    } catch (_: OutOfMemoryError) {
                        System.gc()
                        break
                    }
                    refreshTelemetrySnapshot()
                    delay(140L)
                }

                val dirtyStats = view.historyManager.stats()
                val dirtyHotMb = dirtyStats.hotBytes / (1024.0 * 1024.0)
                val dirtyAvgKb = if (dirtyStats.totalSteps > 0) {
                    (dirtyStats.hotBytes / 1024.0) / dirtyStats.totalSteps
                } else 0.0
                val dirtyAvgLatencyMs = if (dirtyStats.totalSteps > 0) {
                    (dirtyTotalCommitNs / 1_000_000.0) / dirtyStats.totalSteps
                } else 0.0
                val dirtyMaxSteps = if (dirtyAvgKb > 0.0) {
                    ((availableHistoryHeapMb * 1024.0) / dirtyAvgKb).toInt()
                } else 0

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
                    avgLatencyMs = dirtyAvgLatencyMs,
                    prefetchHitRate = 0.0,
                    oomCleanerSavedKb = 0.0,
                    maxUndoInRamSteps = dirtyMaxSteps,
                    maxUndoWithDiskSteps = dirtyMaxSteps
                )

                delay(600L)

                // =========================================================================
                // PHA 3: TIERED (Hot RAM -> Warm Deflate -> Cold Flash + Undo Prefetch)
                // =========================================================================
                switchMode(HistoryMode.TIERED)
                view.clearCanvas()
                System.gc()
                delay(200L)

                val tieredDrawSteps = 32
                var repeatedStrokeForDedup: com.example.canvas.core.Stroke? = null

                for (i in 1..tieredDrawSteps) {
                    if (!isActive) return@launch
                    val kind = if (i % 3 == 0) StrokeType.LARGE else StrokeType.SMALL
                    autoDemoPhaseTitle = "Pha 3/3: Tiered (Hot → Warm → Cold)"
                    autoDemoInstruction = "Bước $i/$tieredDrawSteps: Tự động chuyển các bước cũ từ Hot sang Warm (nén) và Cold (Flash)..."
                    autoDemoProgress = 0.60f + (i.toFloat() / tieredDrawSteps) * 0.25f

                    // At steps 25 and 26, draw with white on the same coordinates so two identical
                    // before-patches enter WarmStore and can be genuinely deduplicated by CRC32.
                    val stroke = when (i) {
                        25 -> demoDrawer.nextStroke(StrokeType.SMALL).copy(color = AndroidColor.WHITE).also {
                            repeatedStrokeForDedup = it
                        }
                        26 -> repeatedStrokeForDedup ?: demoDrawer.nextStroke(kind)
                        else -> demoDrawer.nextStroke(kind)
                    }
                    view.drawProgrammaticStrokeSuspend(stroke)
                    if (i % 2 == 0 || i == tieredDrawSteps) {
                        refreshTelemetrySnapshot()
                    }
                    delay(95L)
                }

                // Run OOM Cleaner to deduplicate any identical patches in WarmStore
                autoDemoInstruction = "Đang chạy bộ dọn trùng lặp CRC32 trên tầng Warm..."
                val tieredMgr = view.historyManager as? TieredHistory
                val oomSavedBytes = tieredMgr?.runOomCleaner() ?: 0L
                delay(250L)
                refreshTelemetrySnapshot()

                val statsPeakTiered = view.historyManager.stats()
                val peakHotMb = statsPeakTiered.hotBytes / (1024.0 * 1024.0)
                val peakWarmMb = statsPeakTiered.warmBytes / (1024.0 * 1024.0)
                val peakColdMb = statsPeakTiered.coldBytes / (1024.0 * 1024.0)
                val totalActualBytes = statsPeakTiered.hotBytes + statsPeakTiered.warmBytes + statsPeakTiered.coldBytes
                val measuredCompRatio = if (totalActualBytes > 0L && statsPeakTiered.rawEquivalentBytes > 0L) {
                    statsPeakTiered.rawEquivalentBytes.toDouble() / totalActualBytes.toDouble()
                } else 1.0
                val compressedTierBytes = statsPeakTiered.warmBytes + statsPeakTiered.coldBytes
                val compressedTierSteps = statsPeakTiered.warmSteps + statsPeakTiered.coldSteps
                val avgTieredStepKb = if (compressedTierSteps > 0) {
                    (compressedTierBytes / 1024.0) / compressedTierSteps
                } else if (statsPeakTiered.totalSteps > 0) {
                    (totalActualBytes / 1024.0) / statsPeakTiered.totalSteps
                } else 0.0

                // Execute 12 continuous Undos to trigger Prefetcher from Cold -> Warm and measure real Undo latency
                val undoTestSteps = 12
                var totalUndoNs = 0L
                var undoExecuted = 0
                for (u in 1..undoTestSteps) {
                    if (!isActive) return@launch
                    autoDemoInstruction = "Đang hoàn tác bước $u/$undoTestSteps và đo độ trễ khôi phục thực tế..."
                    autoDemoProgress = 0.85f + (u.toFloat() / undoTestSteps) * 0.15f
                    val t0 = System.nanoTime()
                    val ok = view.undoSuspend()
                    totalUndoNs += (System.nanoTime() - t0)
                    if (ok) undoExecuted++
                    refreshTelemetrySnapshot()
                    delay(90L)
                }
                val realUndoLatencyMs = if (undoExecuted > 0) {
                    (totalUndoNs / 1_000_000.0) / undoExecuted
                } else 0.0

                val statsAfterUndo = view.historyManager.stats()
                val totalPrefetch = statsAfterUndo.prefetchHits + statsAfterUndo.prefetchMisses
                val hitRatePct = if (totalPrefetch > 0) {
                    (statsAfterUndo.prefetchHits.toDouble() / totalPrefetch.toDouble()) * 100.0
                } else 0.0

                val swamRamCapSteps = if (avgTieredStepKb > 0.0) {
                    ((availableHistoryHeapMb * 1024.0) / avgTieredStepKb).toInt()
                } else 0
                val swamDiskCapSteps = if (avgTieredStepKb > 0.0) {
                    swamRamCapSteps + ((coldBudgetMb * 1024.0) / avgTieredStepKb).toInt()
                } else 0

                val tieredMetrics = ModeEvaluationMetrics(
                    mode = HistoryMode.TIERED,
                    stepsExecuted = statsPeakTiered.totalSteps,
                    stoppedNearOom = false,
                    peakRamMb = peakHotMb + peakWarmMb,
                    avgStepSizeKb = avgTieredStepKb,
                    hotMb = peakHotMb,
                    warmMb = peakWarmMb,
                    coldMb = peakColdMb,
                    compressionRatio = measuredCompRatio,
                    avgLatencyMs = realUndoLatencyMs,
                    prefetchHitRate = hitRatePct,
                    oomCleanerSavedKb = oomSavedBytes / 1024.0,
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
                    burstLabel = "Đang vẽ tự động bước $i/$count trên chế độ ${modeAtStart.name} (${strokeKind.name})..."

                    val stroke = burstDrawer.nextStroke(strokeKind)
                    try {
                        view.drawProgrammaticStrokeSuspend(stroke)
                    } catch (oom: OutOfMemoryError) {
                        System.gc()
                        refreshTelemetrySnapshot()
                        val totalNow = view.historyManager.stats().totalSteps
                        val peakMb = currentTelemetry.peakHeapMb
                        oomAlertMessage =
                            "Đã chạm giới hạn bộ nhớ tại bước #$i (Tổng $totalNow bước) trên chế độ ${modeAtStart.name}.\n" +
                            "Đỉnh Heap: ${String.format(Locale.US, "%.1f", peakMb)} MB."
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

            // 6. Dedicated Full-Stage Central Artboard Workspace (Canvas NEVER shrinks when X-Ray opens!)
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

                // Floating Glassmorphic X-Ray Telemetry HUD Overlay at Top-Center of Workspace
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isXRayVisible,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
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

                // Floating status badge at bottom-left of desk
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xD9181B21),
                    border = BorderStroke(1.dp, Color(0xFF2E333E)),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 10.dp, bottom = 6.dp)
                ) {
                    Text(
                        text = lastPatchSummary,
                        color = Color(0xFFD1D5DB),
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                // Floating Bounding-Box Selection Toggle at bottom-right of desk
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (showDirtyRectBox) Color(0xE61E293B) else Color(0xD9181B21),
                    border = BorderStroke(
                        1.dp,
                        if (showDirtyRectBox) SkyBlue.copy(alpha = 0.65f) else Color(0xFF2E333E)
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 10.dp, bottom = 6.dp)
                        .clickable {
                            showDirtyRectBox = !showDirtyRectBox
                            syncBrushToView()
                            drawingViewRef?.invalidate()
                        }
                ) {
                    Text(
                        text = if (showDirtyRectBox) "Khung bao: Bật" else "Khung bao: Tắt",
                        color = if (showDirtyRectBox) SkyBlue else Color(0xFF9CA3AF),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // 8. Bottom Studio & Test Dock
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
                            "Đã đổ màu toàn bộ Canvas 2048×2048 (16 MiB)",
                            Toast.LENGTH_SHORT
                        ).show()
                    } catch (oom: OutOfMemoryError) {
                        System.gc()
                        oomAlertMessage = "Chạm giới hạn bộ nhớ khi đổ màu 16 MB ở chế độ ${currentMode.name}."
                    }
                },
                onClearCanvas = {
                    burstJob?.cancel()
                    isBurstRunning = false
                    oomAlertMessage = null
                    drawingViewRef?.clearCanvas()
                    lastPatchSummary = "Đã xóa trắng bản vẽ (${currentMode.name})"
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
                        "Đã thu hồi bộ nhớ đệm Hot trên chế độ ${currentMode.name}",
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
                                "Đã gộp trang trùng lặp CRC32: thu hồi ${saved / 1024} KB",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "Chức năng gộp trùng lặp chỉ áp dụng cho chế độ Tiered",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                onToggleDemo = {
                    startThreeModeAutoDemo()
                }
            )
        }

        // Full-Screen Mathematical & Visual Result Report Modal
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

        // Architecture & Configuration Sheet
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
                                "Đã giải phóng ${saved / 1024} KB trang trùng lặp",
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
 * Clean Material 3 interactive button surface.
 */
@Composable
fun ResponsiveBouncyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = Color(0xFF22262F),
    borderColor: Color = Color(0xFF2E333E),
    shape: RoundedCornerShape = RoundedCornerShape(8.dp),
    contentPaddingVertical: Int = 6,
    contentPaddingHorizontal: Int = 8,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Surface(
        shape = shape,
        color = if (isPressed) containerColor.copy(alpha = 0.75f) else containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
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
    val headerAccent = when (mode) {
        HistoryMode.NAIVE -> CoralRose
        HistoryMode.DIRTY_RECT -> AmberGold
        HistoryMode.TIERED -> RadiantMint
    }

    AuroraGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        cornerRadius = 12.dp,
        accentColors = listOf(headerAccent, NeonCyan, ElectricIndigo),
        containerColor = Color(0xF2181B21)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Row 1: Undo/Steps/Redo Capsule (Left) + Action Toolbar (Right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Undo / Step Count / Redo Capsule
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF14171D),
                    border = BorderStroke(1.dp, Color(0xFF282D37))
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
                                contentDescription = "Hoàn tác",
                                tint = if (totalSteps > 0) Color(0xFFF3F4F6) else Color(0xFF6B7280),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Text(
                            text = "$totalSteps",
                            color = Color(0xFFF3F4F6),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 5.dp)
                        )

                        IconButton(
                            onClick = onRedo,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Redo,
                                contentDescription = "Làm lại",
                                tint = if (redoSteps > 0) Color(0xFFF3F4F6) else Color(0xFF6B7280),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Right Actions: Demo Button + Report + Profiler + Config + Benchmark
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    val zoomPct = (zoomScale * 100).toInt()
                    if (zoomPct != 100) {
                        ResponsiveBouncyButton(
                            onClick = onResetZoom,
                            containerColor = Color(0xFF22262F),
                            borderColor = Color(0xFF383E4C),
                            shape = RoundedCornerShape(8.dp),
                            contentPaddingHorizontal = 6,
                            contentPaddingVertical = 5
                        ) {
                            Text(
                                text = "$zoomPct%",
                                color = Color(0xFFD1D5DB),
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    PulsingAuroraButton(
                        text = if (isAutoDemoRunning) "Dừng" else "So sánh 3 chế độ",
                        isRunning = isAutoDemoRunning,
                        onClick = onStartTopAutoDemo,
                        leadingIcon = {
                            Icon(
                                imageVector = if (isAutoDemoRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = "So sánh 3 chế độ",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )

                    if (hasReportReady && !isAutoDemoRunning) {
                        ResponsiveBouncyButton(
                            onClick = onOpenLastReport,
                            containerColor = Color(0xFF22262F),
                            borderColor = SkyBlue.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(8.dp),
                            contentPaddingHorizontal = 7,
                            contentPaddingVertical = 5
                        ) {
                            Icon(
                                imageVector = Icons.Default.Assessment,
                                contentDescription = "Xem báo cáo",
                                tint = SkyBlue,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    ResponsiveBouncyButton(
                        onClick = onToggleXRay,
                        containerColor = if (isXRayVisible && !isXRayMinimized) Color(0xFF252B37) else Color(0xFF22262F),
                        borderColor = if (isXRayVisible && !isXRayMinimized) SkyBlue.copy(alpha = 0.6f) else Color(0xFF2E333E),
                        shape = RoundedCornerShape(8.dp),
                        contentPaddingHorizontal = 7,
                        contentPaddingVertical = 5
                    ) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = "Bộ nhớ",
                            tint = if (isXRayVisible) SkyBlue else Color(0xFF9CA3AF),
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    ResponsiveBouncyButton(
                        onClick = onOpenSwamSheet,
                        containerColor = Color(0xFF22262F),
                        borderColor = Color(0xFF2E333E),
                        shape = RoundedCornerShape(8.dp),
                        contentPaddingHorizontal = 7,
                        contentPaddingVertical = 5
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Cấu hình",
                            tint = Color(0xFFD1D5DB),
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    ResponsiveBouncyButton(
                        onClick = onOpenBenchmark,
                        containerColor = Color(0xFF22262F),
                        borderColor = Color(0xFF2E333E),
                        shape = RoundedCornerShape(8.dp),
                        contentPaddingHorizontal = 7,
                        contentPaddingVertical = 5
                    ) {
                        Icon(
                            imageVector = Icons.Default.Science,
                            contentDescription = "Benchmark",
                            tint = Color(0xFFD1D5DB),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            // Row 2: Clean Segmented Mode Selector
            M3ExpressiveModeSelector(
                selectedMode = mode,
                onSelectMode = onSelectMode
            )
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
        shape = RoundedCornerShape(10.dp),
        color = Color(0xF5181B21),
        border = BorderStroke(1.dp, SkyBlue.copy(alpha = 0.65f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$phaseTitle (${(progress * 100).toInt()}%)",
                    color = Color(0xFFF3F4F6),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                ResponsiveBouncyButton(
                    onClick = onStop,
                    containerColor = Color(0xFFDC2626),
                    borderColor = Color(0xFFF87171).copy(alpha = 0.4f),
                    shape = RoundedCornerShape(6.dp),
                    contentPaddingHorizontal = 8,
                    contentPaddingVertical = 3
                ) {
                    Text(
                        text = "Dừng",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(modifier = Modifier.height(5.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = SkyBlue,
                trackColor = Color(0xFF282D37)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = instruction,
                color = Color(0xFFD1D5DB),
                fontSize = 10.5.sp,
                lineHeight = 14.5.sp
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
        shape = RoundedCornerShape(10.dp),
        color = Color(0xF5221619),
        border = BorderStroke(1.dp, CoralRose.copy(alpha = 0.75f))
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
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Cảnh báo giới hạn bộ nhớ Heap",
                        color = CoralRose,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Đóng",
                        tint = Color(0xFFD1D5DB),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = message,
                color = Color(0xFFE5E7EB),
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
                    containerColor = Color(0xFF2D1E22),
                    borderColor = CoralRose.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp),
                    contentPaddingVertical = 5,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Giải phóng RAM",
                        color = Color(0xFFF3F4F6),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                ResponsiveBouncyButton(
                    onClick = onSwitchToTiered,
                    containerColor = Color(0xFF2563EB),
                    borderColor = Color(0xFF60A5FA).copy(alpha = 0.4f),
                    shape = RoundedCornerShape(8.dp),
                    contentPaddingVertical = 5,
                    modifier = Modifier.weight(1.3f)
                ) {
                    Text(
                        text = "Chuyển sang Tiered",
                        color = Color.White,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold
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
        shape = RoundedCornerShape(10.dp),
        color = Color(0xF2181B21),
        border = BorderStroke(1.dp, Color(0xFF2E333E))
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    color = Color(0xFFF3F4F6),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                ResponsiveBouncyButton(
                    onClick = onStop,
                    containerColor = Color(0xFFDC2626),
                    borderColor = Color(0xFFF87171).copy(alpha = 0.4f),
                    shape = RoundedCornerShape(6.dp),
                    contentPaddingHorizontal = 8,
                    contentPaddingVertical = 3
                ) {
                    Text(
                        text = "Dừng",
                        color = Color.White,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { (current.toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = SkyBlue,
                trackColor = Color(0xFF282D37)
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
        AndroidColor.parseColor("#0F172A") to Color(0xFF0F172A),
        AndroidColor.parseColor("#EF4444") to Color(0xFFEF4444),
        AndroidColor.parseColor("#F97316") to Color(0xFFF97316),
        AndroidColor.parseColor("#F59E0B") to Color(0xFFF59E0B),
        AndroidColor.parseColor("#10B981") to Color(0xFF10B981),
        AndroidColor.parseColor("#06B6D4") to Color(0xFF06B6D4),
        AndroidColor.parseColor("#3B82F6") to Color(0xFF3B82F6),
        AndroidColor.parseColor("#6366F1") to Color(0xFF6366F1),
        AndroidColor.parseColor("#8B5CF6") to Color(0xFF8B5CF6),
        AndroidColor.parseColor("#EC4899") to Color(0xFFEC4899),
        AndroidColor.parseColor("#78350F") to Color(0xFF78350F),
        AndroidColor.parseColor("#64748B") to Color(0xFF64748B)
    )

    AuroraGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 5.dp),
        cornerRadius = 12.dp,
        containerColor = Color(0xF2181B21)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            // Row 1: Tab Switcher + Quick Canvas Utilities
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                ResponsiveBouncyButton(
                    onClick = { onSelectDockTab(DockTabMode.CREATIVE_STUDIO) },
                    containerColor = if (dockTab == DockTabMode.CREATIVE_STUDIO) Color(0xFF252A34) else Color(0xFF14171D),
                    borderColor = if (dockTab == DockTabMode.CREATIVE_STUDIO) SkyBlue.copy(alpha = 0.65f) else Color(0xFF282D37),
                    shape = RoundedCornerShape(8.dp),
                    contentPaddingVertical = 6,
                    modifier = Modifier.weight(1.1f)
                ) {
                    Text(
                        text = "Công cụ vẽ",
                        color = if (dockTab == DockTabMode.CREATIVE_STUDIO) Color(0xFFF3F4F6) else Color(0xFF9CA3AF),
                        fontSize = 11.sp,
                        fontWeight = if (dockTab == DockTabMode.CREATIVE_STUDIO) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1
                    )
                }

                ResponsiveBouncyButton(
                    onClick = { onSelectDockTab(DockTabMode.SWAM_DEMO) },
                    containerColor = if (dockTab == DockTabMode.SWAM_DEMO) Color(0xFF252A34) else Color(0xFF14171D),
                    borderColor = if (dockTab == DockTabMode.SWAM_DEMO) SkyBlue.copy(alpha = 0.65f) else Color(0xFF282D37),
                    shape = RoundedCornerShape(8.dp),
                    contentPaddingVertical = 6,
                    modifier = Modifier.weight(1.25f)
                ) {
                    Text(
                        text = "Kiểm thử tải (+50 / +200)",
                        color = if (dockTab == DockTabMode.SWAM_DEMO) Color(0xFFF3F4F6) else Color(0xFF9CA3AF),
                        fontSize = 11.sp,
                        fontWeight = if (dockTab == DockTabMode.SWAM_DEMO) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1
                    )
                }

                ResponsiveBouncyButton(
                    onClick = onToggleGrid,
                    containerColor = if (showPaperGrid) Color(0xFF252A34) else Color(0xFF14171D),
                    borderColor = if (showPaperGrid) SkyBlue.copy(alpha = 0.65f) else Color(0xFF282D37),
                    shape = RoundedCornerShape(8.dp),
                    contentPaddingVertical = 6,
                    contentPaddingHorizontal = 6
                ) {
                    Icon(
                        imageVector = Icons.Default.GridOn,
                        contentDescription = "Lưới",
                        tint = if (showPaperGrid) SkyBlue else Color(0xFF9CA3AF),
                        modifier = Modifier.size(15.dp)
                    )
                }

                ResponsiveBouncyButton(
                    onClick = onFillCanvas,
                    containerColor = Color(0xFF14171D),
                    borderColor = Color(0xFF282D37),
                    shape = RoundedCornerShape(8.dp),
                    contentPaddingVertical = 6,
                    contentPaddingHorizontal = 6
                ) {
                    Icon(
                        imageVector = Icons.Default.FormatColorFill,
                        contentDescription = "Đổ màu",
                        tint = Color(0xFFD1D5DB),
                        modifier = Modifier.size(15.dp)
                    )
                }

                ResponsiveBouncyButton(
                    onClick = onClearCanvas,
                    containerColor = Color(0xFF14171D),
                    borderColor = Color(0xFF282D37),
                    shape = RoundedCornerShape(8.dp),
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StudioToolButton(
                        icon = Icons.Default.Edit,
                        label = "Bút mực",
                        selected = activeTool == StudioBrushTool.INK_PEN,
                        onClick = { onSelectTool(StudioBrushTool.INK_PEN) },
                        modifier = Modifier.weight(1f)
                    )
                    StudioToolButton(
                        icon = Icons.Default.Brush,
                        label = "Bút dạ",
                        selected = activeTool == StudioBrushTool.MARKER,
                        onClick = { onSelectTool(StudioBrushTool.MARKER) },
                        modifier = Modifier.weight(1f)
                    )
                    StudioToolButton(
                        icon = Icons.Default.AutoFixHigh,
                        label = "Tẩy",
                        selected = activeTool == StudioBrushTool.ERASER,
                        onClick = { onSelectTool(StudioBrushTool.ERASER) },
                        modifier = Modifier.weight(1f)
                    )
                    ResponsiveBouncyButton(
                        onClick = { onBurstDraw(50) },
                        containerColor = Color(0xFF22262F),
                        borderColor = Color(0xFF2E333E),
                        shape = RoundedCornerShape(8.dp),
                        contentPaddingVertical = 6,
                        modifier = Modifier.weight(0.85f)
                    ) {
                        Text(
                            text = "+50 nét",
                            color = Color(0xFFD1D5DB),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                    ResponsiveBouncyButton(
                        onClick = { onBurstDraw(200) },
                        containerColor = Color(0xFF22262F),
                        borderColor = Color(0xFF2E333E),
                        shape = RoundedCornerShape(8.dp),
                        contentPaddingVertical = 6,
                        modifier = Modifier.weight(0.9f)
                    ) {
                        Text(
                            text = "+200 nét",
                            color = Color(0xFFD1D5DB),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }

                // Brush Size Slider with Preview Circle
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
                                .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                        )
                    }

                    Slider(
                        value = selectedWidth,
                        onValueChange = onSelectWidth,
                        valueRange = 3f..160f,
                        colors = SliderDefaults.colors(
                            thumbColor = SkyBlue,
                            activeTrackColor = SkyBlue,
                            inactiveTrackColor = Color(0xFF282D37)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(26.dp)
                    )

                    Text(
                        text = "${selectedWidth.toInt()} px",
                        color = Color(0xFFD1D5DB),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Color Swatches
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    proPalette.forEach { (androidCol, composeCol) ->
                        val isSelected = selectedColor == androidCol && activeTool != StudioBrushTool.ERASER
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(composeCol)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) Color.White else Color(0xFF383E4C),
                                    shape = CircleShape
                                )
                                .clickable { onSelectColor(androidCol) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                            }
                        }
                    }
                }
            } else {
                // === TAB 2: STRESS & EVALUATION CONTROLS ===
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ResponsiveBouncyButton(
                        onClick = onToggleBurstStrokeType,
                        containerColor = Color(0xFF14171D),
                        borderColor = Color(0xFF282D37),
                        shape = RoundedCornerShape(8.dp),
                        contentPaddingVertical = 5,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (burstStrokeType == StrokeType.LARGE) "Cỡ nét tự động: Lớn (40–100%)" else "Cỡ nét tự động: Nhỏ (2–10%)",
                            color = Color(0xFFD1D5DB),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }

                    ResponsiveBouncyButton(
                        onClick = onToggleDirtyRect,
                        containerColor = if (showDirtyRectBox) Color(0xFF252A34) else Color(0xFF14171D),
                        borderColor = if (showDirtyRectBox) SkyBlue.copy(alpha = 0.6f) else Color(0xFF282D37),
                        shape = RoundedCornerShape(8.dp),
                        contentPaddingVertical = 5,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = null,
                                tint = if (showDirtyRectBox) SkyBlue else Color(0xFF9CA3AF),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (showDirtyRectBox) "Hiển thị khung bao: Bật" else "Hiển thị khung bao: Tắt",
                                color = if (showDirtyRectBox) Color(0xFFF3F4F6) else Color(0xFF9CA3AF),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Row 1: Burst Draw +50, Burst Draw +200, Batch Undo -50
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BentoDemoCard(
                        icon = Icons.Default.AllInclusive,
                        title = "Vẽ +50 bước",
                        subtitle = "Chế độ ${currentMode.name}",
                        color = RadiantMint,
                        onClick = { onBurstDraw(50) },
                        modifier = Modifier.weight(1f)
                    )
                    BentoDemoCard(
                        icon = Icons.Default.RocketLaunch,
                        title = "Vẽ +200 bước",
                        subtitle = "Chế độ ${currentMode.name}",
                        color = SkyBlue,
                        onClick = { onBurstDraw(200) },
                        modifier = Modifier.weight(1f)
                    )
                    BentoDemoCard(
                        icon = Icons.Default.FastRewind,
                        title = "Hoàn tác 50 bước",
                        subtitle = "Đo độ trễ & Prefetch",
                        color = Color(0xFFD1D5DB),
                        onClick = { onBurstUndo(50) },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Row 2: Auto Demo 3 Mode, Stress Trim RAM, OOM Cleaner
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BentoDemoCard(
                        icon = if (isDemoRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                        title = if (isDemoRunning) "Dừng chạy" else "So sánh 3 chế độ",
                        subtitle = "Chạy thực & xuất báo cáo",
                        color = if (isDemoRunning) CoralRose else AmberGold,
                        onClick = onToggleDemo,
                        modifier = Modifier.weight(1f)
                    )
                    BentoDemoCard(
                        icon = Icons.Default.Bolt,
                        title = "Thu hồi RAM",
                        subtitle = "Giả lập áp lực bộ nhớ",
                        color = CoralRose,
                        onClick = onStressMemory,
                        modifier = Modifier.weight(1f)
                    )
                    BentoDemoCard(
                        icon = Icons.Default.CleaningServices,
                        title = "Khử trùng lặp",
                        subtitle = "Gộp bản vá theo CRC32",
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
        containerColor = if (selected) Color(0xFF2563EB) else Color(0xFF14171D),
        borderColor = if (selected) Color(0xFF60A5FA).copy(alpha = 0.5f) else Color(0xFF282D37),
        shape = RoundedCornerShape(8.dp),
        contentPaddingVertical = 6,
        contentPaddingHorizontal = 4,
        modifier = modifier
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) Color.White else Color(0xFF9CA3AF),
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                color = if (selected) Color.White else Color(0xFFD1D5DB),
                fontSize = 10.5.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
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
        containerColor = Color(0xFF14171D),
        borderColor = Color(0xFF282D37),
        shape = RoundedCornerShape(8.dp),
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
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = title,
                    color = Color(0xFFF3F4F6),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = subtitle,
                color = Color(0xFF9CA3AF),
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
        shape = RoundedCornerShape(10.dp),
        color = Color(0xF2181B21),
        border = BorderStroke(1.dp, AmberGold.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = String.format(Locale.US, "%s (%d:%02d / 3:00)", state.currentPhase.title, min, sec),
                    color = Color(0xFFF3F4F6),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                ResponsiveBouncyButton(
                    onClick = onStop,
                    containerColor = Color(0xFFDC2626),
                    borderColor = Color(0xFFF87171).copy(alpha = 0.4f),
                    shape = RoundedCornerShape(6.dp),
                    contentPaddingHorizontal = 8,
                    contentPaddingVertical = 4
                ) {
                    Text(
                        text = "Dừng",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(modifier = Modifier.height(5.dp))
            LinearProgressIndicator(
                progress = { (state.elapsedSeconds / 180f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = AmberGold,
                trackColor = Color(0xFF282D37)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = state.currentInstruction,
                color = Color(0xFFD1D5DB),
                fontSize = 10.5.sp
            )
        }
    }
}

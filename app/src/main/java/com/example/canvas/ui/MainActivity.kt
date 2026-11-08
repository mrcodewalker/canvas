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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateListOf
import com.example.canvas.history.TierCell
import com.example.canvas.history.TierEvent
import com.example.canvas.ui.compose.TierTrackerPanel
import com.example.canvas.ui.theme.CyberSurface
import com.example.canvas.ui.theme.InkText
import com.example.canvas.ui.compose.LabSheet
import com.example.canvas.ui.compose.MemoryPill
import com.example.canvas.ui.compose.StatusStrip
import com.example.canvas.ui.compose.StudioTopBar
import com.example.canvas.ui.compose.ToolDock
import com.example.canvas.ui.theme.FaintText
import com.example.canvas.ui.theme.MutedText
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

    // Tier tracker: what happens to each stroke while drawing
    private var trackerVisible by mutableStateOf(false)
    private val tierEvents = mutableStateListOf<TierEvent>()   // newest first
    private var tierCells by mutableStateOf<List<TierCell>>(emptyList())
    private var trackerStartMs by mutableStateOf(0L)
    private var showClearConfirm by mutableStateOf(false)

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
    private var selectedBaseColor by mutableIntStateOf(AndroidColor.parseColor("#1C1B1A"))
    private var selectedStrokeWidth by mutableFloatStateOf(20f)
    private var showDirtyRectBox by mutableStateOf(true)
    private var showPaperGrid by mutableStateOf(false)
    private var currentZoomScale by mutableFloatStateOf(1f)
    private var lastPatchSummary by mutableStateOf("Chụm hai ngón để thu phóng")

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
        com.example.canvas.history.MemorySignal.install(this)
        enableEdgeToEdge()

        setupDemoRunner()

        setContent {
            ChronosCanvasTheme {
                PixelProStudioScreen()
            }
        }
    }

    /** Adds an event to the feed; the tiles of one stroke that move together become one line. */
    private fun pushTierEvent(e: TierEvent) {
        val last = tierEvents.firstOrNull()
        if (last != null && last.kind == e.kind && last.stroke == e.stroke && e.timeMs - last.timeMs < 600) {
            tierEvents[0] = last.copy(
                bytesBefore = last.bytesBefore + e.bytesBefore,
                bytesAfter = last.bytesAfter + e.bytesAfter,
                tiles = last.tiles + e.tiles
            )
        } else {
            tierEvents.add(0, e)
            while (tierEvents.size > 200) tierEvents.removeAt(tierEvents.size - 1)
        }
    }

    /** Starts or stops feeding the tracker from the current history (only Tiered has tiers to watch). */
    private fun bindTracker() {
        val mgr = drawingViewRef?.historyManager as? TieredHistory ?: return
        if (trackerVisible) {
            mgr.events.listener = { e -> runOnUiThread { pushTierEvent(e) } }
            mgr.tierMapListener = { cells -> runOnUiThread { tierCells = cells } }
            mgr.requestTierMap()
        } else {
            mgr.events.listener = null
            mgr.tierMapListener = null
        }
    }

    private fun toggleTracker() {
        trackerVisible = !trackerVisible
        if (trackerVisible) {
            isXRayMinimized = true          // one panel at a time: the tracker is the detailed view
            trackerStartMs = System.currentTimeMillis()
            tierEvents.clear()
            tierCells = emptyList()
        }
        bindTracker()
    }

    private fun performClear() {
        burstJob?.cancel()
        isBurstRunning = false
        oomAlertMessage = null
        drawingViewRef?.clearCanvas()
        lastPatchSummary = "Đã xóa bản vẽ"
        tierEvents.clear()
        tierCells = emptyList()
        refreshTelemetrySnapshot()
    }

    private fun modeLabel(mode: HistoryMode): String = when (mode) {
        HistoryMode.NAIVE -> "Naive"
        HistoryMode.DIRTY_RECT -> "Dirty rect"
        HistoryMode.TIERED -> "Tiered"
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
            // Real saved area (sum of dirty tiles, 4 bytes/px) versus the stroke's bounding box
            val coveredPx = patch.sizeBytes / 4
            val boxPx = (w.toLong() * h).coerceAtLeast(1L)
            val pct = if (currentMode == HistoryMode.NAIVE) 100L else (coveredPx * 100L / boxPx).coerceAtMost(100L)
            lastPatchSummary = "Bước ${view.historyManager.stats().totalSteps} · lưu ${coveredPx / 1000}k px, ${pct}% khung bao · ${kb} KB"
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
                it.onHistoryTrimmed = { dropped, left ->
                    runOnUiThread {
                        lastPatchSummary =
                            "Ổ đĩa gần đầy: đã bỏ $dropped nét undo cũ nhất, còn $left bước có thể hoàn tác"
                        refreshTelemetrySnapshot()
                    }
                }
            }
        }
        view.historyManager = newManager
        tierEvents.clear()
        tierCells = emptyList()
        bindTracker()
        memorySampler?.updateHistoryManager(newManager)
        lastPatchSummary = "${modeLabel(mode)} sẵn sàng"
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
        var showLab by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CyberObsidian)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                StudioTopBar(
                    mode = currentMode,
                    canUndo = currentTelemetry.totalSteps > 0,
                    canRedo = currentTelemetry.redoSteps > 0,
                    zoomScale = currentZoomScale,
                    trackerOpen = trackerVisible,
                    onUndo = {
                        drawingViewRef?.undo { undone ->
                            refreshTelemetrySnapshot()
                            if (!undone) {
                                Toast.makeText(this@MainActivity, "Đã hoàn tác về bước đầu", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onRedo = {
                        drawingViewRef?.redo { redone ->
                            refreshTelemetrySnapshot()
                            if (!redone) {
                                Toast.makeText(this@MainActivity, "Không còn bước để làm lại", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onSelectMode = { mode -> switchMode(mode) },
                    onResetZoom = { drawingViewRef?.resetViewport() },
                    onToggleTracker = { toggleTracker() },
                    onClearCanvas = { showClearConfirm = true },
                    onOpenLab = { showLab = true }
                )

                // The workspace: the canvas is full-bleed; everything else floats over the desk
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

                    // Top: things that are happening, then the memory line
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AnimatedVisibility(
                            visible = isAutoDemoRunning,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            StatusStrip(
                                title = autoDemoPhaseTitle,
                                detail = autoDemoInstruction,
                                accent = NeonCyan,
                                progress = autoDemoProgress,
                                primaryLabel = "Dừng",
                                onPrimary = {
                                    autoDemoJob?.cancel()
                                    isAutoDemoRunning = false
                                }
                            )
                        }

                        AnimatedVisibility(
                            visible = oomAlertMessage != null,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            StatusStrip(
                                title = "Hết bộ nhớ cho lịch sử",
                                detail = oomAlertMessage,
                                accent = CoralRose,
                                primaryLabel = if (currentMode != HistoryMode.TIERED) "Dùng Tiered" else null,
                                onPrimary = {
                                    oomAlertMessage = null
                                    switchMode(HistoryMode.TIERED)
                                },
                                secondaryLabel = "Giải phóng RAM",
                                onSecondary = {
                                    oomAlertMessage = null
                                    drawingViewRef?.historyManager?.clear()
                                    System.gc()
                                    refreshTelemetrySnapshot()
                                },
                                onClose = { oomAlertMessage = null }
                            )
                        }

                        AnimatedVisibility(
                            visible = isBurstRunning,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            StatusStrip(
                                title = burstLabel,
                                detail = "$burstProgressCurrent / $burstProgressTotal",
                                accent = AmberGold,
                                progress = if (burstProgressTotal > 0) burstProgressCurrent / burstProgressTotal.toFloat() else 0f,
                                primaryLabel = "Dừng",
                                onPrimary = {
                                    burstJob?.cancel()
                                    isBurstRunning = false
                                }
                            )
                        }

                        AnimatedVisibility(
                            visible = demoState.isRunning,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            StatusStrip(
                                title = String.format(
                                    Locale.US, "%s  %d:%02d / 3:00",
                                    demoState.currentPhase.title, demoState.elapsedSeconds / 60, demoState.elapsedSeconds % 60
                                ),
                                detail = demoState.currentInstruction,
                                accent = AmberGold,
                                progress = (demoState.elapsedSeconds / 180f).coerceIn(0f, 1f),
                                primaryLabel = "Dừng",
                                onPrimary = {
                                    demoRunner?.stop()
                                    Toast.makeText(this@MainActivity, "Đã dừng demo", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }

                        if (isXRayMinimized && !trackerVisible) {
                            MemoryPill(
                                telemetry = currentTelemetry,
                                expanded = false,
                                onClick = { isXRayMinimized = false }
                            )
                        } else if (!trackerVisible) {
                            ChronosXRayHud(
                                telemetry = currentTelemetry,
                                points = telemetryPoints,
                                mode = currentMode,
                                isMinimized = false,
                                onToggleMinimize = { isXRayMinimized = true },
                                onClose = { isXRayMinimized = true },
                                onOpenSwamSheet = { showSwamSheet = true }
                            )
                        }

                        AnimatedVisibility(
                            visible = trackerVisible,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            TierTrackerPanel(
                                mode = currentMode,
                                telemetry = currentTelemetry,
                                cells = tierCells,
                                events = tierEvents,
                                startMs = trackerStartMs,
                                onClear = { tierEvents.clear() },
                                onClose = { toggleTracker() }
                            )
                        }
                    }

                    // Bottom: a one-line caption and the tool dock
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // a quiet note after each stroke that fades away; the dock never moves
                        var noteVisible by remember { mutableStateOf(false) }
                        LaunchedEffect(lastPatchSummary) {
                            noteVisible = true
                            delay(2600L)
                            noteVisible = false
                        }
                        Box(modifier = Modifier.height(20.dp), contentAlignment = Alignment.Center) {
                            androidx.compose.animation.AnimatedVisibility(visible = noteVisible, enter = fadeIn(), exit = fadeOut()) {
                                Text(
                                    text = lastPatchSummary,
                                    color = MutedText,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        ToolDock(
                            activeTool = activeTool,
                            color = selectedBaseColor,
                            width = selectedStrokeWidth,
                            onSelectTool = { tool ->
                                activeTool = tool
                                syncBrushToView()
                            },
                            onSelectColor = { color ->
                                selectedBaseColor = color
                                if (activeTool == StudioBrushTool.ERASER) activeTool = StudioBrushTool.INK_PEN
                                syncBrushToView()
                            },
                            onSelectWidth = { width ->
                                selectedStrokeWidth = width
                                syncBrushToView()
                            }
                        )
                    }
                }
            }

            if (showClearConfirm) {
                AlertDialog(
                    onDismissRequest = { showClearConfirm = false },
                    containerColor = CyberSurface,
                    titleContentColor = InkText,
                    textContentColor = MutedText,
                    title = { Text("Xóa bản vẽ?") },
                    text = { Text("Bản vẽ và toàn bộ lịch sử hoàn tác sẽ bị xóa, không thể khôi phục.") },
                    confirmButton = {
                        TextButton(onClick = {
                            showClearConfirm = false
                            performClear()
                        }) { Text("Xóa", color = CoralRose, fontWeight = FontWeight.SemiBold) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearConfirm = false }) { Text("Hủy", color = MutedText) }
                    }
                )
            }

            if (showLab) {
                LabSheet(
                    isDemoRunning = isAutoDemoRunning || demoState.isRunning,
                    hasReport = latestDemoReport != null,
                    burstLarge = burstStrokeType == StrokeType.LARGE,
                    showSavedRegions = showDirtyRectBox,
                    showGrid = showPaperGrid,
                    showMemoryDetail = !isXRayMinimized,
                    onStartComparison = { startThreeModeAutoDemo() },
                    onOpenReport = { showDemoReportModal = true },
                    onOpenBenchmark = { startActivity(Intent(this@MainActivity, BenchmarkActivity::class.java)) },
                    onOpenConfig = { showSwamSheet = true },
                    onBurstDraw = { count -> burstDrawSteps(count) },
                    onBurstUndo = { count -> burstUndoSteps(count) },
                    onToggleBurstSize = {
                        burstStrokeType = if (burstStrokeType == StrokeType.LARGE) StrokeType.SMALL else StrokeType.LARGE
                    },
                    onReleaseHot = {
                        @Suppress("DEPRECATION")
                        drawingViewRef?.historyManager?.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
                        lifecycleScope.launch {
                            delay(80L)
                            refreshTelemetrySnapshot()
                        }
                    },
                    onMergeDuplicates = {
                        val mgr = drawingViewRef?.historyManager
                        if (mgr is TieredHistory) {
                            lifecycleScope.launch {
                                val saved = mgr.runOomCleaner()
                                refreshTelemetrySnapshot()
                                Toast.makeText(this@MainActivity, "Gộp trang trùng: thu hồi ${saved / 1024} KB", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(this@MainActivity, "Chỉ áp dụng cho chế độ Tiered", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onToggleSavedRegions = {
                        showDirtyRectBox = !showDirtyRectBox
                        syncBrushToView()
                        drawingViewRef?.invalidate()
                    },
                    onToggleGrid = {
                        showPaperGrid = !showPaperGrid
                        syncBrushToView()
                        drawingViewRef?.invalidate()
                    },
                    onToggleMemoryDetail = { isXRayMinimized = !isXRayMinimized },
                    onFill = {
                        try {
                            drawingViewRef?.fillCanvas(selectedBaseColor)
                            refreshTelemetrySnapshot()
                        } catch (oom: OutOfMemoryError) {
                            System.gc()
                            oomAlertMessage = "Không đủ bộ nhớ để đổ màu cả canvas (16 MB) ở chế độ ${currentMode.name}."
                        }
                    },
                    onDismiss = { showLab = false }
                )
            }

            // Comparison report
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

            // Tier configuration
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
                                Toast.makeText(this@MainActivity, "Đã giải phóng ${saved / 1024} KB trang trùng lặp", Toast.LENGTH_SHORT).show()
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
}

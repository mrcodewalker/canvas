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
import com.example.canvas.ui.compose.DemoCharts
import com.example.canvas.ui.compose.DemoStageHeader
import com.example.canvas.ui.compose.DemoStageState
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
    private var demoStage by mutableStateOf(DemoStageState())
    private var demoRestoreConfig: com.example.canvas.history.TierConfig? = null
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
     * Automated 3-mode comparison made to be watched: the canvas stays visible, two charts grow stroke by stroke
     * (RAM for the history, and time per stroke) and keep every finished mode on screen for comparison. The Tiered
     * part draws many more strokes, faster, so the flat line against the two climbing ones is easy to see.
     * All numbers are measured from the real history managers.
     */
    private fun startThreeModeAutoDemo() {
        val view = drawingViewRef ?: return
        if (isAutoDemoRunning) {
            autoDemoJob?.cancel()
            isAutoDemoRunning = false
            demoStage = DemoStageState()
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
        val mb = 1024.0 * 1024.0

        demoStage = DemoStageState(
            visible = true, phase = 0,
            title = "So sánh 3 cách lưu lịch sử hoàn tác",
            explain = "Cùng một bộ nét vẽ, ba cách lưu. Hai biểu đồ bên dưới cho thấy RAM và thời gian của từng cách khi số nét tăng dần."
        )

        autoDemoJob = lifecycleScope.launch {
            var completed = false
            try {
                // One step of one mode: put its RAM and time on the charts and refresh the tier meter
                fun record(index: Int, ramMb: Double, stepMs: Double, action: String? = null, caption: String = "") {
                    val s = view.historyManager.stats()
                    demoStage = demoStage.copy(
                        ram = demoStage.ram.toMutableList().also { it[index] = it[index] + ramMb.toFloat() },
                        ms = demoStage.ms.toMutableList().also { it[index] = it[index] + stepMs.toFloat() },
                        hotMb = s.hotBytes / mb, readyMb = s.predecodedBytes / mb, warmMb = s.warmBytes / mb, coldMb = s.coldBytes / mb,
                        hotBudgetMb = s.hotBudgetBytes / mb, warmBudgetMb = s.warmBudgetBytes / mb,
                        steps = s.totalSteps, action = action ?: demoStage.action, meterCaption = caption
                    )
                }
                fun refreshMeter(action: String? = null, caption: String = "") {
                    val s = view.historyManager.stats()
                    demoStage = demoStage.copy(
                        hotMb = s.hotBytes / mb, readyMb = s.predecodedBytes / mb, warmMb = s.warmBytes / mb, coldMb = s.coldBytes / mb,
                        hotBudgetMb = s.hotBudgetBytes / mb, warmBudgetMb = s.warmBudgetBytes / mb,
                        steps = s.totalSteps, action = action ?: demoStage.action, meterCaption = caption
                    )
                    refreshTelemetrySnapshot()
                }
                suspend fun cleanUp() {
                    view.historyManager.clear()
                    System.gc()
                    demoStage = demoStage.copy(
                        hotMb = 0.0, readyMb = 0.0, warmMb = 0.0, coldMb = 0.0, steps = 0, action = "",
                        meterCaption = "Đã dọn bộ nhớ để đo cách tiếp theo (đường trên biểu đồ vẫn giữ nguyên)."
                    )
                    refreshTelemetrySnapshot()
                    delay(1200L)
                }

                delay(3000L)

                // =========================================================================
                // PHA 1: NAIVE
                // =========================================================================
                switchMode(HistoryMode.NAIVE)
                view.clearCanvas()
                System.gc()
                demoStage = demoStage.copy(
                    phase = 1, progress = 0.02f,
                    title = "1 · Naive: chép cả canvas mỗi nét",
                    explain = "Mỗi nét sao chép toàn bộ canvas 2048×2048 (16 MB) để hoàn tác. RAM tăng thẳng đứng cho tới khi chạm ngưỡng an toàn.",
                    action = "", meterCaption = ""
                )
                delay(3000L)

                var naiveExecuted = 0
                var naiveTotalCommitNs = 0L
                var naiveStopped = false
                for (i in 1..12) {
                    if (!isActive) return@launch
                    autoDemoProgress = (i / 12f) * 0.15f
                    val stroke = demoDrawer.nextStroke(StrokeType.LARGE)
                    var ms = 0.0
                    try {
                        val t0 = System.nanoTime()
                        view.drawProgrammaticStrokeSuspend(stroke)
                        val dt = System.nanoTime() - t0
                        naiveTotalCommitNs += dt
                        ms = dt / 1_000_000.0
                        naiveExecuted++
                    } catch (_: OutOfMemoryError) {
                        System.gc()
                        naiveStopped = true
                        break
                    }
                    record(0, view.historyManager.stats().hotBytes / mb, ms, "Nét $i · lưu thêm 16 MB")
                    delay(600L)
                }
                if (naiveStopped) demoStage = demoStage.copy(action = "Naive dừng: chạm ngưỡng RAM an toàn, không vẽ thêm được.")

                val naiveStats = view.historyManager.stats()
                val naiveHotMb = naiveStats.hotBytes / mb
                val naiveAvgKb = if (naiveStats.totalSteps > 0) (naiveStats.hotBytes / 1024.0) / naiveStats.totalSteps else 0.0
                val naiveAvgLatencyMs = if (naiveExecuted > 0) (naiveTotalCommitNs / 1_000_000.0) / naiveExecuted else 0.0
                val naiveMaxSteps = if (naiveAvgKb > 0.0) ((availableHistoryHeapMb * 1024.0) / naiveAvgKb).toInt() else 0
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
                delay(2600L)
                cleanUp()

                // =========================================================================
                // PHA 2: DIRTY RECT
                // =========================================================================
                switchMode(HistoryMode.DIRTY_RECT)
                view.clearCanvas()
                System.gc()
                demoStage = demoStage.copy(
                    phase = 2, progress = 0.17f,
                    title = "2 · Dirty rect: chỉ lưu vùng thay đổi",
                    explain = "Chỉ lưu vùng nét đi qua, không lưu cả canvas. Nét nhỏ rất nhẹ; nét to phủ rộng thì RAM vẫn leo lên và không có nơi nào để đẩy bớt.",
                    action = "", meterCaption = ""
                )
                delay(3000L)

                val dirtyTotalSteps = 60
                var dirtyTotalCommitNs = 0L
                for (i in 1..dirtyTotalSteps) {
                    if (!isActive) return@launch
                    val kind = if (i <= 36) StrokeType.SMALL else StrokeType.LARGE
                    autoDemoProgress = 0.17f + (i.toFloat() / dirtyTotalSteps) * 0.25f
                    val stroke = demoDrawer.nextStroke(kind)
                    var ms = 0.0
                    try {
                        val t0 = System.nanoTime()
                        view.drawProgrammaticStrokeSuspend(stroke)
                        val dt = System.nanoTime() - t0
                        dirtyTotalCommitNs += dt
                        ms = dt / 1_000_000.0
                    } catch (_: OutOfMemoryError) {
                        System.gc()
                        demoStage = demoStage.copy(action = "Dirty rect dừng ở nét $i: chạm ngưỡng RAM an toàn.")
                        break
                    }
                    record(
                        1, view.historyManager.stats().hotBytes / mb, ms,
                        "Nét $i/$dirtyTotalSteps · ${if (kind == StrokeType.SMALL) "nét nhỏ" else "nét to"}"
                    )
                    delay(if (kind == StrokeType.SMALL) 170L else 260L)
                }

                val dirtyStats = view.historyManager.stats()
                val dirtyHotMb = dirtyStats.hotBytes / mb
                val dirtyAvgKb = if (dirtyStats.totalSteps > 0) (dirtyStats.hotBytes / 1024.0) / dirtyStats.totalSteps else 0.0
                val dirtyAvgLatencyMs = if (dirtyStats.totalSteps > 0) (dirtyTotalCommitNs / 1_000_000.0) / dirtyStats.totalSteps else 0.0
                val dirtyMaxSteps = if (dirtyAvgKb > 0.0) ((availableHistoryHeapMb * 1024.0) / dirtyAvgKb).toInt() else 0
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
                delay(2600L)
                cleanUp()

                // =========================================================================
                // PHA 3: TIERED, many more strokes, faster
                // =========================================================================
                switchMode(HistoryMode.TIERED)
                view.clearCanvas()
                System.gc()
                // A small budget, said out loud on screen: with the full one even 150 strokes barely leave Warm and
                // the audience would not see the Cold tier at all
                (view.historyManager as? TieredHistory)?.let { t ->
                    demoRestoreConfig = t.tierConfig
                    t.updateTierConfig(t.tierConfig.copy(autoBudgets = false, hotBudgetBytes = 1L * 1024 * 1024, warmBudgetBytes = 512L * 1024))
                }
                demoStage = demoStage.copy(
                    phase = 3, progress = 0.45f,
                    title = "3 · Tiered: Hot → Warm → Cold",
                    explain = "Nét mới ở Hot, nét cũ nén sang Warm, cũ hơn nữa ghi xuống đĩa Cold. Demo thu nhỏ Hot (1 MB) và Warm (0,5 MB) để thấy đủ ba tầng. RAM gần như đi ngang dù vẽ rất nhiều nét.",
                    action = "", meterCaption = ""
                )
                delay(3200L)

                val tieredDrawSteps = 160
                var repeatedStrokeForDedup: com.example.canvas.core.Stroke? = null
                var announcedWarm = false
                var announcedCold = false
                for (i in 1..tieredDrawSteps) {
                    if (!isActive) return@launch
                    val kind = if (i % 2 == 0) StrokeType.LARGE else StrokeType.SMALL
                    autoDemoProgress = 0.45f + (i.toFloat() / tieredDrawSteps) * 0.40f

                    // At steps 25 and 26, draw with white on the same coordinates so two identical
                    // before-patches enter WarmStore and can be genuinely deduplicated by CRC32.
                    val stroke = when (i) {
                        25 -> demoDrawer.nextStroke(StrokeType.SMALL).copy(color = AndroidColor.WHITE).also {
                            repeatedStrokeForDedup = it
                        }
                        26 -> repeatedStrokeForDedup ?: demoDrawer.nextStroke(kind)
                        else -> demoDrawer.nextStroke(kind)
                    }
                    val t0 = System.nanoTime()
                    view.drawProgrammaticStrokeSuspend(stroke)
                    val ms = (System.nanoTime() - t0) / 1_000_000.0

                    val s = view.historyManager.stats()
                    val action = when {
                        s.coldSteps > 0 && !announcedCold -> { announcedCold = true; "Warm đầy: nét cũ nhất được GHI XUỐNG ĐĨA (Cold)" }
                        s.warmSteps > 0 && !announcedWarm -> { announcedWarm = true; "Nét cũ bắt đầu được NÉN sang Warm" }
                        else -> "Nét $i/$tieredDrawSteps"
                    }
                    record(2, (s.hotBytes + s.warmBytes) / mb, ms, action)
                    // quick, but the three "first time" moments are held long enough to read
                    delay(if (action.startsWith("Nét ")) 45L else 1400L)
                }

                // Run OOM Cleaner to deduplicate any identical patches in WarmStore
                demoStage = demoStage.copy(action = "Dọn trùng lặp trên Warm: hai bước giống hệt chỉ giữ một bản (CRC32)")
                val tieredMgr = view.historyManager as? TieredHistory
                val oomSavedBytes = tieredMgr?.runOomCleaner() ?: 0L
                delay(1200L)
                refreshMeter()

                val statsPeakTiered = view.historyManager.stats()
                val peakHotMb = statsPeakTiered.hotBytes / mb
                val peakWarmMb = statsPeakTiered.warmBytes / mb
                val peakColdMb = statsPeakTiered.coldBytes / mb
                val totalActualBytes = statsPeakTiered.hotBytes + statsPeakTiered.warmBytes + statsPeakTiered.coldBytes
                val measuredCompRatio = if (totalActualBytes > 0L && statsPeakTiered.rawEquivalentBytes > 0L) {
                    statsPeakTiered.rawEquivalentBytes.toDouble() / totalActualBytes.toDouble()
                } else 1.0
                // everything that is stored (Hot raw + Warm and Cold compressed) per step: no flattering subset
                val avgTieredStepKb = if (statsPeakTiered.totalSteps > 0) (totalActualBytes / 1024.0) / statsPeakTiered.totalSteps else 0.0
                val tieredSteps = statsPeakTiered.totalSteps
                demoStage = demoStage.copy(
                    action = String.format(Locale.US, "Tiered: %d nét, chỉ %.1f MB RAM (+ %.1f MB trên đĩa)", tieredSteps, peakHotMb + peakWarmMb, peakColdMb)
                )
                delay(2600L)

                // Continuous undo: Cold steps come back through the prefetcher
                demoStage = demoStage.copy(
                    title = "3 · Tiered: hoàn tác liên tiếp",
                    explain = "Hoàn tác lấy nét mới nhất trước: Hot rồi Warm hết trước, sau đó tới Cold. Bộ nạp trước đưa vài nét kế tiếp đi lên: Cold → Warm (đọc đĩa) → Hot (giải nén sẵn, phần \"Sẵn\" trên thanh). Số nét đi lên bị giới hạn bởi ngân sách RAM.",
                    action = ""
                )
                delay(2400L)

                val undoTestSteps = 40
                var totalUndoNs = 0L
                var undoExecuted = 0
                for (u in 1..undoTestSteps) {
                    if (!isActive) return@launch
                    autoDemoProgress = 0.85f + (u.toFloat() / undoTestSteps) * 0.12f
                    val t0 = System.nanoTime()
                    val ok = view.undoSuspend()
                    val ms = (System.nanoTime() - t0) / 1_000_000.0
                    totalUndoNs += (System.nanoTime() - t0)
                    if (ok) undoExecuted++
                    val st = view.historyManager.stats()
                    val tp = st.prefetchHits + st.prefetchMisses
                    val hit = if (tp > 0) st.prefetchHits * 100.0 / tp else 0.0
                    refreshMeter(String.format(Locale.US, "Hoàn tác %d/%d · %.0f ms · nạp trước sẵn %d nét kế tiếp · trúng %.0f%%", u, undoTestSteps, ms, st.prefetchLookahead, hit))
                    delay(110L)
                }
                val realUndoLatencyMs = if (undoExecuted > 0) (totalUndoNs / 1_000_000.0) / undoExecuted else 0.0

                val statsAfterUndo = view.historyManager.stats()
                val totalPrefetch = statsAfterUndo.prefetchHits + statsAfterUndo.prefetchMisses
                val hitRatePct = if (totalPrefetch > 0) {
                    (statsAfterUndo.prefetchHits.toDouble() / totalPrefetch.toDouble()) * 100.0
                } else 0.0

                val swamRamCapSteps = if (avgTieredStepKb > 0.0) ((availableHistoryHeapMb * 1024.0) / avgTieredStepKb).toInt() else 0
                val swamDiskCapSteps = if (avgTieredStepKb > 0.0) swamRamCapSteps + ((coldBudgetMb * 1024.0) / avgTieredStepKb).toInt() else 0

                val tieredMetrics = ModeEvaluationMetrics(
                    mode = HistoryMode.TIERED,
                    stepsExecuted = tieredSteps,
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

                latestDemoReport = SwamComparisonReport(
                    deviceHeapLimitMb = maxHeapMb,
                    availableHeapForHistoryMb = availableHistoryHeapMb,
                    coldStorageBudgetMb = coldBudgetMb,
                    naive = naiveMetrics,
                    dirtyRect = dirtyMetrics,
                    tieredSwam = tieredMetrics
                )

                // The ending: what was measured, in plain words
                demoStage = demoStage.copy(
                    phase = 4, progress = 1f,
                    title = "Kết quả",
                    explain = String.format(
                        Locale.US,
                        "Naive: %d nét, %.0f MB. Dirty rect: %d nét, %.0f MB. Tiered: %d nét, %.1f MB RAM (+ %.1f MB đĩa). Hoàn tác trung bình %.0f ms, nạp trước trúng %.0f%%.",
                        naiveStats.totalSteps, naiveHotMb, dirtyStats.totalSteps, dirtyHotMb, tieredSteps, peakHotMb + peakWarmMb, peakColdMb,
                        realUndoLatencyMs, hitRatePct
                    ),
                    action = "Số đo thật trên thiết bị này, một lần chạy. Nhấn Báo cáo để xem chi tiết."
                )
                completed = true
            } finally {
                demoRestoreConfig?.let { cfg -> (drawingViewRef?.historyManager as? TieredHistory)?.updateTierConfig(cfg) }
                demoRestoreConfig = null
                isAutoDemoRunning = false
                if (!completed) demoStage = DemoStageState()
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
                    undoSteps = currentTelemetry.totalSteps,
                    redoSteps = currentTelemetry.redoSteps,
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
                            visible = demoStage.visible,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            DemoStageHeader(
                                state = demoStage,
                                onStop = {
                                    autoDemoJob?.cancel()
                                    isAutoDemoRunning = false
                                    demoStage = DemoStageState()
                                },
                                onOpenReport = { if (latestDemoReport != null) showDemoReportModal = true }
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

                        if (demoStage.visible) {
                            // the stage above is the one place to look while the demo runs
                        } else if (isXRayMinimized && !trackerVisible) {
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

                    // While the 3-mode demo runs, the scoreboard takes the place of the tool dock
                    androidx.compose.animation.AnimatedVisibility(
                        visible = demoStage.visible,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        DemoCharts(demoStage)
                    }

                    // Bottom: a one-line caption and the tool dock
                    if (!demoStage.visible) Column(
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

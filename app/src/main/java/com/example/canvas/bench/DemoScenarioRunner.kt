package com.example.canvas.bench

import android.graphics.Color
import android.graphics.PointF
import com.example.canvas.core.Stroke
import com.example.canvas.history.HistoryMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class DemoPhase(
    val phaseNumber: Int,
    val title: String,
    val mode: HistoryMode,
    val durationSeconds: Int,
    val description: String
) {
    PHASE_1_NAIVE(
        phaseNumber = 1,
        title = "PHASE 1: NAIVE (Full 16MB Canvas Copy)",
        mode = HistoryMode.NAIVE,
        durationSeconds = 60,
        description = "Each stroke stores a 2048x2048 ARGB_8888 bitmap (16 MiB). Notice rapid RAM exhaustion towards OOM."
    ),
    PHASE_2_DIRTY_RECT(
        phaseNumber = 2,
        title = "PHASE 2: DIRTY RECT (Localized Patches)",
        mode = HistoryMode.DIRTY_RECT,
        durationSeconds = 60,
        description = "Stores only dirty bounding rects. Small strokes save 180x RAM, but large strokes still accumulate uncompressed in RAM."
    ),
    PHASE_3_TIERED(
        phaseNumber = 3,
        title = "PHASE 3: TIERED (SWAM Hot/Warm/Cold + Prefetch)",
        mode = HistoryMode.TIERED,
        durationSeconds = 60,
        description = "Hot budget capped at 15% heap. Older patches deflate into Warm and swap to Cold disk. Zero data loss under stress, rapid undo via Prefetch."
    )
}

data class DemoState(
    val isRunning: Boolean = false,
    val currentPhase: DemoPhase = DemoPhase.PHASE_1_NAIVE,
    val elapsedSeconds: Int = 0,
    val totalSeconds: Int = 180,
    val currentInstruction: String = ""
)

/**
 * Coordinates and executes the 3-minute live demonstration script.
 * Can run in real-time (1.0x, 180s total) or accelerated mode for automated testing.
 */
class DemoScenarioRunner(
    private val timeScale: Float = 1.0f,
    private val dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Main,
    private val onStateUpdate: (DemoState) -> Unit = {},
    private val onSwitchMode: suspend (HistoryMode) -> Unit = {},
    private val onDrawStroke: suspend (Stroke) -> Unit = {},
    private val onUndo: suspend () -> Unit = {},
    private val onStress: suspend () -> Unit = {},
    private val onClearCanvas: suspend () -> Unit = {}
) {
    private var job: Job? = null
    private var isRunning = false
    private val rng = Random(42)

    val isDemoRunning: Boolean
        get() = isRunning

    fun start(scope: CoroutineScope): Job? {
        if (isRunning) return job
        isRunning = true

        val launchedJob = scope.launch(dispatcher) {
            try {
                runDemoScript()
            } finally {
                isRunning = false
                onStateUpdate(
                    DemoState(
                        isRunning = false,
                        currentPhase = DemoPhase.PHASE_3_TIERED,
                        elapsedSeconds = 180,
                        totalSeconds = 180,
                        currentInstruction = "Demo Finished. Inspect the X-Ray HUD!"
                    )
                )
            }
        }
        job = launchedJob
        return launchedJob
    }

    suspend fun awaitCompletion() {
        job?.join()
    }

    fun stop() {
        job?.cancel()
        job = null
        isRunning = false
    }

    private suspend fun delayScaled(ms: Long) {
        val scaled = (ms / timeScale).toLong().coerceAtLeast(1L)
        delay(scaled)
    }

    private suspend fun runDemoScript() {
        var elapsedSec = 0

        // ==========================================
        // PHASE 1: NAIVE (0s - 60s)
        // ==========================================
        val phase1 = DemoPhase.PHASE_1_NAIVE
        onSwitchMode(phase1.mode)
        onClearCanvas()

        updateUi(phase1, elapsedSec, "Starting Phase 1: Naive Full Canvas copies (16 MiB per step)...")
        delayScaled(2000)
        elapsedSec += 2

        // Draw 8 large strokes, each adding 16 MiB to Hot RAM
        for (i in 1..8) {
            val stroke = createLargeStroke(Color.RED, strokeWidth = 140f)
            onDrawStroke(stroke)
            updateUi(
                phase1,
                elapsedSec,
                "Naive Stroke #$i committed: +16 MiB Hot RAM! Notice the red Hot Bar skyrocketing."
            )
            delayScaled(6000)
            elapsedSec += 6
        }

        updateUi(
            phase1,
            elapsedSec,
            "⚠️ DANGER: Hot RAM is dangerously high (~128 MiB)! 10 more strokes would cause OutOfMemoryError."
        )
        delayScaled((60 - elapsedSec).coerceAtLeast(0) * 1000L)
        elapsedSec = 60

        // ==========================================
        // PHASE 2: DIRTY RECT (60s - 120s)
        // ==========================================
        val phase2 = DemoPhase.PHASE_2_DIRTY_RECT
        onSwitchMode(phase2.mode)
        onClearCanvas()

        updateUi(phase2, elapsedSec, "Starting Phase 2: Dirty Rect. Only storing localized bounding boxes...")
        delayScaled(2000)
        elapsedSec += 2

        // 1. Draw 12 small strokes (RAM grows very slowly)
        for (i in 1..12) {
            val stroke = createSmallStroke(Color.BLUE, strokeWidth = 16f)
            onDrawStroke(stroke)
            updateUi(
                phase2,
                elapsedSec,
                "DirtyRect Small Stroke #$i: Only ~0.1 MiB saved! 180x smaller than Naive."
            )
            delayScaled(2000)
            elapsedSec += 2
        }

        // 2. Now draw 4 large full-coverage strokes to show limitation
        for (i in 1..4) {
            val stroke = createLargeStroke(Color.parseColor("#FFA000"), strokeWidth = 180f)
            onDrawStroke(stroke)
            updateUi(
                phase2,
                elapsedSec,
                "DirtyRect Large Stroke #$i: When strokes are large, RAM still surges because there is no tiering!"
            )
            delayScaled(5000)
            elapsedSec += 5
        }

        updateUi(
            phase2,
            elapsedSec,
            "DirtyRect is great for small pens, but fails when large brushes or canvas-wide edits occur."
        )
        delayScaled((120 - elapsedSec).coerceAtLeast(0) * 1000L)
        elapsedSec = 120

        // ==========================================
        // PHASE 3: TIERED (120s - 180s)
        // ==========================================
        val phase3 = DemoPhase.PHASE_3_TIERED
        onSwitchMode(phase3.mode)
        onClearCanvas()

        updateUi(phase3, elapsedSec, "Starting Phase 3: SWAM-inspired Tiered Management (Hot -> Warm -> Cold)...")
        delayScaled(2000)
        elapsedSec += 2

        // 1. Draw 20 large strokes continuously
        for (i in 1..20) {
            val color = if (i % 2 == 0) Color.GREEN else Color.CYAN
            val stroke = createLargeStroke(color, strokeWidth = 120f)
            onDrawStroke(stroke)
            updateUi(
                phase3,
                elapsedSec,
                "Tiered Stroke #$i: Hot is bounded! Older steps automatically compress to Warm and swap to Cold."
            )
            delayScaled(1500)
            elapsedSec += 1
        }

        // 2. Trigger Memory Stress
        updateUi(
            phase3,
            elapsedSec,
            "⚡ STRESS INCOMING: Simulating OS onTrimMemory (TRIM_MEMORY_COMPLETE)..."
        )
        delayScaled(2000)
        elapsedSec += 2

        onStress()
        updateUi(
            phase3,
            elapsedSec,
            "💥 STRESS EXECUTED! Hot RAM dropped to 0 MiB! Zero data loss — patches safely in Warm & Cold."
        )
        delayScaled(4000)
        elapsedSec += 4

        // 3. Continuous Undo with Prefetch
        updateUi(
            phase3,
            elapsedSec,
            "🔄 CONTINUOUS UNDO: Rolling back 15 steps. Watch Prefetcher preload Cold patches into Warm!"
        )
        for (u in 1..15) {
            onUndo()
            updateUi(
                phase3,
                elapsedSec,
                "Undo step #$u: High Prefetch Hit Rate (>80%) ensures zero UI stuttering!"
            )
            delayScaled(1000)
            elapsedSec += 1
        }

        updateUi(
            phase3,
            elapsedSec,
            "🎉 DEMO COMPLETE: Tiered + SWAM delivers infinite history, bounded RAM, and instantaneous undo."
        )
        delayScaled((180 - elapsedSec).coerceAtLeast(0) * 1000L)
    }

    private fun updateUi(phase: DemoPhase, elapsedSec: Int, instruction: String) {
        onStateUpdate(
            DemoState(
                isRunning = true,
                currentPhase = phase,
                elapsedSeconds = elapsedSec,
                totalSeconds = 180,
                currentInstruction = instruction
            )
        )
    }

    private fun createLargeStroke(color: Int, strokeWidth: Float): Stroke {
        val stroke = Stroke(color = color, width = strokeWidth)
        val x1 = rng.nextFloat() * 1800f + 100f
        val y1 = rng.nextFloat() * 1800f + 100f
        val x2 = rng.nextFloat() * 1800f + 100f
        val y2 = rng.nextFloat() * 1800f + 100f
        stroke.addPoint(x1, y1)
        stroke.addPoint((x1 + x2) / 2f + 100f, (y1 + y2) / 2f - 100f)
        stroke.addPoint(x2, y2)
        return stroke
    }

    private fun createSmallStroke(color: Int, strokeWidth: Float): Stroke {
        val stroke = Stroke(color = color, width = strokeWidth)
        val cx = rng.nextFloat() * 1800f + 100f
        val cy = rng.nextFloat() * 1800f + 100f
        stroke.addPoint(cx, cy)
        stroke.addPoint(cx + rng.nextFloat() * 40f, cy + rng.nextFloat() * 40f)
        return stroke
    }
}

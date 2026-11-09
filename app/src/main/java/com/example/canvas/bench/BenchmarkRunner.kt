package com.example.canvas.bench

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import com.example.canvas.core.Patch
import com.example.canvas.core.StrokeRenderer
import com.example.canvas.history.HistoryManager
import com.example.canvas.history.HistoryMode
import com.example.canvas.metrics.MemorySampler
import com.example.canvas.metrics.MetricsCollector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.util.zip.CRC32

/**
 * Executes benchmark scenarios sequentially and records telemetry metrics.
 */
class BenchmarkRunner(
    private val historyManager: HistoryManager,
    private val canvasWidth: Int = 2048,
    private val canvasHeight: Int = 2048,
    /** true = patch only the dirty tiles a stroke touches instead of its whole bounding box. */
    private val tilingEnabled: Boolean = false
) {
    private val strokeRenderer = StrokeRenderer()

    @Volatile private var liveCanvas: Bitmap? = null

    /** Live metrics of the run in progress (null before it starts); read-only for the UI. */
    @Volatile var liveCollector: MetricsCollector? = null
        private set

    /** A small copy of the canvas being drawn right now, or null if no run is active. Safe to call from any thread. */
    fun snapshot(maxSide: Int): Bitmap? {
        val src = liveCanvas ?: return null
        return try {
            val scale = maxSide.toFloat() / maxOf(src.width, src.height)
            Bitmap.createScaledBitmap(src, (src.width * scale).toInt().coerceAtLeast(1), (src.height * scale).toInt().coerceAtLeast(1), true)
        } catch (_: Throwable) {
            null // recycled or out of memory: skip this frame
        }
    }

    /**
     * Executes the configured benchmark scenario.
     * Note: Section 7 specifies "chỉ bắt OutOfMemoryError trong benchmark".
     */
    suspend fun runBenchmark(
        config: BenchmarkConfig,
        scope: CoroutineScope,
        onProgress: ((completedSteps: Int, totalSteps: Int) -> Unit)? = null
    ): MetricsCollector = withContext(Dispatchers.Default) {
        val collector = MetricsCollector()
        val sampler = MemorySampler(
            historyManager = historyManager,
            intervalMs = config.sampleIntervalMs,
            onSample = {
                collector.recordSample(it)
                collector.recordHistoryStats(historyManager.stats())
            }
        )

        val drawer = ScriptedDrawer(
            seed = config.seed,
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight
        )

        // Like the drawing view: steps remember their stroke so that redo can replay it when pixels were given back
        historyManager.opRenderer = { c, op -> op.stroke?.let { strokeRenderer.renderStroke(c, it) } }

        // Reset history and create fresh blank canvas
        historyManager.clear()
        val backingBitmap = Bitmap.createBitmap(
            canvasWidth,
            canvasHeight,
            Bitmap.Config.ARGB_8888
        )
        val backingCanvas = Canvas(backingBitmap)
        backingCanvas.drawColor(Color.WHITE)

        // E5, E6 and E7 end by comparing the canvas with the blank one it started as (E5 used to compare with 0)
        val initialHash = if (config.scenario == ScenarioType.E5_STRESS_AND_RECOVER ||
            config.scenario == ScenarioType.E6_CORRECTNESS_HASH ||
            config.scenario == ScenarioType.E7_UNDO_REDO_BOUNDARY
        ) {
            computePixelCrc32(backingBitmap)
        } else {
            0L
        }

        liveCollector = collector
        liveCanvas = backingBitmap
        collector.start()
        sampler.start(scope)

        try {
            when (config.scenario) {
                ScenarioType.E1_DRAW_UNTIL_OOM -> {
                    runDrawUntilOom(
                        config = config,
                        drawer = drawer,
                        bitmap = backingBitmap,
                        canvas = backingCanvas,
                        collector = collector,
                        onProgress = onProgress
                    )
                }

                ScenarioType.E2_DRAW_AND_UNDO_ALL -> {
                    runDrawAndUndoAll(
                        config = config,
                        drawer = drawer,
                        bitmap = backingBitmap,
                        canvas = backingCanvas,
                        collector = collector,
                        onProgress = onProgress
                    )
                }

                ScenarioType.E3_UNDO_BY_TIER -> {
                    runUndoByTier(
                        config = config,
                        drawer = drawer,
                        bitmap = backingBitmap,
                        canvas = backingCanvas,
                        collector = collector,
                        onProgress = onProgress
                    )
                }

                ScenarioType.E4_CONTINUOUS_UNDO_PREFETCH -> {
                    runContinuousUndoPrefetch(
                        config = config,
                        drawer = drawer,
                        bitmap = backingBitmap,
                        canvas = backingCanvas,
                        collector = collector,
                        onProgress = onProgress
                    )
                }

                ScenarioType.E5_STRESS_AND_RECOVER -> {
                    runStressAndRecover(
                        config = config,
                        drawer = drawer,
                        bitmap = backingBitmap,
                        canvas = backingCanvas,
                        collector = collector,
                        initialHash = initialHash,
                        onProgress = onProgress
                    )
                }

                ScenarioType.E7_UNDO_REDO_BOUNDARY -> {
                    runUndoRedoBoundary(
                        config = config,
                        drawer = drawer,
                        bitmap = backingBitmap,
                        canvas = backingCanvas,
                        collector = collector,
                        initialHash = initialHash,
                        onProgress = onProgress
                    )
                }

                ScenarioType.E6_CORRECTNESS_HASH -> {
                    runCorrectnessHash(
                        config = config,
                        drawer = drawer,
                        bitmap = backingBitmap,
                        canvas = backingCanvas,
                        collector = collector,
                        initialHash = initialHash,
                        onProgress = onProgress
                    )
                }
            }
        } finally {
            sampler.stop()
            // Sample final state before freeing benchmark bitmaps
            collector.recordSample(sampler.captureSnapshot())
            collector.recordHistoryStats(historyManager.stats())
            historyManager.clear()
            liveCanvas = null
            backingBitmap.recycle()
            System.gc()
        }

        collector
    }

    private suspend fun runDrawUntilOom(
        config: BenchmarkConfig,
        drawer: ScriptedDrawer,
        bitmap: Bitmap,
        canvas: Canvas,
        collector: MetricsCollector,
        onProgress: ((Int, Int) -> Unit)?
    ) {
        val maxSteps = config.stepCount
        for (step in 1..maxSteps) {
            val stroke = drawer.nextStroke(config.strokeType)
            val startMs = System.currentTimeMillis()

            try {
                commitStroke(bitmap, canvas, stroke)
            } catch (oom: OutOfMemoryError) {
                // Caught as per specification
                collector.markOom(oom)
                return
            }

            val latency = System.currentTimeMillis() - startMs
            collector.recordStepLatency(latency)
            onProgress?.invoke(step, maxSteps)
        }
        collector.markSuccess()
    }

    private suspend fun runDrawAndUndoAll(
        config: BenchmarkConfig,
        drawer: ScriptedDrawer,
        bitmap: Bitmap,
        canvas: Canvas,
        collector: MetricsCollector,
        onProgress: ((Int, Int) -> Unit)?
    ) {
        val totalSteps = config.stepCount * 2
        // Phase 1: Draw strokes
        for (step in 1..config.stepCount) {
            val stroke = drawer.nextStroke(config.strokeType)
            val startMs = System.currentTimeMillis()

            try {
                commitStroke(bitmap, canvas, stroke)
            } catch (oom: OutOfMemoryError) {
                collector.markOom(oom)
                return
            }

            val latency = System.currentTimeMillis() - startMs
            collector.recordStepLatency(latency)
            onProgress?.invoke(step, totalSteps)
        }

        historyManager.settle()            // the draw phase is over: let the background tier moves arrive, then measure the undos
        // Phase 2: Undo all
        for (step in 1..config.stepCount) {
            val startMs = System.currentTimeMillis()
            val undone = try {
                undoTracked(bitmap, collector)
            } catch (oom: OutOfMemoryError) {
                collector.markOom(oom)
                return
            }
            val latency = System.currentTimeMillis() - startMs
            collector.recordStepLatency(latency)
            onProgress?.invoke(config.stepCount + step, totalSteps)
            if (!undone) break
        }

        collector.markSuccess()
    }

    private suspend fun runUndoByTier(
        config: BenchmarkConfig,
        drawer: ScriptedDrawer,
        bitmap: Bitmap,
        canvas: Canvas,
        collector: MetricsCollector,
        onProgress: ((Int, Int) -> Unit)?
    ) {
        val totalSteps = config.stepCount * 2
        // Phase 1: Draw strokes to populate tiers
        for (step in 1..config.stepCount) {
            val stroke = drawer.nextStroke(config.strokeType)
            val startMs = System.currentTimeMillis()
            try {
                commitStroke(bitmap, canvas, stroke)
            } catch (oom: OutOfMemoryError) {
                collector.markOom(oom)
                return
            }
            collector.recordStepLatency(System.currentTimeMillis() - startMs)
            onProgress?.invoke(step, totalSteps)
        }

        historyManager.settle()
        // Phase 2: Undo step by step, measuring latency by tier
        for (step in 1..config.stepCount) {
            val statsBefore = historyManager.stats()
            val tier = when {
                statsBefore.hotSteps > 0 -> "HOT"
                statsBefore.warmSteps > 0 -> "WARM"
                statsBefore.coldSteps > 0 -> "COLD"
                else -> "UNKNOWN"
            }

            val startMs = System.currentTimeMillis()
            val undone = try {
                undoTracked(bitmap, collector)
            } catch (oom: OutOfMemoryError) {
                collector.markOom(oom)
                return
            }
            val latency = System.currentTimeMillis() - startMs
            collector.recordStepLatency(latency)
            collector.recordTierLatency(tier, latency)
            onProgress?.invoke(config.stepCount + step, totalSteps)
            if (!undone) break
        }

        collector.markSuccess()
    }

    private suspend fun runCorrectnessHash(
        config: BenchmarkConfig,
        drawer: ScriptedDrawer,
        bitmap: Bitmap,
        canvas: Canvas,
        collector: MetricsCollector,
        initialHash: Long,
        onProgress: ((Int, Int) -> Unit)?
    ) {
        // Draw steps
        for (step in 1..config.stepCount) {
            val stroke = drawer.nextStroke(config.strokeType)
            val startMs = System.currentTimeMillis()
            try {
                commitStroke(bitmap, canvas, stroke)
            } catch (oom: OutOfMemoryError) {
                collector.markOom(oom)
                return
            }
            collector.recordStepLatency(System.currentTimeMillis() - startMs)
            onProgress?.invoke(step, config.stepCount * 2)
        }

        // Undo all
        for (step in 1..config.stepCount) {
            val startMs = System.currentTimeMillis()
            undoTracked(bitmap, collector)
            collector.recordStepLatency(System.currentTimeMillis() - startMs)
            onProgress?.invoke(config.stepCount + step, config.stepCount * 2)
        }

        // Compute restored hash
        val restoredHash = computePixelCrc32(bitmap)
        if (restoredHash == initialHash) {
            collector.markSuccess()
        } else {
            collector.markFailed(
                IllegalStateException("Pixel hash mismatch! Expected CRC $initialHash but got $restoredHash")
            )
        }
    }

    private suspend fun runContinuousUndoPrefetch(
        config: BenchmarkConfig,
        drawer: ScriptedDrawer,
        bitmap: Bitmap,
        canvas: Canvas,
        collector: MetricsCollector,
        onProgress: ((Int, Int) -> Unit)?
    ) {
        val totalSteps = config.stepCount * 2
        // Draw steps
        for (step in 1..config.stepCount) {
            val stroke = drawer.nextStroke(config.strokeType)
            val startMs = System.currentTimeMillis()
            try {
                commitStroke(bitmap, canvas, stroke)
            } catch (oom: OutOfMemoryError) {
                collector.markOom(oom)
                return
            }
            collector.recordStepLatency(System.currentTimeMillis() - startMs)
            onProgress?.invoke(step, totalSteps)
        }

        historyManager.settle()
        // Continuous undos (evaluating prefetch latency and hit rate)
        for (step in 1..config.stepCount) {
            val startMs = System.currentTimeMillis()
            val undone = undoTracked(bitmap, collector)
            val latency = System.currentTimeMillis() - startMs
            collector.recordStepLatency(latency)
            onProgress?.invoke(config.stepCount + step, totalSteps)
            if (!undone) break
            if (config.undoThinkMs > 0) kotlinx.coroutines.delay(config.undoThinkMs)
        }

        collector.markSuccess()
    }

    /**
     * Draws, then repeats rounds of "undo k steps, redo the same k steps". With small tiers the older steps sit in
     * Warm and Cold, so every round pulls steps across the tier boundary and pushes them back.
     */
    private suspend fun runUndoRedoBoundary(
        config: BenchmarkConfig,
        drawer: ScriptedDrawer,
        bitmap: Bitmap,
        canvas: Canvas,
        collector: MetricsCollector,
        initialHash: Long,
        onProgress: ((Int, Int) -> Unit)?
    ) {
        val k = config.cycleDepth.coerceIn(1, config.stepCount)
        val total = config.stepCount + config.cycleCount * k * 2
        var done = 0
        for (step in 1..config.stepCount) {
            val stroke = drawer.nextStroke(config.strokeType)
            val startMs = System.currentTimeMillis()
            try {
                commitStroke(bitmap, canvas, stroke)
            } catch (oom: OutOfMemoryError) {
                collector.markOom(oom)
                return
            }
            collector.recordStepLatency(System.currentTimeMillis() - startMs)
            onProgress?.invoke(++done, total)
        }
        historyManager.settle()
        repeat(config.cycleCount) {
            repeat(k) {
                val startMs = System.currentTimeMillis()
                undoTracked(bitmap, collector)
                collector.recordStepLatency(System.currentTimeMillis() - startMs)
                onProgress?.invoke(++done, total)
            }
            repeat(k) {
                val startMs = System.currentTimeMillis()
                redoTracked(bitmap, collector)
                collector.recordStepLatency(System.currentTimeMillis() - startMs)
                onProgress?.invoke(++done, total)
            }
        }
        // Proof that nothing was lost or changed on the way: undo everything, the canvas must be the blank one again
        while (undoTracked(bitmap, collector)) { /* all the way back */ }
        if (computePixelCrc32(bitmap) != initialHash) {
            collector.markFailed(IllegalStateException("Canvas khác trạng thái ban đầu sau các vòng Undo/Redo"))
            return
        }
        collector.markSuccess()
    }

    private suspend fun runStressAndRecover(
        config: BenchmarkConfig,
        drawer: ScriptedDrawer,
        bitmap: Bitmap,
        canvas: Canvas,
        collector: MetricsCollector,
        initialHash: Long,
        onProgress: ((Int, Int) -> Unit)?
    ) {
        val phase1Steps = (config.stepCount * 0.75).toInt().coerceAtLeast(1)
        val phase2Steps = (config.stepCount * 0.25).toInt().coerceAtLeast(1)
        val totalDrawSteps = phase1Steps + phase2Steps
        val totalOverallSteps = totalDrawSteps * 2

        // Phase 1: Draw until stress point
        for (step in 1..phase1Steps) {
            val stroke = drawer.nextStroke(config.strokeType)
            val startMs = System.currentTimeMillis()
            try {
                commitStroke(bitmap, canvas, stroke)
            } catch (oom: OutOfMemoryError) {
                collector.markOom(oom)
                return
            }
            collector.recordStepLatency(System.currentTimeMillis() - startMs)
            onProgress?.invoke(step, totalOverallSteps)
        }

        // Trigger memory stress (ComponentCallbacks2.TRIM_MEMORY_COMPLETE = 80)
        @Suppress("DEPRECATION")
        historyManager.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE)

        // Phase 2: Continue drawing after stress
        for (step in 1..phase2Steps) {
            val stroke = drawer.nextStroke(config.strokeType)
            val startMs = System.currentTimeMillis()
            try {
                commitStroke(bitmap, canvas, stroke)
            } catch (oom: OutOfMemoryError) {
                collector.markOom(oom)
                return
            }
            collector.recordStepLatency(System.currentTimeMillis() - startMs)
            onProgress?.invoke(phase1Steps + step, totalOverallSteps)
        }

        // Phase 3: Undo all to verify zero data loss and full recovery
        for (step in 1..totalDrawSteps) {
            val startMs = System.currentTimeMillis()
            val undone = undoTracked(bitmap, collector)
            val latency = System.currentTimeMillis() - startMs
            collector.recordStepLatency(latency)
            onProgress?.invoke(totalDrawSteps + step, totalOverallSteps)
            if (!undone) break
        }

        val restoredHash = computePixelCrc32(bitmap)
        if (restoredHash == initialHash) {
            collector.markSuccess()
        } else {
            collector.markFailed(
                IllegalStateException("Pixel hash mismatch after Stress! Expected CRC $initialHash but got $restoredHash")
            )
        }
    }

    /** One undo, counted, with how far ahead the prefetcher had prepared afterwards. */
    private suspend fun undoTracked(bitmap: Bitmap, collector: MetricsCollector): Boolean {
        val ok = historyManager.undo(bitmap)
        if (ok) collector.recordUndoDone(historyManager.stats().prefetchLookahead)
        return ok
    }

    private suspend fun redoTracked(bitmap: Bitmap, collector: MetricsCollector): Boolean {
        val ok = historyManager.redo(bitmap)
        if (ok) collector.recordRedoDone()
        return ok
    }

    private suspend fun commitStroke(bitmap: Bitmap, canvas: Canvas, stroke: com.example.canvas.core.Stroke) {
        val beforePatches: List<Patch> = if (historyManager.mode == HistoryMode.NAIVE) {
            if (canvasWidth >= 1024 && canvasHeight >= 1024) {
                val requiredBytes = canvasWidth.toLong() * canvasHeight.toLong() * 4L
                val currentHotBytes = historyManager.stats().hotBytes
                val rt = Runtime.getRuntime()
                val safeCapBytes = kotlin.math.min((rt.maxMemory() * 0.50).toLong(), 128L * 1024L * 1024L)
                val freeHeadroom = rt.maxMemory() - (rt.totalMemory() - rt.freeMemory())
                if (currentHotBytes + requiredBytes > safeCapBytes || freeHeadroom - requiredBytes < 48L * 1024L * 1024L) {
                    throw OutOfMemoryError("Chạm trần bảo vệ Cận-OOM (${currentHotBytes / (1024 * 1024)} MB) trong Benchmark NAIVE")
                }
            }
            val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                ?: throw OutOfMemoryError("Bitmap.copy failed to allocate full canvas")
            listOf(
                Patch(
                    id = System.nanoTime(),
                    rect = Rect(0, 0, canvasWidth, canvasHeight),
                    bitmap = copy,
                    sizeBytes = copy.allocationByteCount.toLong()
                )
            )
        } else {
            val regions = if (tilingEnabled) {
                stroke.computeDirtyRegions(canvasWidth, canvasHeight)
            } else {
                listOf(stroke.computeBounds(canvasWidth, canvasHeight))
            }
            if (historyManager.mode == HistoryMode.DIRTY_RECT && canvasWidth >= 1024 && canvasHeight >= 1024) {
                val requiredBytes = regions.sumOf { it.width().coerceAtLeast(1).toLong() * it.height().coerceAtLeast(1) * 4L }
                val currentHotBytes = historyManager.stats().hotBytes
                val rt = Runtime.getRuntime()
                val safeCapBytes = kotlin.math.min((rt.maxMemory() * 0.58).toLong(), 160L * 1024L * 1024L)
                val freeHeadroom = rt.maxMemory() - (rt.totalMemory() - rt.freeMemory())
                if (currentHotBytes + requiredBytes > safeCapBytes || freeHeadroom - requiredBytes < 48L * 1024L * 1024L) {
                    throw OutOfMemoryError("Chạm trần bảo vệ Cận-OOM (${currentHotBytes / (1024 * 1024)} MB) trong Benchmark DIRTY_RECT")
                }
            }
            val groupId = if (regions.size > 1) System.nanoTime() else 0L
            val op = com.example.canvas.core.StrokeOp(stroke = stroke)
            regions.mapIndexed { i, rect ->
                val patchBmp = Bitmap.createBitmap(
                    bitmap,
                    rect.left,
                    rect.top,
                    rect.width().coerceAtLeast(1),
                    rect.height().coerceAtLeast(1)
                )
                Patch(
                    id = System.nanoTime() + i,
                    rect = rect,
                    bitmap = patchBmp,
                    sizeBytes = patchBmp.allocationByteCount.toLong(),
                    groupId = groupId,
                    op = op
                )
            }
        }

        strokeRenderer.renderStroke(canvas, stroke)
        for (p in beforePatches) historyManager.commit(p)
    }

    private fun computePixelCrc32(bitmap: Bitmap): Long {
        val byteBuffer = ByteBuffer.allocate(bitmap.byteCount)
        bitmap.copyPixelsToBuffer(byteBuffer)
        val crc = CRC32()
        crc.update(byteBuffer.array())
        return crc.value
    }
}

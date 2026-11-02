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
    private val canvasHeight: Int = 2048
) {
    private val strokeRenderer = StrokeRenderer()

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
            onSample = { collector.recordSample(it) }
        )

        val drawer = ScriptedDrawer(
            seed = config.seed,
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight
        )

        // Reset history and create fresh blank canvas
        historyManager.clear()
        val backingBitmap = Bitmap.createBitmap(
            canvasWidth,
            canvasHeight,
            Bitmap.Config.ARGB_8888
        )
        val backingCanvas = Canvas(backingBitmap)
        backingCanvas.drawColor(Color.WHITE)

        val initialHash = if (config.scenario == ScenarioType.E6_CORRECTNESS_HASH) {
            computePixelCrc32(backingBitmap)
        } else {
            0L
        }

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
            historyManager.clear()
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

        // Phase 2: Undo all
        for (step in 1..config.stepCount) {
            val startMs = System.currentTimeMillis()
            val undone = try {
                historyManager.undo(bitmap)
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
                historyManager.undo(bitmap)
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
            historyManager.undo(bitmap)
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

        // Continuous undos (evaluating prefetch latency and hit rate)
        for (step in 1..config.stepCount) {
            val startMs = System.currentTimeMillis()
            val undone = historyManager.undo(bitmap)
            val latency = System.currentTimeMillis() - startMs
            collector.recordStepLatency(latency)
            onProgress?.invoke(config.stepCount + step, totalSteps)
            if (!undone) break
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
            val undone = historyManager.undo(bitmap)
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

    private suspend fun commitStroke(bitmap: Bitmap, canvas: Canvas, stroke: com.example.canvas.core.Stroke) {
        val beforePatch = if (historyManager.mode == HistoryMode.NAIVE) {
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
            Patch(
                id = System.nanoTime(),
                rect = Rect(0, 0, canvasWidth, canvasHeight),
                bitmap = copy,
                sizeBytes = copy.allocationByteCount.toLong()
            )
        } else {
            val bounds = stroke.computeBounds(canvasWidth, canvasHeight)
            val w = bounds.width().coerceAtLeast(1)
            val h = bounds.height().coerceAtLeast(1)
            if (historyManager.mode == HistoryMode.DIRTY_RECT && canvasWidth >= 1024 && canvasHeight >= 1024) {
                val requiredBytes = w.toLong() * h.toLong() * 4L
                val currentHotBytes = historyManager.stats().hotBytes
                val rt = Runtime.getRuntime()
                val safeCapBytes = kotlin.math.min((rt.maxMemory() * 0.58).toLong(), 160L * 1024L * 1024L)
                val freeHeadroom = rt.maxMemory() - (rt.totalMemory() - rt.freeMemory())
                if (currentHotBytes + requiredBytes > safeCapBytes || freeHeadroom - requiredBytes < 48L * 1024L * 1024L) {
                    throw OutOfMemoryError("Chạm trần bảo vệ Cận-OOM (${currentHotBytes / (1024 * 1024)} MB) trong Benchmark DIRTY_RECT")
                }
            }
            val patchBmp = Bitmap.createBitmap(
                bitmap,
                bounds.left,
                bounds.top,
                w,
                h
            )
            Patch(
                id = System.nanoTime(),
                rect = bounds,
                bitmap = patchBmp,
                sizeBytes = patchBmp.allocationByteCount.toLong()
            )
        }

        strokeRenderer.renderStroke(canvas, stroke)
        historyManager.commit(beforePatch)
    }

    private fun computePixelCrc32(bitmap: Bitmap): Long {
        val byteBuffer = ByteBuffer.allocate(bitmap.byteCount)
        bitmap.copyPixelsToBuffer(byteBuffer)
        val crc = CRC32()
        crc.update(byteBuffer.array())
        return crc.value
    }
}

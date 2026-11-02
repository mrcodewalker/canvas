package com.example.canvas.metrics

import android.os.Debug
import com.example.canvas.history.HistoryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Periodically samples memory usage (Java heap, Native heap, PSS, GC count)
 * and history tier sizes every 250 ms.
 */
class MemorySampler(
    private val historyManager: HistoryManager,
    val intervalMs: Long = 250L,
    private val onSample: (MemorySnapshot) -> Unit
) {
    private var job: Job? = null
    private var startTimestampMs: Long = 0L

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        startTimestampMs = System.currentTimeMillis()

        job = scope.launch(Dispatchers.Default) {
            while (isActive) {
                val now = System.currentTimeMillis()
                val elapsed = now - startTimestampMs
                val snapshot = captureSnapshot(now, elapsed)
                onSample(snapshot)
                delay(intervalMs)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /**
     * Captures a single telemetry snapshot at the current moment.
     */
    fun captureSnapshot(
        timestampMs: Long = System.currentTimeMillis(),
        elapsedMs: Long = 0L
    ): MemorySnapshot {
        // 1. Java heap
        val runtime = Runtime.getRuntime()
        val javaHeapBytes = runtime.totalMemory() - runtime.freeMemory()

        // 2. Native heap (From Android 8.0, Bitmap pixel memory resides here)
        val nativeHeapBytes = try {
            Debug.getNativeHeapAllocatedSize()
        } catch (_: Throwable) {
            0L
        }

        // 3. PSS (Proportional Set Size)
        val pssBytes = try {
            val memInfo = Debug.MemoryInfo()
            Debug.getMemoryInfo(memInfo)
            memInfo.totalPss.toLong() * 1024L
        } catch (_: Throwable) {
            0L
        }

        // 4. ART GC Count
        val gcCount = try {
            val countStr = Debug.getRuntimeStat("art.gc.gc-count")
            countStr?.toLongOrNull() ?: -1L
        } catch (_: Throwable) {
            -1L
        }

        // 5. Tier sizes from HistoryManager
        val stats = historyManager.stats()

        return MemorySnapshot(
            timestampMs = timestampMs,
            elapsedMs = elapsedMs,
            javaHeapBytes = javaHeapBytes,
            nativeHeapBytes = nativeHeapBytes,
            pssBytes = pssBytes,
            gcCount = gcCount,
            hotBytes = stats.hotBytes,
            warmBytes = stats.warmBytes,
            coldBytes = stats.coldBytes,
            totalSteps = stats.totalSteps
        )
    }
}

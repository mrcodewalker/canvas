package com.example.canvas.metrics

import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.max

enum class BenchmarkStatus {
    IDLE,
    RUNNING,
    SUCCESS,
    OOM_CRASH,
    FAILED
}

/**
 * Aggregates runtime telemetry samples and benchmark execution metrics.
 */
class MetricsCollector {

    private val samples = CopyOnWriteArrayList<MemorySnapshot>()
    private val stepLatenciesMs = CopyOnWriteArrayList<Long>()
    private val tierLatencies = java.util.concurrent.ConcurrentHashMap<String, CopyOnWriteArrayList<Long>>()

    var status: BenchmarkStatus = BenchmarkStatus.IDLE
        private set

    var startTimeMs: Long = 0L
        private set
    var endTimeMs: Long = 0L
        private set

    var initialGcCount: Long = -1L
        private set
    var finalGcCount: Long = -1L
        private set

    var completedSteps: Int = 0
        private set

    var errorMessage: String? = null
        private set

    fun start() {
        samples.clear()
        stepLatenciesMs.clear()
        tierLatencies.clear()
        completedSteps = 0
        errorMessage = null
        status = BenchmarkStatus.RUNNING
        startTimeMs = System.currentTimeMillis()
        endTimeMs = 0L
        initialGcCount = readCurrentGcCount()
    }

    fun recordSample(snapshot: MemorySnapshot) {
        samples.add(snapshot)
    }

    fun recordStepLatency(latencyMs: Long) {
        stepLatenciesMs.add(latencyMs)
        completedSteps++
    }

    fun recordTierLatency(tier: String, latencyMs: Long) {
        tierLatencies.computeIfAbsent(tier) { CopyOnWriteArrayList() }.add(latencyMs)
    }

    fun getTierLatencies(tier: String): List<Long> =
        tierLatencies[tier]?.toList() ?: emptyList()

    fun percentileTierLatencyMs(tier: String, percentile: Double): Double {
        val list = tierLatencies[tier] ?: return 0.0
        if (list.isEmpty()) return 0.0
        val sorted = list.sorted()
        val index = ((percentile / 100.0) * (sorted.size - 1)).toInt()
        return sorted[index.coerceIn(0, sorted.size - 1)].toDouble()
    }

    fun averageTierLatencyMs(tier: String): Double {
        val list = tierLatencies[tier] ?: return 0.0
        if (list.isEmpty()) return 0.0
        return list.average()
    }

    fun markSuccess() {
        endTimeMs = System.currentTimeMillis()
        finalGcCount = readCurrentGcCount()
        status = BenchmarkStatus.SUCCESS
    }

    fun markOom(error: OutOfMemoryError) {
        endTimeMs = System.currentTimeMillis()
        finalGcCount = readCurrentGcCount()
        status = BenchmarkStatus.OOM_CRASH
        errorMessage = error.message ?: "OutOfMemoryError: Java/Native heap limit exceeded"
    }

    fun markFailed(throwable: Throwable) {
        endTimeMs = System.currentTimeMillis()
        finalGcCount = readCurrentGcCount()
        status = BenchmarkStatus.FAILED
        errorMessage = throwable.message ?: throwable.javaClass.simpleName
    }

    val durationMs: Long
        get() = if (endTimeMs > startTimeMs) endTimeMs - startTimeMs else 0L

    val gcDelta: Long
        get() = if (initialGcCount >= 0 && finalGcCount >= initialGcCount) {
            finalGcCount - initialGcCount
        } else {
            0L
        }

    val peakJavaHeapBytes: Long
        get() = samples.maxOfOrNull { it.javaHeapBytes } ?: 0L

    val peakNativeHeapBytes: Long
        get() = samples.maxOfOrNull { it.nativeHeapBytes } ?: 0L

    val peakPssBytes: Long
        get() = samples.maxOfOrNull { it.pssBytes } ?: 0L

    val peakCombinedHeapBytes: Long
        get() = samples.maxOfOrNull { it.javaHeapBytes + it.nativeHeapBytes } ?: 0L

    fun getSamples(): List<MemorySnapshot> = samples.toList()

    fun getLatencies(): List<Long> = stepLatenciesMs.toList()

    fun averageLatencyMs(): Double {
        if (stepLatenciesMs.isEmpty()) return 0.0
        return stepLatenciesMs.average()
    }

    fun percentileLatencyMs(percentile: Double): Double {
        if (stepLatenciesMs.isEmpty()) return 0.0
        val sorted = stepLatenciesMs.sorted()
        val index = ((percentile / 100.0) * (sorted.size - 1)).toInt()
        return sorted[index.coerceIn(0, sorted.size - 1)].toDouble()
    }

    private fun readCurrentGcCount(): Long {
        return try {
            val countStr = android.os.Debug.getRuntimeStat("art.gc.gc-count")
            countStr?.toLongOrNull() ?: -1L
        } catch (_: Throwable) {
            -1L
        }
    }
}

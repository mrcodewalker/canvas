package com.example.canvas.ui

import com.example.canvas.history.HistoryStats
import com.example.canvas.metrics.MemorySnapshot
import java.util.concurrent.CopyOnWriteArrayList

data class XRayPoint(
    val timestampMs: Long,
    val javaHeapMb: Float,
    val nativeHeapMb: Float,
    val totalHeapMb: Float
)

data class XRayCurrentTelemetry(
    val latestSnapshot: MemorySnapshot? = null,
    val hotMb: Float = 0f,
    val warmMb: Float = 0f,
    val coldMb: Float = 0f,
    val hotSteps: Int = 0,
    val warmSteps: Int = 0,
    val coldSteps: Int = 0,
    val totalSteps: Int = 0,
    val redoSteps: Int = 0,
    val gcCount: Long = 0L,
    val gcDelta: Long = 0L,
    val prefetchHits: Int = 0,
    val prefetchMisses: Int = 0,
    val prefetchHitRate: Float = 0f,
    val peakHeapMb: Float = 0f,
    val currentJavaHeapMb: Float = 0f,
    val currentNativeHeapMb: Float = 0f
)

/**
 * Tracks a rolling 60-second window of memory telemetry points and current tier metrics
 * for rendering the X-Ray HUD overlay.
 */
class XRayTelemetryTracker(
    val windowDurationMs: Long = 60_000L
) {
    private val points = CopyOnWriteArrayList<XRayPoint>()
    private var baselineGcCount: Long = -1L
    private var lastSnapshot: MemorySnapshot? = null
    private var lastStats: HistoryStats = HistoryStats()

    @Synchronized
    fun addSample(snapshot: MemorySnapshot, stats: HistoryStats = HistoryStats()) {
        val now = snapshot.timestampMs
        val javaMb = snapshot.javaHeapBytes / (1024f * 1024f)
        val nativeMb = snapshot.nativeHeapBytes / (1024f * 1024f)
        val totalMb = javaMb + nativeMb

        if (baselineGcCount < 0 && snapshot.gcCount >= 0) {
            baselineGcCount = snapshot.gcCount
        }

        points.add(
            XRayPoint(
                timestampMs = now,
                javaHeapMb = javaMb,
                nativeHeapMb = nativeMb,
                totalHeapMb = totalMb
            )
        )

        // Trim points older than windowDurationMs
        val cutoff = now - windowDurationMs
        while (points.isNotEmpty() && points.first().timestampMs < cutoff) {
            points.removeAt(0)
        }

        lastSnapshot = snapshot
        lastStats = stats
    }

    fun getPoints(): List<XRayPoint> = points.toList()

    fun getPeakTotalMb(): Float {
        return points.maxOfOrNull { it.totalHeapMb } ?: 0f
    }

    fun getCurrentTelemetry(): XRayCurrentTelemetry {
        val snap = lastSnapshot
        val stats = lastStats

        val hotMb = (stats.hotBytes / (1024f * 1024f))
        val warmMb = (stats.warmBytes / (1024f * 1024f))
        val coldMb = (stats.coldBytes / (1024f * 1024f))

        val gcDelta = if (snap != null && snap.gcCount >= 0 && baselineGcCount >= 0) {
            (snap.gcCount - baselineGcCount).coerceAtLeast(0L)
        } else {
            0L
        }

        val totalPrefetch = stats.prefetchHits + stats.prefetchMisses
        val hitRate = if (totalPrefetch > 0) {
            (stats.prefetchHits.toFloat() / totalPrefetch) * 100f
        } else {
            0f
        }

        val javaMb = snap?.let { it.javaHeapBytes / (1024f * 1024f) } ?: 0f
        val nativeMb = snap?.let { it.nativeHeapBytes / (1024f * 1024f) } ?: 0f

        return XRayCurrentTelemetry(
            latestSnapshot = snap,
            hotMb = hotMb,
            warmMb = warmMb,
            coldMb = coldMb,
            hotSteps = stats.hotSteps,
            warmSteps = stats.warmSteps,
            coldSteps = stats.coldSteps,
            totalSteps = stats.totalSteps,
            redoSteps = stats.redoSteps,
            gcCount = snap?.gcCount ?: 0L,
            gcDelta = gcDelta,
            prefetchHits = stats.prefetchHits,
            prefetchMisses = stats.prefetchMisses,
            prefetchHitRate = hitRate,
            peakHeapMb = getPeakTotalMb(),
            currentJavaHeapMb = javaMb,
            currentNativeHeapMb = nativeMb
        )
    }

    @Synchronized
    fun clear() {
        points.clear()
        baselineGcCount = -1L
        lastSnapshot = null
        lastStats = HistoryStats()
    }
}

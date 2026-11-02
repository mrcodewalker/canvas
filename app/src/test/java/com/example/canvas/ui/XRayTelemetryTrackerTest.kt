package com.example.canvas.ui

import com.example.canvas.history.HistoryStats
import com.example.canvas.metrics.MemorySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XRayTelemetryTrackerTest {

    @Test
    fun testAddSampleAndRollingWindow() {
        val tracker = XRayTelemetryTracker(windowDurationMs = 60_000L)

        val t0 = 100_000L
        tracker.addSample(
            MemorySnapshot(
                timestampMs = t0,
                elapsedMs = 0L,
                javaHeapBytes = 10 * 1024 * 1024L,
                nativeHeapBytes = 20 * 1024 * 1024L,
                pssBytes = 30 * 1024 * 1024L,
                gcCount = 5L,
                hotBytes = 10 * 1024 * 1024L,
                warmBytes = 0L,
                coldBytes = 0L,
                totalSteps = 1
            )
        )

        assertEquals(1, tracker.getPoints().size)
        val p1 = tracker.getPoints().first()
        assertEquals(10f, p1.javaHeapMb, 0.01f)
        assertEquals(20f, p1.nativeHeapMb, 0.01f)
        assertEquals(30f, p1.totalHeapMb, 0.01f)

        // Add sample at t0 + 30s
        tracker.addSample(
            MemorySnapshot(
                timestampMs = t0 + 30_000L,
                elapsedMs = 30_000L,
                javaHeapBytes = 15 * 1024 * 1024L,
                nativeHeapBytes = 25 * 1024 * 1024L,
                pssBytes = 40 * 1024 * 1024L,
                gcCount = 6L,
                hotBytes = 15 * 1024 * 1024L,
                warmBytes = 0L,
                coldBytes = 0L,
                totalSteps = 2
            )
        )
        assertEquals(2, tracker.getPoints().size)

        // Add sample at t0 + 65s (first sample at t0 should be trimmed as it is >60s old)
        tracker.addSample(
            MemorySnapshot(
                timestampMs = t0 + 65_000L,
                elapsedMs = 65_000L,
                javaHeapBytes = 20 * 1024 * 1024L,
                nativeHeapBytes = 30 * 1024 * 1024L,
                pssBytes = 50 * 1024 * 1024L,
                gcCount = 7L,
                hotBytes = 20 * 1024 * 1024L,
                warmBytes = 0L,
                coldBytes = 0L,
                totalSteps = 3
            )
        )

        val remaining = tracker.getPoints()
        assertEquals(2, remaining.size)
        assertEquals(t0 + 30_000L, remaining[0].timestampMs)
        assertEquals(t0 + 65_000L, remaining[1].timestampMs)
    }

    @Test
    fun testPeakHeapMb() {
        val tracker = XRayTelemetryTracker(windowDurationMs = 60_000L)
        val t0 = 10_000L

        tracker.addSample(
            MemorySnapshot(
                timestampMs = t0,
                javaHeapBytes = 10 * 1024 * 1024L,
                nativeHeapBytes = 20 * 1024 * 1024L
            )
        ) // 30 MB

        tracker.addSample(
            MemorySnapshot(
                timestampMs = t0 + 5_000L,
                javaHeapBytes = 50 * 1024 * 1024L,
                nativeHeapBytes = 30 * 1024 * 1024L
            )
        ) // 80 MB (peak)

        tracker.addSample(
            MemorySnapshot(
                timestampMs = t0 + 10_000L,
                javaHeapBytes = 20 * 1024 * 1024L,
                nativeHeapBytes = 20 * 1024 * 1024L
            )
        ) // 40 MB

        assertEquals(80f, tracker.getPeakTotalMb(), 0.01f)
    }

    @Test
    fun testGcDeltaAndPrefetchHitRate() {
        val tracker = XRayTelemetryTracker(windowDurationMs = 60_000L)
        val t0 = 1_000L

        // Baseline sample: GC count = 10
        tracker.addSample(
            snapshot = MemorySnapshot(
                timestampMs = t0,
                gcCount = 10L,
                javaHeapBytes = 8 * 1024 * 1024L,
                nativeHeapBytes = 12 * 1024 * 1024L
            ),
            stats = HistoryStats(
                hotBytes = 5 * 1024 * 1024L,
                warmBytes = 2 * 1024 * 1024L,
                coldBytes = 1 * 1024 * 1024L,
                prefetchHits = 8,
                prefetchMisses = 2
            )
        )

        var tel = tracker.getCurrentTelemetry()
        assertEquals(10L, tel.gcCount)
        assertEquals(0L, tel.gcDelta)
        assertEquals(80.0f, tel.prefetchHitRate, 0.01f)
        assertEquals(8, tel.prefetchHits)
        assertEquals(2, tel.prefetchMisses)

        // Subsequent sample: GC count rises to 14 (+4 runs)
        tracker.addSample(
            snapshot = MemorySnapshot(
                timestampMs = t0 + 1000L,
                gcCount = 14L,
                javaHeapBytes = 8 * 1024 * 1024L,
                nativeHeapBytes = 12 * 1024 * 1024L
            ),
            stats = HistoryStats(
                prefetchHits = 19,
                prefetchMisses = 1
            )
        )

        tel = tracker.getCurrentTelemetry()
        assertEquals(14L, tel.gcCount)
        assertEquals(4L, tel.gcDelta)
        // 19 / (19 + 1) = 95.0%
        assertEquals(95.0f, tel.prefetchHitRate, 0.01f)
    }

    @Test
    fun testClear() {
        val tracker = XRayTelemetryTracker()
        tracker.addSample(
            MemorySnapshot(
                timestampMs = 100L,
                gcCount = 5L,
                javaHeapBytes = 1024L,
                nativeHeapBytes = 1024L
            )
        )
        assertTrue(tracker.getPoints().isNotEmpty())

        tracker.clear()
        assertTrue(tracker.getPoints().isEmpty())
        assertEquals(0f, tracker.getPeakTotalMb(), 0.01f)
    }
}

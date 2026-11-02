package com.example.canvas.metrics

/**
 * Point-in-time telemetry snapshot of memory allocations and tier statistics.
 *
 * @param timestampMs System timestamp in milliseconds.
 * @param elapsedMs Elapsed milliseconds since the start of the benchmark/monitoring session.
 * @param javaHeapBytes Java heap memory currently in use (Runtime.totalMemory - freeMemory).
 * @param nativeHeapBytes Native heap memory allocated (Debug.getNativeHeapAllocatedSize).
 * @param pssBytes Proportional Set Size (Debug.MemoryInfo.totalPss * 1024).
 * @param gcCount ART garbage collection count from art.gc.gc-count.
 * @param hotBytes Memory footprint of uncompressed Bitmap patches in RAM.
 * @param warmBytes Memory footprint of deflated byte patches in RAM.
 * @param coldBytes Total disk footprint of swapped patch files.
 * @param totalSteps Total undoable steps currently in history.
 */
data class MemorySnapshot(
    val timestampMs: Long,
    val elapsedMs: Long,
    val javaHeapBytes: Long,
    val nativeHeapBytes: Long,
    val pssBytes: Long,
    val gcCount: Long,
    val hotBytes: Long,
    val warmBytes: Long,
    val coldBytes: Long,
    val totalSteps: Int
) {
    val javaHeapMb: Double get() = javaHeapBytes / (1024.0 * 1024.0)
    val nativeHeapMb: Double get() = nativeHeapBytes / (1024.0 * 1024.0)
    val pssMb: Double get() = pssBytes / (1024.0 * 1024.0)
    val totalHeapMb: Double get() = (javaHeapBytes + nativeHeapBytes) / (1024.0 * 1024.0)
    val hotMb: Double get() = hotBytes / (1024.0 * 1024.0)
    val warmMb: Double get() = warmBytes / (1024.0 * 1024.0)
    val coldMb: Double get() = coldBytes / (1024.0 * 1024.0)
}

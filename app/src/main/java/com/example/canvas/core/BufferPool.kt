package com.example.canvas.core

import java.lang.ref.SoftReference

/**
 * Reusable byte buffers for compressing and decompressing patches, so that every undo or demotion does not
 * allocate (and the GC does not collect) a pixel-sized array.
 *
 * What may be kept is bounded by [limit], which the history ties to its own RAM budget. The first version kept
 * one scratch buffer per thread; with several worker threads and full-canvas patches that pinned over a
 * hundred megabytes of Java heap, so retention is now global, size-matched and capped. Buffers larger than the
 * cap are simply allocated and dropped, exactly as without a pool.
 */
object BufferPool {

    /** Maximum bytes the pool may retain. 0 (default) disables reuse. */
    @Volatile
    var limit: () -> Long = { 0L }

    private val free = ArrayList<SoftReference<ByteArray>>()
    private var retained = 0L

    /** An array of at least [size] bytes (at most twice as large when reused). Contents are undefined. */
    @Synchronized
    fun acquire(size: Int): ByteArray {
        val it = free.iterator()
        while (it.hasNext()) {
            val a = it.next().get()
            if (a == null) {
                it.remove()
                continue
            }
            if (a.size >= size && a.size.toLong() <= size * 2L) {
                it.remove()
                retained -= a.size
                return a
            }
        }
        return ByteArray(size)
    }

    @Synchronized
    fun release(buffer: ByteArray) {
        prune()
        if (retained + buffer.size <= limit()) {
            free.add(SoftReference(buffer))
            retained += buffer.size
        }
    }

    /** Bytes currently kept for reuse (for tests and telemetry). */
    @Synchronized
    fun retainedBytes(): Long {
        prune()
        return retained
    }

    @Synchronized
    fun clear() {
        free.clear()
        retained = 0L
    }

    private fun prune() {
        var sum = 0L
        val it = free.iterator()
        while (it.hasNext()) {
            val a = it.next().get()
            if (a == null) it.remove() else sum += a.size
        }
        retained = sum
    }
}

package com.example.canvas.history

import com.example.canvas.core.Patch

/**
 * Manages raw uncompressed Bitmap patches residing directly in RAM (Hot tier).
 */
class HotStore {

    private val queue = ArrayDeque<Patch>()
    var totalBytes: Long = 0L
        private set

    val count: Int get() = queue.size

    fun add(patch: Patch) {
        queue.addLast(patch)
        totalBytes += patch.sizeBytes
    }

    fun removeOldest(): Patch? {
        if (queue.isEmpty()) return null
        val oldest = queue.removeFirst()
        totalBytes = (totalBytes - oldest.sizeBytes).coerceAtLeast(0L)
        return oldest
    }

    fun remove(patch: Patch): Boolean {
        val removed = queue.remove(patch)
        if (removed) {
            totalBytes = (totalBytes - patch.sizeBytes).coerceAtLeast(0L)
        }
        return removed
    }

    fun peekOldest(): Patch? = queue.firstOrNull()

    fun getAll(): List<Patch> = queue.toList()

    fun clear() {
        for (patch in queue) {
            patch.recycle()
        }
        queue.clear()
        totalBytes = 0L
    }
}

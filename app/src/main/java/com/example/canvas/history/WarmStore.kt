package com.example.canvas.history

import com.example.canvas.core.Patch

/**
 * Manages deflated, lossless compressed byte array patches stored in RAM (Warm tier).
 */
class WarmStore {

    private val queue = PatchQueue()
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

    fun contains(patch: Patch): Boolean = queue.contains(patch)

    fun peekOldest(): Patch? = queue.firstOrNull()

    fun getAll(): List<Patch> = queue.toList()

    fun recalculateTotalBytes() {
        val seenArrays = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<ByteArray, Boolean>())
        var sum = 0L
        for (p in queue) {
            val arr = p.compressedBytes
            if (arr != null) {
                if (seenArrays.add(arr)) {
                    sum += p.sizeBytes
                }
            } else {
                sum += p.sizeBytes
            }
        }
        totalBytes = sum
    }

    fun clear() {
        for (patch in queue) {
            patch.compressedBytes = null
        }
        queue.clear()
        totalBytes = 0L
    }
}

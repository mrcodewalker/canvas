package com.example.canvas.history

import com.example.canvas.core.Patch
import java.io.File

/**
 * Manages cold swapped patch files on disk (Cold tier).
 */
class ColdStore(
    val historyDir: File
) {
    private val queue = ArrayDeque<Patch>()
    var totalBytes: Long = 0L
        private set

    val count: Int get() = queue.size

    init {
        cleanupOrphanedFiles()
    }

    /**
     * Deletes any lingering temporary or cold files from past app runs.
     */
    fun cleanupOrphanedFiles() {
        if (!historyDir.exists()) return
        historyDir.listFiles()?.forEach { file ->
            if (file.name.endsWith(".tmp") || file.name.endsWith(".chrf")) {
                file.delete()
            }
        }
    }

    fun add(patch: Patch) {
        queue.addLast(patch)
        totalBytes += patch.sizeBytes
    }

    fun removeOldest(): Patch? {
        if (queue.isEmpty()) return null
        val oldest = queue.removeFirst()
        deleteColdFile(oldest)
        totalBytes = (totalBytes - oldest.sizeBytes).coerceAtLeast(0L)
        return oldest
    }

    fun remove(patch: Patch): Boolean {
        val removed = queue.remove(patch)
        if (removed) {
            deleteColdFile(patch)
            totalBytes = (totalBytes - patch.sizeBytes).coerceAtLeast(0L)
        }
        return removed
    }

    fun writeToFile(patch: Patch, compressedBytes: ByteArray): File {
        val file = ColdFileCodec.writeColdFile(historyDir, patch, compressedBytes)
        patch.filePath = file.absolutePath
        patch.sizeBytes = file.length()
        return file
    }

    fun readPayload(patch: Patch): ColdFilePayload {
        val path = patch.filePath ?: throw IllegalStateException("Patch ${patch.id} has no cold file path")
        return ColdFileCodec.readColdFile(File(path))
    }

    fun deleteColdFile(patch: Patch) {
        val path = patch.filePath ?: return
        patch.filePath = null
        // SWAM OOM Cleaner safety: only delete the physical file if no other patch shares it
        val stillReferenced = queue.any { it !== patch && it.filePath == path }
        if (!stillReferenced) {
            try {
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                }
            } catch (_: Throwable) {}
        }
    }

    fun getAll(): List<Patch> = queue.toList()

    fun recalculateTotalBytes() {
        val uniquePaths = HashSet<String>()
        var sum = 0L
        for (p in queue) {
            val path = p.filePath
            if (path != null) {
                if (uniquePaths.add(path)) {
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
            deleteColdFile(patch)
        }
        queue.clear()
        totalBytes = 0L
        cleanupOrphanedFiles()
    }
}

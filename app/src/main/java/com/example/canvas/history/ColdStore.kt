package com.example.canvas.history

import com.example.canvas.core.Patch
import java.io.File

/**
 * Manages cold swapped patch files on disk (Cold tier).
 */
class ColdStore(
    val historyDir: File
) {
    private val queue = PatchQueue()

    // How many Cold patches point at each physical file (dedupe makes several share one file).
    // Replaces a scan of the whole queue every time a file was deleted or checked.
    private val pathRefs = HashMap<String, Int>()

    private fun ref(path: String?) {
        if (path != null) pathRefs[path] = (pathRefs[path] ?: 0) + 1
    }

    private fun unref(path: String?): Int {
        if (path == null) return 0
        val left = (pathRefs[path] ?: 1) - 1
        if (left <= 0) pathRefs.remove(path) else pathRefs[path] = left
        return left.coerceAtLeast(0)
    }
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
        ref(patch.filePath)
        totalBytes += patch.sizeBytes
    }

    fun peekOldest(): Patch? = queue.firstOrNull()

    /** True if another Cold patch points at the same physical file (deduplicated). */
    fun isShared(patch: Patch): Boolean {
        val path = patch.filePath ?: return false
        return (pathRefs[path] ?: 0) > 1
    }

    /** Replaces the Cold file of [patch] with [compressedBytes] (same raw content), fixing the accounting. */
    fun rewrite(patch: Patch, compressedBytes: ByteArray) {
        val oldPath = patch.filePath
        val oldSize = patch.sizeBytes
        val file = ColdFileCodec.writeColdFile(historyDir, patch, compressedBytes)
        patch.filePath = file.absolutePath
        patch.sizeBytes = file.length()
        if (oldPath != patch.filePath && queue.contains(patch)) {
            unref(oldPath)
            ref(patch.filePath)
        }
        if (oldPath != null && oldPath != patch.filePath && (pathRefs[oldPath] ?: 0) == 0) {
            try { File(oldPath).delete() } catch (_: Throwable) {}
        }
        totalBytes = (totalBytes - oldSize + patch.sizeBytes).coerceAtLeast(0L)
    }

    fun removeOldest(): Patch? {
        if (queue.isEmpty()) return null
        val oldest = queue.removeFirst()
        unref(oldest.filePath)
        deleteColdFile(oldest)
        totalBytes = (totalBytes - oldest.sizeBytes).coerceAtLeast(0L)
        return oldest
    }

    fun remove(patch: Patch): Boolean {
        val removed = queue.remove(patch)
        if (removed) {
            unref(patch.filePath)
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
        val stillReferenced = (pathRefs[path] ?: 0) > 0
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
        // The OOM cleaner may have re-pointed patches at shared files behind our back: rebuild the refs too
        pathRefs.clear()
        val uniquePaths = HashSet<String>()
        var sum = 0L
        for (p in queue) {
            val path = p.filePath
            if (path != null) {
                ref(path)
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
        pathRefs.clear()
        for (patch in queue) {
            deleteColdFile(patch)
        }
        queue.clear()
        totalBytes = 0L
        cleanupOrphanedFiles()
    }
}

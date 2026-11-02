package com.example.canvas.history

import com.example.canvas.core.Patch
import com.example.canvas.core.PatchState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/**
 * Prefetches the next K older patches from Cold (disk) into Warm (RAM)
 * after each undo step, improving responsiveness during consecutive undo operations.
 */
class Prefetcher(
    private val coldStore: ColdStore,
    private val warmStore: WarmStore,
    val k: Int = 5,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    val hits = AtomicInteger(0)
    val misses = AtomicInteger(0)

    var isEnabled: Boolean = true
    private var prefetchJob: Job? = null

    fun resetStats() {
        hits.set(0)
        misses.set(0)
    }

    val hitRate: Double
        get() {
            val h = hits.get()
            val m = misses.get()
            val total = h + m
            return if (total > 0) h.toDouble() / total.toDouble() else 0.0
        }

    fun recordHit() {
        hits.incrementAndGet()
    }

    fun recordMiss() {
        misses.incrementAndGet()
    }

    /**
     * Inspects the remaining undo history in reverse (next patches to be undone)
     * and asynchronously loads up to [k] patches from Cold to Warm.
     */
    fun prefetchNextK(
        undoHistory: List<Patch>,
        scope: CoroutineScope
    ) {
        if (!isEnabled || undoHistory.isEmpty()) return

        prefetchJob?.cancel()
        prefetchJob = scope.launch {
            // Traverse from the top of the undo stack (most recent to oldest)
            var prefetchedCount = 0
            for (i in undoHistory.size - 1 downTo 0) {
                if (prefetchedCount >= k) break
                val patch = undoHistory[i]

                if (patch.state == PatchState.COLD && patch.filePath != null) {
                    prefetchPatchToWarm(patch)
                    prefetchedCount++
                }
            }
        }
    }

    /**
     * Reads a patch from disk into RAM compressed bytes, promoting it to Warm.
     */
    suspend fun prefetchPatchToWarm(patch: Patch) {
        if (patch.state != PatchState.COLD || patch.filePath == null) return

        try {
            val payload = withContext(ioDispatcher) {
                coldStore.readPayload(patch)
            }

            // Successfully read into RAM -> promote to WARM
            patch.compressedBytes = payload.compressedBytes
            patch.rawLength = payload.rawLength
            patch.crc32 = payload.crc32
            patch.sizeBytes = payload.compressedBytes.size.toLong()
            patch.state = PatchState.WARM

            coldStore.remove(patch)
            warmStore.add(patch)
        } catch (_: Throwable) {
            // If prefetch fails, patch remains accessible on disk
        }
    }
}

package com.example.canvas.history

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import com.example.canvas.core.Patch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Naive history manager that stores a full canvas copy (Bitmap.copy) for every stroke.
 * Serves as the worst-case memory baseline (easily triggers OOM with large canvas/strokes).
 */
class NaiveHistory(
    /** Optional hard cap on redo steps; null (default) means free memory alone decides. */
    private val maxRedoSteps: Int? = null
) : HistoryManager {

    override val mode: HistoryMode = HistoryMode.NAIVE

    /** Not used: full-canvas snapshots are always restored exactly (no merge). */
    override var opRenderer: ((android.graphics.Canvas, com.example.canvas.core.StrokeOp) -> Unit)? = null

    private val mutex = Mutex()
    private val undoStack = ArrayDeque<Patch>()
    private val redoStack = ArrayDeque<Patch>()

    override suspend fun commit(before: Patch) = mutex.withLock {
        undoStack.addLast(before)
        clearRedoStackLocked()
    }

    override suspend fun undo(canvas: Bitmap): Boolean = mutex.withLock {
        if (undoStack.isEmpty()) return false

        val patchToRestore = undoStack.removeLast()

        // Capture current state for redo
        val redoBitmap = try {
            canvas.copy(Bitmap.Config.ARGB_8888, false)
        } catch (e: OutOfMemoryError) {
            // Re-add to undo stack before rethrowing OOM
            undoStack.addLast(patchToRestore)
            throw e
        }

        if (redoBitmap != null) {
            val redoPatch = Patch(
                id = System.nanoTime(),
                rect = Rect(0, 0, canvas.width, canvas.height),
                bitmap = redoBitmap,
                sizeBytes = redoBitmap.allocationByteCount.toLong()
            )
            redoStack.addLast(redoPatch)
            // every redo step is a full canvas: keep only as many as leave room for the next one
            while (redoStack.size > 1 &&
                (maxRedoSteps?.let { redoStack.size > it } == true ||
                    redoPatch.sizeBytes * 2 > MemorySignal.free())
            ) {
                redoStack.removeFirst().recycle()
            }
        }

        // Apply before patch onto canvas
        val c = Canvas(canvas)
        patchToRestore.applyTo(c)
        patchToRestore.recycle()

        return true
    }

    override suspend fun redo(canvas: Bitmap): Boolean = mutex.withLock {
        if (redoStack.isEmpty()) return false

        val patchToRestore = redoStack.removeLast()

        // Capture current state for undo
        val undoBitmap = try {
            canvas.copy(Bitmap.Config.ARGB_8888, false)
        } catch (e: OutOfMemoryError) {
            redoStack.addLast(patchToRestore)
            throw e
        }

        if (undoBitmap != null) {
            val undoPatch = Patch(
                id = System.nanoTime(),
                rect = Rect(0, 0, canvas.width, canvas.height),
                bitmap = undoBitmap,
                sizeBytes = undoBitmap.allocationByteCount.toLong()
            )
            undoStack.addLast(undoPatch)
        }

        val c = Canvas(canvas)
        patchToRestore.applyTo(c)
        patchToRestore.recycle()

        return true
    }

    override fun onTrimMemory(level: Int) {
        // Naive intentionally does not compress or offload,
        // but we release redo steps under severe memory pressure to behave safely.
        synchronized(redoStack) {
            while (redoStack.isNotEmpty()) {
                redoStack.removeFirst().recycle()
            }
        }
    }

    override fun stats(): HistoryStats {
        var bytes = 0L
        var hotCount = 0
        for (p in undoStack) {
            bytes += p.sizeBytes
            hotCount++
        }
        for (p in redoStack) {
            bytes += p.sizeBytes
        }
        return HistoryStats(
            hotBytes = bytes,
            warmBytes = 0L,
            coldBytes = 0L,
            hotSteps = hotCount,
            warmSteps = 0,
            coldSteps = 0,
            totalSteps = hotCount,
            redoSteps = redoStack.size
        )
    }

    override fun clear() {
        for (p in undoStack) {
            p.recycle()
        }
        undoStack.clear()
        clearRedoStackLocked()
    }

    private fun clearRedoStackLocked() {
        for (p in redoStack) {
            p.recycle()
        }
        redoStack.clear()
    }
}

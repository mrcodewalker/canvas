package com.example.canvas.history

import android.graphics.Bitmap
import android.graphics.Canvas
import com.example.canvas.core.Patch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * History manager that stores only the localized bounding rectangle (dirty rect) of each stroke.
 * Retains raw uncompressed Bitmaps in RAM. Provides significant memory savings over [NaiveHistory]
 * when strokes are localized.
 */
class DirtyRectHistory(
    private val maxRedoSteps: Int = 20
) : HistoryManager {

    override val mode: HistoryMode = HistoryMode.DIRTY_RECT

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
        val rect = patchToRestore.rect

        // Capture current canvas dirty region for redo
        val redoBitmap = try {
            Bitmap.createBitmap(
                canvas,
                rect.left,
                rect.top,
                rect.width().coerceAtLeast(1),
                rect.height().coerceAtLeast(1)
            )
        } catch (e: OutOfMemoryError) {
            undoStack.addLast(patchToRestore)
            throw e
        }

        val redoPatch = Patch(
            id = System.nanoTime(),
            rect = rect,
            bitmap = redoBitmap,
            sizeBytes = redoBitmap.allocationByteCount.toLong(),
            crc32 = patchToRestore.crc32
        )
        if (redoStack.size >= maxRedoSteps) {
            val oldest = redoStack.removeFirst()
            oldest.recycle()
        }
        redoStack.addLast(redoPatch)

        // Apply before patch onto canvas using PorterDuff.Mode.SRC
        val c = Canvas(canvas)
        patchToRestore.applyTo(c)
        patchToRestore.recycle()

        return true
    }

    override suspend fun redo(canvas: Bitmap): Boolean = mutex.withLock {
        if (redoStack.isEmpty()) return false

        val patchToRestore = redoStack.removeLast()
        val rect = patchToRestore.rect

        // Capture current canvas dirty region for undo
        val undoBitmap = try {
            Bitmap.createBitmap(
                canvas,
                rect.left,
                rect.top,
                rect.width().coerceAtLeast(1),
                rect.height().coerceAtLeast(1)
            )
        } catch (e: OutOfMemoryError) {
            redoStack.addLast(patchToRestore)
            throw e
        }

        val undoPatch = Patch(
            id = System.nanoTime(),
            rect = rect,
            bitmap = undoBitmap,
            sizeBytes = undoBitmap.allocationByteCount.toLong(),
            crc32 = patchToRestore.crc32
        )
        undoStack.addLast(undoPatch)

        val c = Canvas(canvas)
        patchToRestore.applyTo(c)
        patchToRestore.recycle()

        return true
    }

    override fun onTrimMemory(level: Int) {
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

package com.example.canvas.history

import android.graphics.Bitmap
import android.graphics.Canvas
import com.example.canvas.core.Patch
import com.example.canvas.core.StrokeOp
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * History manager that stores only the localized dirty regions of each stroke.
 * Retains raw uncompressed Bitmaps in RAM. Provides significant memory savings over [NaiveHistory]
 * when strokes are localized.
 *
 * Redo survives new drawing and merges with it: see [RedoMerge]. Steps are replayed on top of the current
 * canvas when their saved pixels would erase newer drawing.
 *
 * @param maxRedoSteps optional hard cap on redo steps; null (default) means memory alone decides.
 */
class DirtyRectHistory(
    private val maxRedoSteps: Int? = null
) : HistoryManager {

    override val mode: HistoryMode = HistoryMode.DIRTY_RECT
    override var opRenderer: ((Canvas, StrokeOp) -> Unit)? = null

    /** Learns how deep this user redoes; sizes the redo stack instead of a fixed count. */
    val redoBehavior = RedoBehavior()

    private val mutex = Mutex()
    private val undoStack = ArrayDeque<Patch>()
    private val redoStack = ArrayDeque<Patch>()

    override suspend fun commit(before: Patch) = mutex.withLock {
        undoStack.addLast(before)
        redoBehavior.onOther()
        RedoMerge.invalidate(redoStack, before.rect)
    }

    /** Pops the top patch plus every other patch of the same stroke group (tiles). */
    private fun popGroup(stack: ArrayDeque<Patch>): List<Patch> {
        val first = stack.removeLast()
        if (first.groupId == 0L) return listOf(first)
        val group = arrayListOf(first)
        while (stack.isNotEmpty() && stack.last().groupId == first.groupId) {
            group.add(stack.removeLast())
        }
        return group
    }

    private fun pushBack(stack: ArrayDeque<Patch>, group: List<Patch>) {
        for (i in group.indices.reversed()) stack.addLast(group[i])
    }

    /** Captures the current canvas under each patch rect (the inverse patches), or throws OOM leaving canvas untouched. */
    private fun captureInverse(canvas: Bitmap, group: List<Patch>): List<Patch> {
        val result = ArrayList<Patch>(group.size)
        try {
            for (p in group) {
                val rect = p.rect
                val bmp = Bitmap.createBitmap(
                    canvas,
                    rect.left,
                    rect.top,
                    rect.width().coerceAtLeast(1),
                    rect.height().coerceAtLeast(1)
                )
                result.add(
                    Patch(
                        id = System.nanoTime() + result.size,
                        rect = rect,
                        bitmap = bmp,
                        sizeBytes = bmp.allocationByteCount.toLong(),
                        crc32 = p.crc32,
                        groupId = p.groupId,
                        op = p.op
                    )
                )
            }
        } catch (e: OutOfMemoryError) {
            result.forEach { it.recycle() }
            throw e
        }
        return result
    }

    private fun distinctGroups(stack: ArrayDeque<Patch>): Int {
        // stats() may race with a mutation on another thread: fall back to the raw size then.
        return try {
            var count = 0
            var prev = Long.MIN_VALUE
            for (p in stack) {
                if (p.groupId == 0L || p.groupId != prev) count++
                prev = p.groupId
            }
            count
        } catch (_: RuntimeException) {
            stack.size
        }
    }

    private fun applyGroup(canvas: Bitmap, group: List<Patch>) {
        val c = Canvas(canvas)
        for (p in group) {
            p.applyTo(c)
            p.recycle()
        }
    }

    private fun enforceRedoMemory() {
        // pixels only for as deep as this user really redoes (one step until there is evidence of more);
        // deeper steps stay as replayable strokes, and steps that cannot be replayed are forgotten
        RedoMerge.dropPixelsBeyond(redoStack, redoBehavior.typicalDepth() ?: 1)
        // and if memory is still short, give back pixels of the steps least likely to be redone, per byte
        RedoMerge.dropLeastValuable(redoStack, RedoMerge.excess(redoStack), redoBehavior::reach)
        if (maxRedoSteps != null) {
            while (redoStack.isNotEmpty() && distinctGroups(redoStack) > maxRedoSteps) {
                val oldest = redoStack.removeFirst()
                oldest.recycle()
                while (oldest.groupId != 0L && redoStack.isNotEmpty() && redoStack.first().groupId == oldest.groupId) {
                    redoStack.removeFirst().recycle()
                }
            }
        }
    }

    override suspend fun undo(canvas: Bitmap): Boolean = mutex.withLock {
        if (undoStack.isEmpty()) return false

        val group = popGroup(undoStack)
        redoBehavior.onOther()
        val inverse = try {
            captureInverse(canvas, group)
        } catch (e: OutOfMemoryError) {
            pushBack(undoStack, group)
            throw e
        }

        pushBack(redoStack, inverse)
        enforceRedoMemory()

        applyGroup(canvas, group)
        return true
    }

    override suspend fun redo(canvas: Bitmap): Boolean = mutex.withLock {
        if (redoStack.isEmpty()) return false

        val group = popGroup(redoStack)
        redoBehavior.onRedo()
        val inverse = try {
            captureInverse(canvas, group)
        } catch (e: OutOfMemoryError) {
            pushBack(redoStack, group)
            throw e
        }

        if (RedoMerge.needsReplay(group)) {
            // The canvas changed under this step (or its pixels were dropped): draw the stroke again on top
            val op = group.first().op
            val render = opRenderer
            if (op == null || render == null) {
                inverse.forEach { it.recycle() }
                group.forEach { it.recycle() }
                return false
            }
            render(Canvas(canvas), op)
            group.forEach { it.recycle() }
        } else {
            applyGroup(canvas, group)
        }
        pushBack(undoStack, inverse)
        return true
    }

    override fun onTrimMemory(level: Int) {
        synchronized(redoStack) {
            // keep the steps, drop their pixels: they can still be replayed
            RedoMerge.dropOldestPixels(redoStack, Long.MAX_VALUE)
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
            bytes += if (RedoMerge.hasPixels(p)) p.sizeBytes else 0L
        }
        return HistoryStats(
            hotBytes = bytes,
            warmBytes = 0L,
            coldBytes = 0L,
            hotSteps = hotCount,
            warmSteps = 0,
            coldSteps = 0,
            totalSteps = distinctGroups(undoStack),
            redoSteps = distinctGroups(redoStack)
        )
    }

    override fun clear() {
        for (p in undoStack) {
            p.recycle()
        }
        undoStack.clear()
        for (p in redoStack) p.recycle()
        redoStack.clear()
    }
}

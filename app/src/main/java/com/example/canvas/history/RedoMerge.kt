package com.example.canvas.history

import android.graphics.Rect
import com.example.canvas.core.Patch

/**
 * Redo that survives new drawing ("redo with merge").
 *
 * After undo the redo stack used to be thrown away as soon as the user drew something new. Now it is kept.
 * A redo step holds up to two things: the pixels of the stroke (fast exact restore) and the stroke itself
 * ([Patch.op], tiny). When new drawing overlaps a redo step its pixels are stale - restoring them would
 * erase the new drawing - so they are dropped and the step is later *replayed* on top of the current canvas
 * (merge). Under memory pressure the oldest pixels are dropped the same way. A step with neither pixels nor
 * a replayable op cannot be redone and is removed.
 */
internal object RedoMerge {

    fun overlaps(a: Rect, b: Rect): Boolean =
        a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom

    fun hasPixels(p: Patch): Boolean = p.bitmap?.isRecycled == false

    fun groups(stack: ArrayDeque<Patch>): List<List<Patch>> {
        val out = ArrayList<List<Patch>>()
        var i = 0
        while (i < stack.size) {
            val first = stack[i]
            val g = arrayListOf(first)
            i++
            if (first.groupId != 0L) {
                while (i < stack.size && stack[i].groupId == first.groupId) g.add(stack[i++])
            }
            out.add(g)
        }
        return out
    }

    /** A new drawing at [changed] makes overlapping redo pixels stale. Keeps steps that can still be replayed. */
    fun invalidate(stack: ArrayDeque<Patch>, changed: Rect) {
        if (stack.isEmpty()) return
        val all = groups(stack)
        stack.clear()
        for (g in all) {
            if (g.none { overlaps(it.rect, changed) }) {
                stack.addAll(g)
                continue
            }
            for (p in g) p.recycle()                      // pixels are stale
            if (g.first().op != null) stack.addAll(g)     // can still be replayed
        }
    }

    fun pixelBytes(stack: ArrayDeque<Patch>): Long =
        stack.sumOf { p -> p.bitmap?.takeIf { !it.isRecycled }?.allocationByteCount?.toLong() ?: 0L }

    /** Drops pixels of the oldest redo steps until at least [mustFree] bytes were released. */
    fun dropOldestPixels(stack: ArrayDeque<Patch>, mustFree: Long) {
        if (mustFree <= 0L || stack.isEmpty()) return
        var freed = 0L
        val all = groups(stack)
        stack.clear()
        for (g in all) {
            if (freed < mustFree && g.any { hasPixels(it) }) {
                for (p in g) {
                    freed += p.bitmap?.takeIf { !it.isRecycled }?.allocationByteCount?.toLong() ?: 0L
                    p.recycle()
                }
                if (g.first().op != null) stack.addAll(g)
            } else {
                stack.addAll(g)
            }
        }
    }

    /**
     * Frees pixels of redo steps deeper than [maxDepth] (1 = the step redone first) that can be replayed
     * from their stroke. Steps that cannot be replayed keep their pixels.
     */
    fun dropPixelsBeyond(stack: ArrayDeque<Patch>, maxDepth: Int) {
        if (stack.isEmpty()) return
        val all = groups(stack)
        var removed = false
        val keep = ArrayList<List<Patch>>(all.size)
        for ((i, g) in all.withIndex()) {
            val depth = all.size - i                    // last element of the stack is depth 1
            if (depth <= maxDepth) {
                keep.add(g)
            } else if (g.first().op != null) {
                for (p in g) p.recycle()                // replayable: keep the stroke, free the pixels
                keep.add(g)
            } else {
                for (p in g) p.recycle()                // not replayable and beyond what is ever redone: forget it
                removed = true
            }
        }
        if (removed) {
            stack.clear()
            for (g in keep) stack.addAll(g)
        }
    }

    /** Bytes of the pixels held by the step that would be redone first. */
    fun newestGroupBytes(stack: ArrayDeque<Patch>): Long {
        val g = groups(stack).lastOrNull() ?: return 0L
        return g.sumOf { p -> p.bitmap?.takeIf { !it.isRecycled }?.allocationByteCount?.toLong() ?: 0L }
    }

    /** Habit first (how deep this user redoes), then a pixel budget, always sparing the step redone first. */
    fun enforce(stack: ArrayDeque<Patch>, keepDepth: Int, pixelBudget: Long, reach: (Int) -> Double) {
        dropPixelsBeyond(stack, keepDepth)
        val budget = maxOf(pixelBudget, newestGroupBytes(stack))
        dropLeastValuable(stack, pixelBytes(stack) - budget, reach)
    }

    /**
     * Frees at least [mustFree] bytes of redo pixels, taking from the steps with the lowest
     * `probability of being redone / bytes` first. Replayable steps keep their stroke; the others go entirely.
     */
    fun dropLeastValuable(stack: ArrayDeque<Patch>, mustFree: Long, reach: (Int) -> Double) {
        if (mustFree <= 0L || stack.isEmpty()) return
        val all = groups(stack)
        val order = all.indices.filter { i -> all[i].any { hasPixels(it) } }.sortedBy { i ->
            val depth = all.size - i
            val bytes = all[i].sumOf { p -> p.bitmap?.takeIf { !it.isRecycled }?.allocationByteCount?.toLong() ?: 0L }
            reach(depth) / bytes.coerceAtLeast(1L).toDouble()
        }
        var freed = 0L
        val gone = HashSet<Int>()
        for (i in order) {
            if (freed >= mustFree) break
            val g = all[i]
            for (p in g) freed += p.bitmap?.takeIf { !it.isRecycled }?.allocationByteCount?.toLong() ?: 0L
            for (p in g) p.recycle()
            if (g.first().op == null) gone.add(i)
        }
        if (gone.isNotEmpty()) {
            stack.clear()
            for ((i, g) in all.withIndex()) if (i !in gone) stack.addAll(g)
        }
    }

    fun needsReplay(group: List<Patch>): Boolean = group.any { !hasPixels(it) }

    fun freeHeap(): Long = MemorySignal.free()

    /**
     * How many bytes of redo pixels have to go so the user's own next capture (twice the largest step seen)
     * still fits in free memory. No fixed cap: it follows the strokes this user draws.
     */
    fun excess(stack: ArrayDeque<Patch>): Long {
        val biggest = stack.maxOfOrNull { p ->
            p.bitmap?.takeIf { !it.isRecycled }?.allocationByteCount?.toLong() ?: 0L
        } ?: 0L
        return 2 * biggest - freeHeap()
    }
}
